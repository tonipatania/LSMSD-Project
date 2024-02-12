package it.unipi.lsmsd.gamehub.service.impl;

import it.unipi.lsmsd.gamehub.DTO.*;
import it.unipi.lsmsd.gamehub.model.Game;
import it.unipi.lsmsd.gamehub.model.Review;
import it.unipi.lsmsd.gamehub.repository.GameRepository;
import it.unipi.lsmsd.gamehub.repository.LoginRepository;
import it.unipi.lsmsd.gamehub.repository.ReviewRepository;
import it.unipi.lsmsd.gamehub.service.IGameService;
import it.unipi.lsmsd.gamehub.service.IReviewService;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.GroupOperation;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Service
public class ReviewService implements IReviewService {

    @Autowired
    private ReviewRepository reviewRepository;

    @Autowired
    private GameRepository gameRepository;

    @Autowired
    private LoginRepository loginRepository;

    @Autowired
    private IGameService gameService;

    //TENGO LOCALE
    @Override
    public List<Review> retrieveReviewByTitle(ReviewDTO reviewDTO) {
        try {
            List<Game> game=gameRepository.findByName(reviewDTO.getTitle());
            if(game!=null && !game.isEmpty()){
                //compare if the more reviews we want to retrieve are already present il the review embedded list of the game
                //we add one more read for this comparison


                //retrieve the reviews embedded in game
                List<Review> reviewEmbeddedList=game.get(0).getReviews();

                //retrieve all the reviews about a game
                List<Review> reviewNotEmbeddedList=reviewRepository.findByTitle(reviewDTO.getTitle());

                List<Review> reviewsNotPresentInEmbeddedList = new ArrayList<>();

                // Iterate through reviewNotEmbeddedList
                for (Review review : reviewNotEmbeddedList) {
                    boolean foundInEmbeddedList = false;
                    // Check if the ID of the current review is present in the embedded list
                    for (Review embeddedReview : reviewEmbeddedList) {
                        if (review.getId().equals(embeddedReview.getId())) {
                            foundInEmbeddedList = true;
                            break;
                        }
                    }
                    // If the review is not found in the embedded list, add it to reviewsNotPresentInEmbeddedList
                    if (!foundInEmbeddedList) {
                        reviewsNotPresentInEmbeddedList.add(review);
                    }
                }


                return reviewsNotPresentInEmbeddedList;
            }
            return Collections.emptyList();
        }catch (Exception e){
            System.out.println(e.getMessage());
            return null;
        }

    }

    @Override
    public List<ReviewDTOAggregation> retrieveAggregateFirstAndLastUserLike() {
        try {
            List<ReviewDTOAggregation> reviewList= reviewRepository.findAggregation2();
            if(!reviewList.isEmpty()){
                return reviewList;
            }
            return null;
        }catch (Exception e){
            System.out.println(e.getMessage());
            return null;
        }

    }

    @Override
    public List<ReviewDTOAggregation2> findAggregation3() {
        try {
            return reviewRepository.findAggregation3();
        }catch (Exception e){
            System.out.println(e.getMessage());
            return null;
        }

    }

    @Override
    public List<Review> retrieveByTitleOrderByLikeCountDesc(ReviewDTO reviewDTO, int limit) {
        Pageable pageable = PageRequest.of(0, limit);
        return reviewRepository.findByTitleOrderByLikeCountDesc(reviewDTO.getTitle(),pageable);
    }

    //TENGO LOCALE
    @Override
    public Review createReview(ReviewDTO reviewDTO) {
        // converto il dto in model entity
        try {
            List<Game> game=gameRepository.findByName(reviewDTO.getTitle());
            //check both if the game is present in the db and if the user is present in the db
            if(game!=null && loginRepository.findByUsername(reviewDTO.getUsername())!=null) {
                ModelMapper modelMapper = new ModelMapper();
                Review review = modelMapper.map(reviewDTO, Review.class);
                // inserisco il model nel db

                Review saved = reviewRepository.save(review);





                //before i check with one read if the review list is never updated
                if(game.get(0).getReviews()!=null && game.get(0).getReviews().size()==1 && game.get(0).getReviews().get(0).getId()==null){

                    //after i get the review reffering to that game to see if they are > 10, in this way we have more read operations
                    //because we have to retrieve all the reviews
                    List<Review> reviewList= reviewRepository.findByTitle(reviewDTO.getTitle());

                    if(reviewList.size()>=1){
                        gameService.updateGameReviewFromScratch(game.get(0),20);
                    }


                }


                return review;
            }
            return null;
        }catch (Exception e) {
            System.out.println("Errore nella creazione del gioco: " + e.getMessage());
            return null;
        }
    }

    //TENGO LCOALE
    @Override
    public ResponseEntity<String> deleteReview(String id) {
        try {
            Optional<Review> review=reviewRepository.findById(id);
            reviewRepository.deleteById(id);
            //if is present delete the review also in the game review list
            List<Game> game=gameRepository.findByName(review.get().getTitle());

            List<Review> reviewList=game.get(0).getReviews();
            //if present remove the review from the embedded review list
            for (int i=0;i<reviewList.size();i++){
                if(review.get().getId().equals(reviewList.get(i).getId())){
                    reviewList.remove(i);
                    gameRepository.save(game.get(0));
                }
            }


            return new ResponseEntity<>("review deleted", HttpStatus.OK);
        }catch (Exception e) {
            return new ResponseEntity<>("Error in deleting the review: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }


}
