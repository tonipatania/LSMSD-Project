package it.unipi.lsmsd.gamehub.service;

import it.unipi.lsmsd.gamehub.DTO.*;
import it.unipi.lsmsd.gamehub.model.Review;
import java.util.List;
import org.springframework.http.ResponseEntity;

public interface IReviewService {
    public List<Review> retrieveReviewByTitle(ReviewDTO reviewDTO);

    // public List<ReviewDTOAggregation> retrieveAggregateReviewsByTitleAndSortByLike();
    public List<ReviewDTOAggregation> retrieveAggregateFirstAndLastUserLike();

    public List<ReviewDTOAggregation2> findAggregation3();

    public List<Review> retrieveByTitleOrderByLikeCountDesc(ReviewDTO reviewDTO, int limit);

    public Review createReview(ReviewDTO review);

    public ResponseEntity<String> deleteReview(String id);
}
