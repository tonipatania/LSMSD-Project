package it.unipi.lsmsd.gamehub.service.impl;

import it.unipi.lsmsd.gamehub.DTO.GameDTO;
import it.unipi.lsmsd.gamehub.model.GameNeo4j;
import it.unipi.lsmsd.gamehub.model.User;
import it.unipi.lsmsd.gamehub.repository.LoginRepository;
import it.unipi.lsmsd.gamehub.repository.UserNeo4jRepository;
import it.unipi.lsmsd.gamehub.service.IInteractionGameService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;


@Service
public class InteractionGameService implements IInteractionGameService {
    @Autowired
    private UserNeo4jRepository userNeo4jRepository;

    @Override
    public List<GameNeo4j> getUserWishlist(String username) {
        try {
            return userNeo4jRepository.findByUsername(username);
        }catch (Exception e){
            System.out.println(e.getMessage());
            return null;
        }
    }

    @Override
    public ResponseEntity<String> addGameToWishlist(String username, String name) {
        try {
            userNeo4jRepository.addGameToUser(username, name);
            return new ResponseEntity<>("game correctly added", HttpStatus.OK);
        }catch (Exception e){
            return new ResponseEntity<>("error in wishlist adding: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public ResponseEntity<String> deleteGameToWishlist(String username, String name) {
        try {
            userNeo4jRepository.deleteGameFromUser(username, name);
            return new ResponseEntity<>("game correctly deleted", HttpStatus.OK);
        }catch (Exception e){
            return new ResponseEntity<>("error in deleting game: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}
