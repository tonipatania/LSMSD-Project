package it.unipi.lsmsd.gamehub.service;

import it.unipi.lsmsd.gamehub.DTO.GameDTO;
import it.unipi.lsmsd.gamehub.DTO.GameDTOAggregation;
import it.unipi.lsmsd.gamehub.DTO.GameDTOAggregation2;
import it.unipi.lsmsd.gamehub.model.Game;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import java.util.List;

public interface IGameService {
    public List<Game> retrieveGamesByParameters(GameDTO gameDTO);

    public List<GameDTOAggregation> retrieveAggregateGamesByGenresAndSortByScore();

    public List<GameDTOAggregation2> findAggregation4();

    public Page<GameDTO> getAll(Pageable pageable);
    public GameDTO createGame(GameDTO gameDTO);
    public void deleteGame(String id);
}
