package it.unipi.lsmsd.gamehub.service.impl;

import it.unipi.lsmsd.gamehub.DTO.ActivityDTO;
import it.unipi.lsmsd.gamehub.model.Activity;
import it.unipi.lsmsd.gamehub.model.ActivityType;
import it.unipi.lsmsd.gamehub.model.Game;
import it.unipi.lsmsd.gamehub.model.UserNeo4j;
import it.unipi.lsmsd.gamehub.repository.ActivityRepository;
import it.unipi.lsmsd.gamehub.repository.GameRepository;
import it.unipi.lsmsd.gamehub.repository.UserNeo4jRepository;
import it.unipi.lsmsd.gamehub.service.IActivityService;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class ActivityService implements IActivityService {
    @Autowired private ActivityRepository activityRepository;
    @Autowired private UserNeo4jRepository userNeo4jRepository;
    @Autowired private GameRepository gameRepository;

    // il feed e' un arricchimento della Home, non un'operazione critica: un fallimento qui non
    // deve far fallire l'azione (wishlist/review) gia' andata a buon fine altrove, quindi ogni
    // eccezione viene solo loggata invece di risalire al chiamante.
    @Override
    public void recordWishlistAdd(String username, String gameName) {
        try {
            activityRepository.save(
                    new Activity(
                            null,
                            username,
                            ActivityType.WISHLIST_ADD,
                            gameName,
                            null,
                            null,
                            Instant.now()));
        } catch (Exception e) {
            log.error("Errore in recordWishlistAdd", e);
        }
    }

    @Override
    public void recordReview(String username, String gameName, String reviewId, int score) {
        try {
            activityRepository.save(
                    new Activity(
                            null,
                            username,
                            ActivityType.REVIEW,
                            gameName,
                            reviewId,
                            score,
                            Instant.now()));
        } catch (Exception e) {
            log.error("Errore in recordReview", e);
        }
    }

    @Override
    public Page<ActivityDTO> getFriendsActivity(String username, Pageable pageable) {
        try {
            List<String> friends =
                    userNeo4jRepository.findFollowedUsers(username).stream()
                            .map(UserNeo4j::getUsername)
                            .toList();
            if (friends.isEmpty()) {
                return Page.empty(pageable);
            }

            Page<Activity> activities =
                    activityRepository.findByUsernameInOrderByCreatedAtDesc(friends, pageable);

            // una sola query batch per le copertine dei giochi citati in questa pagina, invece di
            // una query per riga. I nomi non sono univoci nel dataset, ma per una card di feed
            // basta un'immagine plausibile: stesso compromesso gia' accettato altrove (vedi
            // UserNeo4jRepository.addGameToUser) per i nomi duplicati.
            List<String> gameNames =
                    activities.getContent().stream().map(Activity::getGameName).distinct().toList();
            Map<String, Game> byName = new HashMap<>();
            for (Game game : gameRepository.findByNameIn(gameNames)) {
                byName.putIfAbsent(game.getName(), game);
            }

            List<ActivityDTO> dtos =
                    activities.getContent().stream()
                            .map(a -> toDTO(a, byName.get(a.getGameName())))
                            .toList();

            return new PageImpl<>(dtos, pageable, activities.getTotalElements());
        } catch (Exception e) {
            log.error("Errore in getFriendsActivity", e);
            return Page.empty(pageable);
        }
    }

    private ActivityDTO toDTO(Activity activity, Game game) {
        ActivityDTO dto = new ActivityDTO();
        dto.setUsername(activity.getUsername());
        dto.setType(activity.getType());
        dto.setGameName(activity.getGameName());
        dto.setGameHeaderImage(
                game != null && game.getURL() != null ? game.getURL().getHeaderImage() : null);
        dto.setScore(activity.getScore());
        dto.setCreatedAt(activity.getCreatedAt());
        return dto;
    }
}
