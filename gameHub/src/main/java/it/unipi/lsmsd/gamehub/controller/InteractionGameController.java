package it.unipi.lsmsd.gamehub.controller;

import it.unipi.lsmsd.gamehub.model.GameNeo4j;
import it.unipi.lsmsd.gamehub.service.IInteractionGameService;
import it.unipi.lsmsd.gamehub.service.impl.UserNeo4jService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

//tengo da remoto
@RequestMapping("wishlist")
@RestController
public class InteractionGameController {
    @Autowired
    private IInteractionGameService iInteractionGameService;

    /*@GetMapping("userSelected/view/{userId}")
    public ResponseEntity<List<GameNeo4j>> getUSerWishlist(@PathVariable String userId, @RequestParam String username) {
        List<GameNeo4j> gameList = iInteractionGameService.getUserWishlist(username);
        if (!gameList.isEmpty()) {
            return ResponseEntity.ok(gameList);
        }
        System.out.println("gamelist empty");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }

    @PostMapping("/gameSelected/addGame/{userId}")
    public ResponseEntity<String> addGameToWishlist(@PathVariable String userId, @RequestParam String username, @RequestParam String name) {
        return iInteractionGameService.addGameToWishlist(username, name);
    }


    @DeleteMapping("/gameSelected/deleteGame/{userId}")
    public ResponseEntity<String> deleteGameToWishlist(@PathVariable String userId, @RequestParam String username, @RequestParam String name) {
        return iInteractionGameService.deleteGameToWishlist(username, name);
    }*/

}
