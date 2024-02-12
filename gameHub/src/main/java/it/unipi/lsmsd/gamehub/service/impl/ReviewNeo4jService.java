package it.unipi.lsmsd.gamehub.service.impl;

import it.unipi.lsmsd.gamehub.model.Game;
import it.unipi.lsmsd.gamehub.model.GameNeo4j;
import it.unipi.lsmsd.gamehub.model.Review;
import it.unipi.lsmsd.gamehub.model.ReviewNeo4j;
import it.unipi.lsmsd.gamehub.repository.ReviewNeo4jRepository;
import it.unipi.lsmsd.gamehub.repository.ReviewRepository;
import it.unipi.lsmsd.gamehub.service.IReviewNeo4jService;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.aggregation.ArrayOperators;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ReviewNeo4jService implements IReviewNeo4jService {

    @Autowired
    ReviewRepository reviewRepository;

    @Autowired
    ReviewNeo4jRepository reviewNeo4jRepository;

    /*@Override
    public void loadReview() {
            List<Review> reviews = reviewRepository.findAll();
            ModelMapper modelMapper = new ModelMapper();
            List<ReviewNeo4j> graphReviews = reviews.stream().map(Review -> modelMapper.map(Review, ReviewNeo4j.class)).toList();
            for(int i=0; i<20; i++)
                reviewNeo4jRepository.save(graphReviews.get(i));
    }*/

    public Integer getReviewsIngoingLinks(String id){
        try {
            return reviewNeo4jRepository.findReviewIngoingLinks(id);
        }catch (Exception e){
            System.out.println(e.getMessage());
            return null;
        }
    }
    public ResponseEntity<String> createReview(String idReview) {
        try {
            ReviewNeo4j reviewNeo4j = new ReviewNeo4j(idReview);
            ReviewNeo4j savedReview = reviewNeo4jRepository.save(reviewNeo4j);
            return new ResponseEntity<>("corrected created review", HttpStatus.CREATED);
        }
        catch (Exception e) {
            return new ResponseEntity<>("error saving in neo4j: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
    public ResponseEntity<String> removeReview(String idReview) {
        try {
            ReviewNeo4j reviewNeo4j = new ReviewNeo4j(idReview);
            reviewNeo4jRepository.delete(reviewNeo4j);
            return new ResponseEntity<>("remove correct", HttpStatus.OK);
        }
        catch (Exception e) {
            return new ResponseEntity<>("error in deleting review: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}
