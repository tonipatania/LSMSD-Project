package it.unipi.lsmsd.gamehub.controller;

import it.unipi.lsmsd.gamehub.service.impl.ReviewNeo4jService;
import it.unipi.lsmsd.gamehub.service.impl.UserNeo4jService;
import org.neo4j.cypherdsl.core.Create;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ReviewNeo4jController {

    @Autowired
    ReviewNeo4jService reviewNeo4jService;

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
