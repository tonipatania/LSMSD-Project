package it.unipi.lsmsd.gamehub.service;


import it.unipi.lsmsd.gamehub.DTO.GameDTO;
import it.unipi.lsmsd.gamehub.model.GameNeo4j;
import it.unipi.lsmsd.gamehub.model.Like;
import org.springframework.http.ResponseEntity;

import java.util.List;

public interface IGameNeo4jService {

    GameNeo4j getGameByTitle(String id);
    Integer getGamesIngoingLinks(String name);
    ResponseEntity<List<GameDTO>> getSuggestGames(String userId);


    public void removeGame(String name);
    public void addGame(String id, String name, String developers, String categories, String genres);
    public GameNeo4j getGame(String name);
    boolean updateGame(String name, String newName, String newDevelopers, String newCategories, String newGenres);
}
