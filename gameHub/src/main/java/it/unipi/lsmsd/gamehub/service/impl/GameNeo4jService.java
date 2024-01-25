package it.unipi.lsmsd.gamehub.service.impl;


import it.unipi.lsmsd.gamehub.DTO.GameDTO;
import it.unipi.lsmsd.gamehub.DTO.GameWishlistDTO;
import it.unipi.lsmsd.gamehub.model.GameNeo4j;
import it.unipi.lsmsd.gamehub.model.User;
import it.unipi.lsmsd.gamehub.repository.GameNeo4jRepository;
import it.unipi.lsmsd.gamehub.repository.LoginRepository;
import it.unipi.lsmsd.gamehub.repository.UserNeo4jRepository;
import it.unipi.lsmsd.gamehub.service.IGameNeo4jService;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class GameNeo4jService implements IGameNeo4jService {
    @Autowired
    private GameNeo4jRepository gameNeo4jRepository;
    @Autowired
    private LoginRepository loginRepository;

    @Override
    public GameNeo4j getGameByTitle(String name) {
        try {
            return gameNeo4jRepository.findGameByName(name);
        } catch (Exception e){
            System.out.println(e.getMessage());
            return null;
        }
    }

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
    public ResponseEntity<List<GameDTO>> getSuggestGames(String userId) {
        try {
            // recupero l ultimo gioco aggiunto in wishlist
            Optional<User> user = loginRepository.findById(userId);
            List<GameWishlistDTO> gameWishlistDTOS = user.get().getGames();
            GameWishlistDTO lastGameWishlistDTO = gameWishlistDTOS.get(gameWishlistDTOS.size() - 1);

            List<GameNeo4j> games = gameNeo4jRepository.findSuggestGames(lastGameWishlistDTO.getId(), userId);
            ModelMapper modelMapper = new ModelMapper();
            List<GameDTO> gameDTOS = games.stream()
                    .map(game -> modelMapper.map(game, GameDTO.class))
                    .collect(Collectors.toList());
            return new ResponseEntity<>(gameDTOS, HttpStatus.OK);
        } catch (Exception e) {
            System.out.println("Errore durante l accesso al database: " + e.getMessage());
            return new ResponseEntity<>(null, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}
