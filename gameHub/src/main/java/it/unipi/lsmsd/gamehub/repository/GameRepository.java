package it.unipi.lsmsd.gamehub.repository;

import it.unipi.lsmsd.gamehub.model.Game;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;



@Repository
public interface GameRepository extends MongoRepository<Game, String> {
    Page<Game> findAll(Pageable pageable);
}
