package it.unipi.lsmsd.gamehub.controller;

import it.unipi.lsmsd.gamehub.DTO.*;
import it.unipi.lsmsd.gamehub.model.Review;
import it.unipi.lsmsd.gamehub.service.ILoginService;
import it.unipi.lsmsd.gamehub.service.IReviewService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class ReviewController {
    @Autowired
    private IReviewService review2Service;

    @Autowired
    private ILoginService iLoginService;

    @GetMapping("/review")
    public ResponseEntity<List<Review>> retrieveReviewByTitle(@RequestBody ReviewDTO reviewDTO) {
        List<Review> reviewList = review2Service.retrieveReviewByTitle(reviewDTO);
        if (!reviewList.isEmpty()) {
            return ResponseEntity.ok(reviewList);
        }
        System.out.println("gamelist empty");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }

    //count the top 20 like review given a game title
    @GetMapping("/topCountReview")
    public ResponseEntity<List<Review>> retrieveByTitleOrderByLikeCountDesc(@RequestBody ReviewDTO reviewDTO) {
        List<Review> reviewList = review2Service.retrieveByTitleOrderByLikeCountDesc(reviewDTO,20);
        if (!reviewList.isEmpty()) {
            return ResponseEntity.ok(reviewList);
        }
        System.out.println("gamelist empty");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }

    @GetMapping("/reviewAggr1")
    public ResponseEntity<List<ReviewDTOAggregation>> retrieveAggregateFirstAndLastUserLike() {
        List<ReviewDTOAggregation> reviewList = review2Service.retrieveAggregateFirstAndLastUserLike();

        if (!reviewList.isEmpty()) {
            return ResponseEntity.ok(reviewList);
        }
        System.out.println("gamelist empty");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }

    @GetMapping("/reviewAggr2")
    public ResponseEntity<List<ReviewDTOAggregation2>> findAggregation3() {
        List<ReviewDTOAggregation2> reviewList = review2Service.findAggregation3();

        if (!reviewList.isEmpty()) {
            return ResponseEntity.ok(reviewList);
        }
        System.out.println("gamelist empty");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }

    @PostMapping("/createReview/{userId}")
    public ResponseEntity<Object> createGame(@PathVariable String userId,@RequestBody ReviewDTO reviewDTO) {
        // controllo se si tratta di admin
        ResponseEntity<Object> responseEntity= iLoginService.roleUser(userId);
        if(responseEntity.getStatusCode() == HttpStatus.UNAUTHORIZED) {
            return responseEntity;
        }
        else if (responseEntity.getStatusCode() == HttpStatus.INTERNAL_SERVER_ERROR) {
            return responseEntity;
        }

        ReviewDTO review = review2Service.createReview(reviewDTO);
        return new ResponseEntity<>(review, HttpStatus.CREATED);
    }




    @DeleteMapping("/deleteReview/{userId}")
    public ResponseEntity<Object> deleteGame(@PathVariable String userId, @RequestParam String reviewId) {
        // controllo se si tratta di admin
        ResponseEntity<Object> responseEntity= iLoginService.roleUser(userId);
        if(responseEntity.getStatusCode() == HttpStatus.UNAUTHORIZED) {
            return responseEntity;
        }
        else if (responseEntity.getStatusCode() == HttpStatus.INTERNAL_SERVER_ERROR) {
            return responseEntity;
        }

        review2Service.deleteReview(reviewId);
        return ResponseEntity.ok().build();
    }



}
