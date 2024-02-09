package it.unipi.lsmsd.gamehub.controller;

import it.unipi.lsmsd.gamehub.DTO.*;
import it.unipi.lsmsd.gamehub.model.Review;
import it.unipi.lsmsd.gamehub.service.ILoginService;
import it.unipi.lsmsd.gamehub.service.IReviewService;
import it.unipi.lsmsd.gamehub.service.impl.ReviewNeo4jService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RequestMapping("review")
@RestController
public class ReviewController {
    @Autowired
    private IReviewService review2Service;

    @Autowired
    private ILoginService iLoginService;
    @Autowired
    ReviewNeo4jService reviewNeo4jService;

    @GetMapping("/searchByGameTitle")
    public ResponseEntity<List<Review>> retrieveReviewByTitle(@RequestBody ReviewDTO reviewDTO) {
        List<Review> reviewList = review2Service.retrieveReviewByTitle(reviewDTO);
        if (!reviewList.isEmpty()) {
            return ResponseEntity.ok(reviewList);
        }
        System.out.println("gamelist empty");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }

    //count the top 20 like review given a game title
    @GetMapping("/gameSelected/topCountReview")
    public ResponseEntity<List<Review>> retrieveByTitleOrderByLikeCountDesc(@RequestBody ReviewDTO reviewDTO) {
        List<Review> reviewList = review2Service.retrieveByTitleOrderByLikeCountDesc(reviewDTO,20);
        if (!reviewList.isEmpty()) {
            return ResponseEntity.ok(reviewList);
        }
        System.out.println("gamelist empty");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }

    @GetMapping("/aggr1")
    public ResponseEntity<List<ReviewDTOAggregation>> retrieveAggregateFirstAndLastUserLike() {
        List<ReviewDTOAggregation> reviewList = review2Service.retrieveAggregateFirstAndLastUserLike();

        if (!reviewList.isEmpty()) {
            return ResponseEntity.ok(reviewList);
        }
        System.out.println("gamelist empty");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }

    @GetMapping("/aggr2")
    public ResponseEntity<List<ReviewDTOAggregation2>> findAggregation3() {
        List<ReviewDTOAggregation2> reviewList = review2Service.findAggregation3();

        if (!reviewList.isEmpty()) {
            return ResponseEntity.ok(reviewList);
        }
        System.out.println("gamelist empty");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }

    @PostMapping("/gameSelected/create/{userId}")
    public ResponseEntity<String> createGame(@PathVariable String userId,@RequestBody ReviewDTO reviewDTO) {
        // controllo se si tratta di admin
//        ResponseEntity<String> responseEntity= iLoginService.roleUser(userId);
//        if(responseEntity.getStatusCode() != HttpStatus.OK) {
//            return responseEntity;
//        }
        // creo review in mongo
        ReviewDTO review = review2Service.createReview(reviewDTO);
        if(review == null) {
            return new ResponseEntity<>("error in review creation", HttpStatus.OK);
        }
        // creo su neo4j
        ResponseEntity<String> response = reviewNeo4jService.createReview(reviewDTO.getId());
        if(response.getStatusCode() == HttpStatus.CREATED) {
            return response;
        }
        // cancellare review in mongo
        return review2Service.deleteReview(reviewDTO.getId());
    }

    @DeleteMapping("/reviewSelected/delete/{userId}")
    public ResponseEntity<String> deleteGame(@PathVariable String userId, @RequestParam String reviewId) {
        // controllo se si tratta di admin
        ResponseEntity<String> responseEntity= iLoginService.roleUser(userId);
        if(responseEntity.getStatusCode() != HttpStatus.OK) {
            return responseEntity;
        }
        // cancello su mongo
        responseEntity = review2Service.deleteReview(reviewId);
        if(responseEntity.getStatusCode() != HttpStatus.OK) {
            return responseEntity;
        }
        // cancello anche in neo4j
        return reviewNeo4jService.removeReview(reviewId);
    }
    @PostMapping("/loadreviews")
    public ResponseEntity<String> reqReviews() {
        reviewNeo4jService.loadReview();
        return ResponseEntity.ok("Recensioni caricate");
    }

    @GetMapping("/getReviewsIngoingLinks")
    public ResponseEntity<Integer> getReviewsIngoingLinks(@RequestParam String id) {
        Integer countLinks= reviewNeo4jService.getReviewsIngoingLinks(id);
        if (countLinks!=null) {
            return ResponseEntity.ok(countLinks);
        }
        System.out.println("review empty");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }
}
