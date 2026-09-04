package it.unipi.lsmsd.gamehub.controller;

import it.unipi.lsmsd.gamehub.DTO.ReviewDTO;
import it.unipi.lsmsd.gamehub.model.Review;
import it.unipi.lsmsd.gamehub.service.ILoginService;
import it.unipi.lsmsd.gamehub.service.IReviewNeo4jService;
import it.unipi.lsmsd.gamehub.service.IReviewService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RequestMapping("review")
@RestController
@Slf4j
public class ReviewController {
    @Autowired private IReviewService review2Service;

    @Autowired private ILoginService iLoginService;
    @Autowired private IReviewNeo4jService reviewNeo4jService;

    /*Postman parameters
    {
        "title":"BARRIER X",
            "username":"Kaistlin",
            "comment":"Amazing",
            "userScore":8
    }*/
    @PostMapping("/gameSelected/create")
    public ResponseEntity<String> createGame(@RequestBody ReviewDTO reviewDTO) {
        // creo review in mongo
        Review review = review2Service.createReview(reviewDTO);
        if (review == null) {
            log.error("Errore nella creazione della review per il gioco {}", reviewDTO.getTitle());
            return new ResponseEntity<>("error in review creation", HttpStatus.OK);
        }
        // creo su neo4j
        ResponseEntity<String> response = reviewNeo4jService.createReview(review.getId());
        if (response.getStatusCode() == HttpStatus.CREATED) {
            return response;
        }
        // cancellare review in mongo
        log.error(
                "Creazione della review {} fallita in Neo4j, rollback del documento Mongo",
                review.getId());
        return review2Service.deleteReview(review.getId());
    }

    @DeleteMapping("/reviewSelected/delete/{userId}")
    public ResponseEntity<String> deleteGame(
            @PathVariable String userId, @RequestParam String reviewId) {
        // controllo se si tratta di admin
        ResponseEntity<String> responseEntity = iLoginService.roleUser(userId);
        if (responseEntity.getStatusCode() != HttpStatus.OK) {
            log.warn(
                    "Utente {} senza permessi ha tentato di eliminare la review {}",
                    userId,
                    reviewId);
            return responseEntity;
        }
        // cancello su mongo
        responseEntity = review2Service.deleteReview(reviewId);
        if (responseEntity.getStatusCode() != HttpStatus.OK) {
            return responseEntity;
        }
        // cancello anche in neo4j
        return reviewNeo4jService.removeReview(reviewId);
    }
}
