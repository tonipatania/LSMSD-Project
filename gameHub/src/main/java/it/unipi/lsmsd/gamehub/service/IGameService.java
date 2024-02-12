package it.unipi.lsmsd.gamehub.service;

import it.unipi.lsmsd.gamehub.DTO.GameDTO;
import it.unipi.lsmsd.gamehub.DTO.GameDTOAggregation;
import it.unipi.lsmsd.gamehub.DTO.GameDTOAggregation2;
import it.unipi.lsmsd.gamehub.DTO.ReviewDTO;
import it.unipi.lsmsd.gamehub.model.Game;
import it.unipi.lsmsd.gamehub.model.Review;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.http.ResponseEntity;

import java.util.List;

public interface IGameService {
    public List<Game> retrieveGamesByParameters(GameDTO gameDTO);

    public List<GameDTOAggregation> retrieveAggregateGamesByGenresAndSortByScore();

    public List<GameDTOAggregation2> findAggregation4();

    //public List<Review> updateGameReview(ReviewDTO reviewDTO, int limit);

    public long countGameDocument();

    public Page<Game> getAll(Pageable pageable);
    public ResponseEntity<String> createGame(GameDTO gameDTO);
    public ResponseEntity<String> deleteGame(String id);

    List<Review> updateGameReviewFromScratch(Game game, int limit);

    public List<Review> updateGameEmbeddedReview(Game game);



}
