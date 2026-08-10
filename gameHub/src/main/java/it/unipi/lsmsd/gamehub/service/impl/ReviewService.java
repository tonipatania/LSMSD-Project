package it.unipi.lsmsd.gamehub.service.impl;

import it.unipi.lsmsd.gamehub.DTO.*;
import it.unipi.lsmsd.gamehub.model.Game;
import it.unipi.lsmsd.gamehub.model.Review;
import it.unipi.lsmsd.gamehub.repository.GameRepository;
import it.unipi.lsmsd.gamehub.repository.LoginRepository;
import it.unipi.lsmsd.gamehub.repository.ReviewRepository;
import it.unipi.lsmsd.gamehub.service.IGameService;
import it.unipi.lsmsd.gamehub.service.IReviewService;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class ReviewService implements IReviewService {

    @Autowired private ReviewRepository reviewRepository;

    @Autowired private GameRepository gameRepository;

    @Autowired private LoginRepository loginRepository;

    @Autowired private IGameService gameService;

    @Override
    public List<Review> retrieveReviewByTitle(ReviewDTO reviewDTO) {
        try {
            return reviewRepository.findByTitle(reviewDTO.getTitle());
        } catch (Exception e) {
            log.error("Errore in retrieveReviewByTitle", e);
            return null;
        }
    }

    @Override
    public List<ReviewDTOAggregation> retrieveAggregateFirstAndLastUserLike() {
        try {
            List<ReviewDTOAggregation> reviewList = reviewRepository.findAggregation2();
            if (!reviewList.isEmpty()) {
                return reviewList;
            }
            return null;
        } catch (Exception e) {
            log.error("Errore in retrieveAggregateFirstAndLastUserLike", e);
            return null;
        }
    }

    @Override
    public List<ReviewDTOAggregation2> findAggregation3() {
        try {
            return reviewRepository.findAggregation3();
        } catch (Exception e) {
            log.error("Errore in findAggregation3", e);
            return null;
        }
    }

    @Override
    public List<Review> retrieveByTitleOrderByLikeCountDesc(ReviewDTO reviewDTO, int limit) {
        Pageable pageable = PageRequest.of(0, limit);
        return reviewRepository.findByTitleOrderByLikeCountDesc(reviewDTO.getTitle(), pageable);
    }

    // TENGO LOCALE
    @Override
    public Review createReview(ReviewDTO reviewDTO) {
        // converto il dto in model entity
        try {
            List<Game> game = gameRepository.findByName(reviewDTO.getTitle());
            // check both if the game is present in the db and if the user is present in the db
            if (game != null && loginRepository.findByUsername(reviewDTO.getUsername()) != null) {
                ModelMapper modelMapper = new ModelMapper();
                Review review = modelMapper.map(reviewDTO, Review.class);
                // inserisco il model nel db

                reviewRepository.save(review);

                // keep the embedded top-reviews list on the game document in sync
                gameService.updateGameReviewFromScratch(game.get(0), 20);

                return review;
            }
            return null;
        } catch (Exception e) {
            log.error("Errore nella creazione del gioco", e);
            return null;
        }
    }

    @Override
    public ResponseEntity<String> deleteReview(String id) {
        try {
            Optional<Review> review = reviewRepository.findById(id);
            if (review.isEmpty()) {
                return new ResponseEntity<>("Review not found", HttpStatus.NOT_FOUND);
            }

            reviewRepository.deleteById(id);

            List<Game> game = gameRepository.findByName(review.get().getTitle());
            if (game != null && !game.isEmpty()) {
                gameService.updateGameReviewFromScratch(game.get(0), 20);
            }

            return new ResponseEntity<>("review deleted", HttpStatus.OK);
        } catch (Exception e) {
            log.error("Errore in deleteReview", e);
            return new ResponseEntity<>(
                    "Error in deleting the review: " + e.getMessage(),
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}
