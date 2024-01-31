package it.unipi.lsmsd.gamehub.service;

import it.unipi.lsmsd.gamehub.model.Game;
import it.unipi.lsmsd.gamehub.model.GameNeo4j;
import it.unipi.lsmsd.gamehub.model.ReviewNeo4j;
import org.modelmapper.ModelMapper;
import org.springframework.http.ResponseEntity;

import java.util.List;

public interface IReviewNeo4jService {
    public void loadReview();

    Integer getReviewsIngoingLinks(String id);

    public ResponseEntity<String> createReview(String idReview);
    public ResponseEntity<String> removeReview(String idReview);
}
