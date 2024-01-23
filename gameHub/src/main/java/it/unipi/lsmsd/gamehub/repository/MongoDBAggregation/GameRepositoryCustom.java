package it.unipi.lsmsd.gamehub.repository.MongoDBAggregation;

import it.unipi.lsmsd.gamehub.DTO.GameDTOAggregation;
import it.unipi.lsmsd.gamehub.DTO.GameDTOAggregation2;
import it.unipi.lsmsd.gamehub.model.Game;

import java.util.List;


public interface GameRepositoryCustom {
    //List<Game> findAggregation();
    List<GameDTOAggregation> findAggregation();

    List<GameDTOAggregation2> findAggregation4();
}
