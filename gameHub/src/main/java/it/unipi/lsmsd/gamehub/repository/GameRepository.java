package it.unipi.lsmsd.gamehub.repository;

import it.unipi.lsmsd.gamehub.model.Game;
import it.unipi.lsmsd.gamehub.repository.MongoDBAggregation.GameRepositoryCustom;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface GameRepository extends MongoRepository<Game, String>, GameRepositoryCustom {
    List<Game> findByName(String name);

    Page<Game> findAll(Pageable pageable);

    // avgScore isn't derived from the embedded Reviews array (most 100-score games have only
    // a placeholder empty review), so picking games for a review feed needs to filter on
    // actually having review content instead of sorting by score.
    //
    // hasReviews e' un campo indicizzato precalcolato (vedi Game.hasReviews e
    // GameService.updateGameReviewFromScratch): il filtro precedente su 'Reviews.0.Comment'
    // forzava un COLLSCAN perche' un array embedded non e' indicizzabile per posizione.
    @Query("{ 'hasReviews': true }")
    List<Game> findGamesWithReviews(Pageable pageable);
}
