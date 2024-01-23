package it.unipi.lsmsd.gamehub.repository;

import it.unipi.lsmsd.gamehub.model.Game;
import it.unipi.lsmsd.gamehub.model.Review;
import it.unipi.lsmsd.gamehub.repository.MongoDBAggregation.GameRepositoryCustom;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface GameRepository extends MongoRepository<Game,String>, GameRepositoryCustom {
    List<Game> findByName(String name);
    List<Game> findByGenres(String genres);
    List<Game> findByAvgScoreGreaterThanEqual(int avgScore);
    List<Game> findByGenresAndAvgScoreGreaterThanEqual(String genres,int avgScore);
    Page<Game> findAll(Pageable pageable);
<<<<<<< HEAD






=======
>>>>>>> a41d8d1145af7195cc875f96112cff9544475d5b
}
