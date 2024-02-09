package it.unipi.lsmsd.gamehub.service.impl;

import com.mongodb.MongoException;
import it.unipi.lsmsd.gamehub.DTO.GameWishlistDTO;
import it.unipi.lsmsd.gamehub.model.User;
import it.unipi.lsmsd.gamehub.repository.LoginRepository;
import it.unipi.lsmsd.gamehub.service.IInteractionGameService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

//rengo da remoto
@Service
public class InteractionGameService implements IInteractionGameService {
   /* @Autowired
    private LoginRepository loginRepository;
    @Override
    public ResponseEntity<Object> addGame(String userId, String idGame, String nameGame) {
        try {
            Optional<User> u = loginRepository.findById(userId);
            User user = u.get();
            GameWishlistDTO gameWishlist = new GameWishlistDTO(idGame,nameGame);

            if(user.getGames() != null) {
                user.getGames().add(gameWishlist);
            }
            // wishlist vuota
            else {
                // creo il campo games
                List<GameWishlistDTO> newGamesList = new ArrayList<>();
                newGamesList.add(gameWishlist);
                user.setGames(newGamesList);
            }
            loginRepository.save(user);
            return ResponseEntity.ok(null);
        }
        catch (Exception e) {
            System.out.println("Errore durante l'operazione: " + e.getMessage());
            return new ResponseEntity<>("Errore di comunicazione con il database", HttpStatus.INTERNAL_SERVER_ERROR);
        }

    }
    @Override
    public ResponseEntity<List<GameWishlistDTO>> getWishlist(String idUser) {
        try {
            Optional<User> user =  loginRepository.findById(idUser);

            if(user.get().getGames() == null) {
                return new ResponseEntity<>(null, HttpStatus.NO_CONTENT);
            } else if (user.get().getGames().isEmpty()) {
                return new ResponseEntity<>(null, HttpStatus.NO_CONTENT);
            }
            return new ResponseEntity<>(user.get().getGames(), HttpStatus.OK);
        }
        catch (MongoException e) {
            System.out.println("Errore durante il recupero dell'utente da MongoDB: " + e.getMessage());
            return new ResponseEntity<>(null, HttpStatus.INTERNAL_SERVER_ERROR);
        }

    }
    @Override
    public ResponseEntity<Object> removeGameWishlist(String userId, int index) {
        try {
            Optional<User> user = loginRepository.findById(userId);
            List<GameWishlistDTO> gameWishlistDTOS = user.get().getGames();
            GameWishlistDTO gameWishlistDTO = gameWishlistDTOS.remove(index);
            loginRepository.save(user.get());
            return ResponseEntity.ok(null);
        }
        catch (MongoException e) {
            System.out.println("Errore durante la modifica su MongoDB: " + e.getMessage());
            return new ResponseEntity<>("Errore di interazione col database", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }*/
}
