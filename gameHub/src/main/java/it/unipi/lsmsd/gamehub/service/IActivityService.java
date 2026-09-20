package it.unipi.lsmsd.gamehub.service;

import it.unipi.lsmsd.gamehub.DTO.ActivityDTO;
import it.unipi.lsmsd.gamehub.DTO.CommunityHighlightsDTO;
import java.time.Instant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface IActivityService {
    void recordWishlistAdd(String username, String gameName);

    void recordReview(String username, String gameName, String reviewId, int score);

    void recordLikeReview(String username, String gameName, String reviewId);

    void removeLikeReview(String username, String reviewId);

    void recordFollow(String username, String targetUsername);

    void removeFollow(String username, String targetUsername);

    Page<ActivityDTO> getFriendsActivity(String username, Pageable pageable);

    // avanza il "segnalibro" del feed: le attivita' fino a upTo risultano viste. Non arretra mai
    // e non supera l'istante corrente. Torna false solo se la scrittura e' fallita.
    boolean markFeedSeen(String username, Instant upTo);

    CommunityHighlightsDTO getCommunityHighlights();
}
