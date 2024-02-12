package it.unipi.lsmsd.gamehub.controller;

import it.unipi.lsmsd.gamehub.DTO.*;
import it.unipi.lsmsd.gamehub.model.Review;
import it.unipi.lsmsd.gamehub.service.ILoginService;
import it.unipi.lsmsd.gamehub.service.IReviewNeo4jService;
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
    private IReviewNeo4jService reviewNeo4jService;

    /*@PostMapping("loadReview")
    public ResponseEntity<String> syncReview() {
        reviewNeo4jService.loadReview();
        return new ResponseEntity<>("Apposto", HttpStatus.OK);
    }*/

    //path cambiato
    @GetMapping("gameSelected/searchByGameTitle")
    public ResponseEntity<Object> retrieveReviewByTitle(@RequestBody ReviewDTO reviewDTO) {
        List<Review> reviewList = review2Service.retrieveReviewByTitle(reviewDTO);
        if (reviewList!=null && !reviewList.isEmpty()) {
            return ResponseEntity.ok(reviewList);
        }else if (reviewList!=null && reviewList.isEmpty()){
            return ResponseEntity.ok("reviewList empty");
        }
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

    // order games by reviews userscore
    @GetMapping("/aggr2")
    public ResponseEntity<List<ReviewDTOAggregation2>> findAggregation3() {
        List<ReviewDTOAggregation2> reviewList = review2Service.findAggregation3();

        if (!reviewList.isEmpty()) {
            return ResponseEntity.ok(reviewList);
        }
        System.out.println("gamelist empty");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }


    //TENGO LOCALE
    //modifico il fatto di non specificare l'id quando inserisco la recensione, faccio ritornare una Review alla
    //funzione createReview
    @PostMapping("/gameSelected/create/{userId}")
    public ResponseEntity<String> createGame(@PathVariable String userId,@RequestBody ReviewDTO reviewDTO) {
        // creo review in mongo
        Review review = review2Service.createReview(reviewDTO);
        if(review == null) {
            return new ResponseEntity<>("error in review creation", HttpStatus.OK);
        }
        // creo su neo4j
        ResponseEntity<String> response = reviewNeo4jService.createReview(review.getId());
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

}
