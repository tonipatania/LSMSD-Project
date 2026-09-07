package it.unipi.lsmsd.gamehub.service;

import it.unipi.lsmsd.gamehub.DTO.ActivityDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface IActivityService {
    void recordWishlistAdd(String username, String gameName);

    void recordReview(String username, String gameName, String reviewId, int score);

    Page<ActivityDTO> getFriendsActivity(String username, Pageable pageable);
}
