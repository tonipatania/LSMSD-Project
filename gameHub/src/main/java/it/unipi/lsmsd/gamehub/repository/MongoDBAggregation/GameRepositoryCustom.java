package it.unipi.lsmsd.gamehub.repository.MongoDBAggregation;

import it.unipi.lsmsd.gamehub.DTO.GameDTOAggregation;
import it.unipi.lsmsd.gamehub.DTO.GameDTOAggregation2;
import it.unipi.lsmsd.gamehub.model.Game;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface GameRepositoryCustom {
    // List<Game> findAggregation();
    List<GameDTOAggregation> findAggregation();

    List<GameDTOAggregation2> findAggregation4();

    Page<Game> searchGames(String name, List<String> genres, Integer avgScore, Pageable pageable);

    List<String> findDistinctGenres();
}
