package it.unipi.lsmsd.gamehub.service.impl;


import it.unipi.lsmsd.gamehub.model.GameNeo4j;
import it.unipi.lsmsd.gamehub.repository.GameNeo4jRepository;
import it.unipi.lsmsd.gamehub.repository.UserNeo4jRepository;
import it.unipi.lsmsd.gamehub.service.IGameNeo4jService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Random;

@Service
public class GameNeo4jService implements IGameNeo4jService {
    @Autowired
    private GameNeo4jRepository gameNeo4jRepository;
    @Autowired
    private UserNeo4jRepository userNeo4jRepository;

    @Override
    public Integer getGamesIngoingLinks(String name) {
        try {
            return gameNeo4jRepository.findGameIngoingLinks(name);
        }catch (Exception e){
            System.out.println(e.getMessage());
            return null;
        }
    }
    @Override
    public ResponseEntity<List<GameNeo4j>> getSuggestGames(String username) {
        try {
            // recupero i giochi della wishlist
            List<GameNeo4j> gameWishlist = userNeo4jRepository.findByUsername(username);
            // scelgo un gioco casuale
            Random random = new Random();
            int randomIndex = random.nextInt(gameWishlist.size());

            List<GameNeo4j> games = gameNeo4jRepository.findSuggestGames(gameWishlist.get(randomIndex).getId(), username);
//            ModelMapper modelMapper = new ModelMapper();
//            List<GameDTO> gameDTOS = games.stream()
//                    .map(game -> modelMapper.map(game, GameDTO.class))
//                    .collect(Collectors.toList());
            return new ResponseEntity<>(games, HttpStatus.OK);
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
