package it.unipi.lsmsd.gamehub.service.impl;

import it.unipi.lsmsd.gamehub.model.FeedState;
import it.unipi.lsmsd.gamehub.model.Game;
import it.unipi.lsmsd.gamehub.model.Review;
import it.unipi.lsmsd.gamehub.model.User;
import it.unipi.lsmsd.gamehub.repository.ActivityRepository;
import it.unipi.lsmsd.gamehub.repository.GameRepository;
import it.unipi.lsmsd.gamehub.repository.LoginRepository;
import it.unipi.lsmsd.gamehub.repository.NotificationRepository;
import it.unipi.lsmsd.gamehub.repository.ReviewNeo4jRepository;
import it.unipi.lsmsd.gamehub.repository.ReviewReplyRepository;
import it.unipi.lsmsd.gamehub.repository.ReviewRepository;
import it.unipi.lsmsd.gamehub.repository.UserNeo4jRepository;
import it.unipi.lsmsd.gamehub.security.LoginRateLimiter;
import it.unipi.lsmsd.gamehub.service.IAccountService;
import it.unipi.lsmsd.gamehub.service.IGameService;
import it.unipi.lsmsd.gamehub.service.ILoginService;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class AccountService implements IAccountService {
    // stessi limiti di /login: la password puo' essere indovinata anche da chi ha un token rubato
    private static final String RATE_LIMIT_PREFIX = "delete-account:";

    // quante recensioni embedded tiene ogni Game (vedi GameService.updateGameReviewFromScratch)
    private static final int EMBEDDED_REVIEWS = 20;

    @Autowired private LoginRepository loginRepository;
    @Autowired private ILoginService loginService;
    @Autowired private ReviewRepository reviewRepository;
    @Autowired private ReviewReplyRepository replyRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private ActivityRepository activityRepository;
    @Autowired private GameRepository gameRepository;
    @Autowired private IGameService gameService;
    @Autowired private UserNeo4jRepository userNeo4jRepository;
    @Autowired private ReviewNeo4jRepository reviewNeo4jRepository;
    @Autowired private MongoTemplate mongoTemplate;
    @Autowired private LoginRateLimiter loginRateLimiter;

    @Override
    public ResponseEntity<String> deleteAccount(String username, String password) {
        User user = loginRepository.findByUsername(username);
        if (user == null) {
            return new ResponseEntity<>("Account non trovato", HttpStatus.NOT_FOUND);
        }
        // Un admin non e' un utente qualunque: il ruolo esiste solo nel dump e non c'e' modo di
        // assegnarlo dall'app, quindi cancellare l'ultimo admin toglierebbe per sempre l'accesso
        // alle funzioni amministrative.
        if (user.getRole() != null) {
            return new ResponseEntity<>(
                    "Gli account amministratore non possono essere eliminati", HttpStatus.CONFLICT);
        }

        String rateLimitKey = RATE_LIMIT_PREFIX + username.toLowerCase();
        if (loginRateLimiter.isBlocked(rateLimitKey)) {
            long retryAfterSeconds = loginRateLimiter.remainingBlockSeconds(rateLimitKey);
            log.warn("Eliminazione account rifiutata per {} (rate limit attivo)", username);
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .header("Retry-After", String.valueOf(retryAfterSeconds))
                    .body("Troppi tentativi falliti, riprova tra qualche minuto");
        }
        // 403 e non 401: il frontend tratta ogni 401 come "sessione scaduta" e fa il logout, ma
        // qui la sessione e' valida, e' solo la password di conferma a essere sbagliata
        if (!loginService.checkPassword(username, password)) {
            loginRateLimiter.recordFailure(rateLimitKey);
            log.warn("Eliminazione account rifiutata per {}: password errata", username);
            return new ResponseEntity<>("Password non corretta", HttpStatus.FORBIDDEN);
        }
        loginRateLimiter.recordSuccess(rateLimitKey);

        // Mongo e Neo4j non sono transazionali insieme, quindi non c'e' un rollback possibile: si
        // procede invece in modo che ogni passo sia idempotente e il documento utente - cio' che
        // permette di rifare il login e ritentare - sia l'ultimo a sparire. Se qualcosa fallisce a
        // meta' l'utente vede un errore, ritenta, e i passi gia' fatti non hanno piu' nulla da
        // fare.
        try {
            Set<String> affectedGames = new LinkedHashSet<>();
            removeGivenLikes(username, affectedGames);
            removeOwnReviews(username, affectedGames);
            removeUserContent(username);
            refreshEmbeddedReviews(affectedGames);
            userNeo4jRepository.deleteUserWithRelationships(username);
            loginRepository.deleteById(user.getId());
            log.info("Account {} eliminato", username);
            return new ResponseEntity<>("Account eliminato", HttpStatus.OK);
        } catch (Exception e) {
            log.error("Errore durante l'eliminazione dell'account {}", username, e);
            return new ResponseEntity<>(
                    "Eliminazione dell'account non riuscita, riprova piu tardi",
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    // i like dati dall'utente contano nel likeCount delle recensioni altrui: senza toglierli
    // resterebbero per sempre, e falserebbero la classifica "piu' votate" di ogni gioco.
    // consumeAllLikedReviewIds cancella le relazioni Neo4j nella stessa query con cui le legge:
    // se un ritentativo arriva qui una seconda volta (perche' un passo successivo e' fallito la
    // prima volta) non ritrova piu' nulla da decrementare, invece di decrementare due volte.
    private void removeGivenLikes(String username, Set<String> affectedGames) {
        List<String> likedIds = userNeo4jRepository.consumeAllLikedReviewIds(username);
        if (likedIds == null || likedIds.isEmpty()) {
            return;
        }
        List<Review> liked = reviewRepository.findAllById(likedIds);
        if (liked.isEmpty()) {
            return;
        }
        List<String> ids = liked.stream().map(Review::getId).toList();
        // >0 come in removeLikeFromReview: il contatore non scende mai sotto zero
        mongoTemplate.updateMulti(
                Query.query(Criteria.where("id").in(ids).and("likeCount").gt(0)),
                new Update().inc("likeCount", -1),
                Review.class);
        liked.forEach(review -> affectedGames.add(review.getTitle()));
    }

    // le recensioni dell'utente con tutto cio' che vi si appoggia: risposte altrui, notifiche,
    // voci del feed e il nodo (con i like ricevuti) su Neo4j
    private void removeOwnReviews(String username, Set<String> affectedGames) {
        List<Review> reviews = reviewRepository.findByUsername(username);
        if (reviews.isEmpty()) {
            return;
        }
        List<String> ids = reviews.stream().map(Review::getId).toList();
        replyRepository.deleteByReviewIdIn(ids);
        notificationRepository.deleteByReviewIdIn(ids);
        activityRepository.deleteByReviewIdIn(ids);
        reviewNeo4jRepository.deleteAllWithRelationshipsByIdIn(ids);
        reviewRepository.deleteAllById(ids);
        reviews.forEach(review -> affectedGames.add(review.getTitle()));
    }

    // tutto il resto che porta il nome dell'utente, sia come autore sia come destinatario
    private void removeUserContent(String username) {
        replyRepository.deleteByUsername(username);
        notificationRepository.deleteByRecipient(username);
        notificationRepository.deleteByActor(username);
        activityRepository.deleteByUsername(username);
        activityRepository.deleteByTargetUsername(username);
        mongoTemplate.remove(Query.query(Criteria.where("_id").is(username)), FeedState.class);
    }

    // la lista di recensioni embedded in ogni Game e' una copia delle piu' votate: va ricalcolata
    // per i giochi in cui e' sparita una recensione o e' sceso un likeCount
    private void refreshEmbeddedReviews(Set<String> gameNames) {
        for (String name : gameNames) {
            List<Game> games = gameRepository.findByName(name);
            if (games != null && !games.isEmpty()) {
                gameService.updateGameReviewFromScratch(games.get(0), EMBEDDED_REVIEWS);
            }
        }
    }
}
