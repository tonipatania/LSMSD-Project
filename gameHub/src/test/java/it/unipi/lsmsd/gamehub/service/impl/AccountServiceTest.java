package it.unipi.lsmsd.gamehub.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

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
import it.unipi.lsmsd.gamehub.service.IGameService;
import it.unipi.lsmsd.gamehub.service.ILoginService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.UpdateDefinition;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock private LoginRepository loginRepository;
    @Mock private ILoginService loginService;
    @Mock private ReviewRepository reviewRepository;
    @Mock private ReviewReplyRepository replyRepository;
    @Mock private NotificationRepository notificationRepository;
    @Mock private ActivityRepository activityRepository;
    @Mock private GameRepository gameRepository;
    @Mock private IGameService gameService;
    @Mock private UserNeo4jRepository userNeo4jRepository;
    @Mock private ReviewNeo4jRepository reviewNeo4jRepository;
    @Mock private MongoTemplate mongoTemplate;
    @Mock private LoginRateLimiter loginRateLimiter;

    @InjectMocks private AccountService accountService;

    private static User user(String username, String role) {
        User u = new User();
        u.setId("id-" + username);
        u.setUsername(username);
        u.setRole(role);
        return u;
    }

    private static Review review(String id, String title, String username) {
        Review r = new Review();
        r.setId(id);
        r.setTitle(title);
        r.setUsername(username);
        return r;
    }

    private void stubValidCredentials(String username) {
        when(loginRepository.findByUsername(username)).thenReturn(user(username, null));
        when(loginService.checkPassword(username, "Password1!")).thenReturn(true);
    }

    @Test
    void deleteAccount_unknownUser_returnsNotFoundAndDeletesNothing() {
        when(loginRepository.findByUsername("ghost")).thenReturn(null);

        ResponseEntity<String> response = accountService.deleteAccount("ghost", "Password1!");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        verifyNoInteractions(userNeo4jRepository, reviewRepository, mongoTemplate);
        verify(loginRepository, never()).deleteById(anyString());
    }

    @Test
    void deleteAccount_adminAccount_returnsConflictAndDeletesNothing() {
        when(loginRepository.findByUsername("Lunark")).thenReturn(user("Lunark", "admin"));

        ResponseEntity<String> response = accountService.deleteAccount("Lunark", "Password1!");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        verify(loginRepository, never()).deleteById(anyString());
        verifyNoInteractions(userNeo4jRepository);
    }

    @Test
    void deleteAccount_rateLimitActive_returnsTooManyRequestsWithoutCheckingPassword() {
        when(loginRepository.findByUsername("mario")).thenReturn(user("mario", null));
        when(loginRateLimiter.isBlocked("delete-account:mario")).thenReturn(true);
        when(loginRateLimiter.remainingBlockSeconds("delete-account:mario")).thenReturn(120L);

        ResponseEntity<String> response = accountService.deleteAccount("mario", "Password1!");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(response.getHeaders().getFirst("Retry-After")).isEqualTo("120");
        verifyNoInteractions(loginService);
        verify(loginRepository, never()).deleteById(anyString());
    }

    @Test
    void deleteAccount_wrongPassword_returnsForbiddenRecordsFailureAndDeletesNothing() {
        when(loginRepository.findByUsername("mario")).thenReturn(user("mario", null));
        when(loginService.checkPassword("mario", "wrong")).thenReturn(false);

        ResponseEntity<String> response = accountService.deleteAccount("mario", "wrong");

        // 403 e non 401: il frontend fa il logout a ogni 401
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        verify(loginRateLimiter).recordFailure("delete-account:mario");
        verify(loginRepository, never()).deleteById(anyString());
        verifyNoInteractions(userNeo4jRepository, reviewRepository);
    }

    @Test
    void deleteAccount_validRequest_removesEverythingAndTheUserDocumentLast() {
        stubValidCredentials("mario");
        when(userNeo4jRepository.consumeAllLikedReviewIds("mario")).thenReturn(List.of("liked1"));
        Review liked = review("liked1", "Portal", "luigi");
        when(reviewRepository.findAllById(List.of("liked1"))).thenReturn(List.of(liked));
        Review own = review("own1", "Hades", "mario");
        when(reviewRepository.findByUsername("mario")).thenReturn(List.of(own));
        Game portal = new Game();
        portal.setName("Portal");
        Game hades = new Game();
        hades.setName("Hades");
        when(gameRepository.findByName("Portal")).thenReturn(List.of(portal));
        when(gameRepository.findByName("Hades")).thenReturn(List.of(hades));

        ResponseEntity<String> response = accountService.deleteAccount("mario", "Password1!");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(loginRateLimiter).recordSuccess("delete-account:mario");
        // il like dato a una recensione altrui viene tolto dal suo contatore
        verify(mongoTemplate)
                .updateMulti(
                        any(Query.class),
                        any(UpdateDefinition.class),
                        org.mockito.ArgumentMatchers.eq(Review.class));
        // la recensione propria sparisce con tutto cio' che vi si appoggia
        verify(replyRepository).deleteByReviewIdIn(List.of("own1"));
        verify(notificationRepository).deleteByReviewIdIn(List.of("own1"));
        verify(activityRepository).deleteByReviewIdIn(List.of("own1"));
        verify(reviewNeo4jRepository).deleteAllWithRelationshipsByIdIn(List.of("own1"));
        verify(reviewRepository).deleteAllById(List.of("own1"));
        // e cio' che porta il nome dell'utente, come autore o destinatario
        verify(replyRepository).deleteByUsername("mario");
        verify(notificationRepository).deleteByRecipient("mario");
        verify(notificationRepository).deleteByActor("mario");
        verify(activityRepository).deleteByUsername("mario");
        verify(activityRepository).deleteByTargetUsername("mario");
        verify(mongoTemplate)
                .remove(any(Query.class), org.mockito.ArgumentMatchers.eq(FeedState.class));
        // le recensioni embedded dei giochi toccati (like tolto o recensione persa) si ricalcolano
        verify(gameService).updateGameReviewFromScratch(portal, 20);
        verify(gameService).updateGameReviewFromScratch(hades, 20);

        InOrder order = inOrder(userNeo4jRepository, loginRepository);
        order.verify(userNeo4jRepository).deleteUserWithRelationships("mario");
        order.verify(loginRepository).deleteById("id-mario");
    }

    @Test
    void deleteAccount_userWithoutReviewsOrLikes_skipsReviewCleanup() {
        stubValidCredentials("mario");
        when(userNeo4jRepository.consumeAllLikedReviewIds("mario")).thenReturn(List.of());
        when(reviewRepository.findByUsername("mario")).thenReturn(List.of());

        ResponseEntity<String> response = accountService.deleteAccount("mario", "Password1!");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(reviewRepository, never()).deleteAllById(anyCollection());
        verify(reviewNeo4jRepository, never()).deleteAllWithRelationshipsByIdIn(anyCollection());
        verify(gameService, never())
                .updateGameReviewFromScratch(any(), org.mockito.ArgumentMatchers.anyInt());
        verify(loginRepository).deleteById("id-mario");
    }

    @Test
    void deleteAccount_neo4jFails_keepsTheUserDocumentSoTheUserCanRetry() {
        stubValidCredentials("mario");
        when(userNeo4jRepository.consumeAllLikedReviewIds("mario")).thenReturn(List.of());
        when(reviewRepository.findByUsername("mario")).thenReturn(List.of());
        doThrow(new IllegalStateException("neo4j down"))
                .when(userNeo4jRepository)
                .deleteUserWithRelationships("mario");

        ResponseEntity<String> response = accountService.deleteAccount("mario", "Password1!");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        verify(loginRepository, never()).deleteById(anyString());
    }

    @Test
    void deleteAccount_retriedAfterTheLikesWereAlreadyConsumed_doesNotDecrementAgain() {
        // simula un ritentativo dopo che un tentativo precedente e' arrivato fino a consumare le
        // relazioni LIKE su Neo4j (vedi consumeAllLikedReviewIds) ma e' poi fallito piu' avanti:
        // la seconda chiamata non deve ritrovare nulla da decrementare una seconda volta
        stubValidCredentials("mario");
        when(userNeo4jRepository.consumeAllLikedReviewIds("mario")).thenReturn(List.of());
        when(reviewRepository.findByUsername("mario")).thenReturn(List.of());

        ResponseEntity<String> response = accountService.deleteAccount("mario", "Password1!");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(reviewRepository, never()).findAllById(anyCollection());
        verify(mongoTemplate, never())
                .updateMulti(
                        any(Query.class),
                        any(UpdateDefinition.class),
                        org.mockito.ArgumentMatchers.eq(Review.class));
    }

    @Test
    void deleteAccount_mongoUserDeletionFailsThenRetried_secondCallFinishesCleanly() {
        // simula il caso peggiore per l'ultimo passo: tutto il resto (like, recensioni, contenuti,
        // nodo Neo4j) e' gia' sparito al primo tentativo, solo la cancellazione del documento
        // User su Mongo fallisce (es. un errore di rete transitorio). Un secondo tentativo deve
        // ritrovare tutto gia' vuoto e limitarsi a ritentare quell'ultimo passo.
        stubValidCredentials("mario");
        when(userNeo4jRepository.consumeAllLikedReviewIds("mario")).thenReturn(List.of());
        when(reviewRepository.findByUsername("mario")).thenReturn(List.of());
        doThrow(new IllegalStateException("mongo transiente"))
                .doNothing()
                .when(loginRepository)
                .deleteById("id-mario");

        ResponseEntity<String> first = accountService.deleteAccount("mario", "Password1!");
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);

        ResponseEntity<String> second = accountService.deleteAccount("mario", "Password1!");
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.OK);

        // il nodo Neo4j e il resto dei contenuti sono stati (ri)cancellati senza errori anche la
        // seconda volta: nessuna doppia decrementazione, nessuna eccezione sui passi gia' no-op
        verify(userNeo4jRepository, times(2)).deleteUserWithRelationships("mario");
        verify(loginRepository, times(2)).deleteById("id-mario");
    }
}
