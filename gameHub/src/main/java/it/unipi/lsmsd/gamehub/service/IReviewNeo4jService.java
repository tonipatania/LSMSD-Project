package it.unipi.lsmsd.gamehub.service;

import org.springframework.http.ResponseEntity;

public interface IReviewNeo4jService {
    // public void loadReview();

    Integer getReviewsIngoingLinks(String id);

    public ResponseEntity<String> createReview(String idReview);

    public ResponseEntity<String> removeReview(String idReview);
}
