package it.unipi.lsmsd.gamehub.service;


import it.unipi.lsmsd.gamehub.DTO.GameDTO;
import it.unipi.lsmsd.gamehub.model.GameNeo4j;
import org.springframework.http.ResponseEntity;

import java.util.List;

public interface IGameNeo4jService {
    Integer getGamesIngoingLinks(String name);
    ResponseEntity<List<GameNeo4j>> getSuggestGames(String username);

    public ResponseEntity<String> removeGame(String gameId);
    public ResponseEntity<String> addGame(String id, String name);
}
