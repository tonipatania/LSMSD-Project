package it.unipi.lsmsd.gamehub.service;

import it.unipi.lsmsd.gamehub.DTO.GameDTO;
import it.unipi.lsmsd.gamehub.model.Game;
import it.unipi.lsmsd.gamehub.model.Review;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;

public interface IGameService {
    public Page<Game> retrieveGamesByParameters(
            String name, List<String> genres, Integer avgScore, Pageable pageable);

    public List<String> findDistinctGenres();

    // public List<Review> updateGameReview(ReviewDTO reviewDTO, int limit);

    public long countGameDocument();

    public Page<Game> getAll(Pageable pageable);

    public List<Game> getGamesWithReviews(int limit);

    public ResponseEntity<String> createGame(GameDTO gameDTO);

    public ResponseEntity<String> deleteGame(String id);

    List<Review> updateGameReviewFromScratch(Game game, int limit);

    public List<Review> updateGameEmbeddedReview(Game game);
}
