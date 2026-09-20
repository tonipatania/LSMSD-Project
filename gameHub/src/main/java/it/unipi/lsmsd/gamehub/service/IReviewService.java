package it.unipi.lsmsd.gamehub.service;

import it.unipi.lsmsd.gamehub.DTO.ReviewDTO;
import it.unipi.lsmsd.gamehub.model.Review;
import java.util.List;
import java.util.Optional;
import org.springframework.http.ResponseEntity;

public interface IReviewService {
    public List<Review> retrieveByTitleOrderByLikeCountDesc(ReviewDTO reviewDTO, int limit);

    public Review createReview(ReviewDTO review);

    public Optional<Review> getReview(String id);

    public ResponseEntity<String> deleteReview(String id);
}
