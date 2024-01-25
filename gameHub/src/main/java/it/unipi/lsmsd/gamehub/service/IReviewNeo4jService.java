package it.unipi.lsmsd.gamehub.service;

import it.unipi.lsmsd.gamehub.model.Game;
import it.unipi.lsmsd.gamehub.model.GameNeo4j;
import org.modelmapper.ModelMapper;

import java.util.List;

public interface IReviewNeo4jService {
    public void loadReview();

    Integer getReviewsIngoingLinks(String id);


}
