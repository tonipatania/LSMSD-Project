package it.unipi.lsmsd.gamehub.service;


import it.unipi.lsmsd.gamehub.model.Game;
import org.springframework.http.ResponseEntity;

import java.util.List;

public interface IGameNeo4jService {
    Integer getGamesIngoingLinks(String name);
    ResponseEntity<List<Game>> getSuggestGames(String username);

    public ResponseEntity<String> removeGame(String gameId);
    public ResponseEntity<String> addGame(String id, String name);
}
