package it.unipi.lsmsd.gamehub.service.impl;


import it.unipi.lsmsd.gamehub.model.Game;
import it.unipi.lsmsd.gamehub.model.GameNeo4j;
import it.unipi.lsmsd.gamehub.repository.GameNeo4jRepository;
import it.unipi.lsmsd.gamehub.repository.GameRepository;
import it.unipi.lsmsd.gamehub.service.IGameNeo4jService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class GameNeo4jService implements IGameNeo4jService {
    @Autowired
    private GameNeo4jRepository gameNeo4jRepository;
    @Autowired
    private GameRepository gameRepository;

    @Override
    public Integer getGamesIngoingLinks(String name) {
        try {
            return gameNeo4jRepository.findGameIngoingLinks(name);
        }catch (Exception e){
            System.out.println(e.getMessage());
            return null;
        }
    }
    // numero di giochi consigliati mostrati nella sidebar della Home
    private static final int SUGGESTIONS_LIMIT = 10;

    @Override
    public ResponseEntity<List<Game>> getSuggestGames(String username) {
        try {
            // Consiglio basato su tutta la wishlist. Se la wishlist e' vuota si ripiega sui giochi
            // piu desiderati: la vecchia versione partiva dal gioco con la media recensioni piu
            // alta, che nel dataset e' quasi sempre un titolo di nicchia con zero wishlist, quindi
            // il suggerimento risultava sistematicamente vuoto.
            List<GameNeo4j> games = gameNeo4jRepository.findSuggestGames(username, SUGGESTIONS_LIMIT);
            if (games.isEmpty()) {
                games = gameNeo4jRepository.findMostWishlistedGames(SUGGESTIONS_LIMIT);
            }

            // GameNeo4j only carries id+name; fetch the full Mongo documents (image, genres,
            // score) for the card UI, since the ids are shared between the two stores
            List<String> ids = games.stream().map(GameNeo4j::getId).toList();
            List<Game> enrichedGames = gameRepository.findAllById(ids);
            return new ResponseEntity<>(enrichedGames, HttpStatus.OK);
        } catch (Exception e) {
            System.out.println("Errore durante l accesso al database: " + e.getMessage());
            return new ResponseEntity<>(null, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
    public ResponseEntity<String> removeGame(String gameId) {
        try {
            gameNeo4jRepository.removeGame(gameId);
            return new ResponseEntity<>("game deleted", HttpStatus.OK);
        }
        catch (Exception e) {
            return new ResponseEntity<>("deletion error: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
    public ResponseEntity<String> addGame(String id, String name){
        try {
            gameNeo4jRepository.addGame(id, name);
            return new ResponseEntity<>("game inserted correctly", HttpStatus.CREATED);
        }
        catch (Exception e) {
            return new ResponseEntity<>("error inserted game in neo4j: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}
