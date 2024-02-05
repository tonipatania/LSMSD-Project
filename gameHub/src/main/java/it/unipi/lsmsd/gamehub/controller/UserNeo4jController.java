package it.unipi.lsmsd.gamehub.controller;


import it.unipi.lsmsd.gamehub.model.GameNeo4j;
import it.unipi.lsmsd.gamehub.model.UserNeo4j;
import it.unipi.lsmsd.gamehub.service.ILoginService;
import it.unipi.lsmsd.gamehub.service.impl.GameNeo4jService;
import it.unipi.lsmsd.gamehub.service.impl.UserNeo4jService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
public class UserNeo4jController {
    @Autowired
    private UserNeo4jService userNeo4jService;
    @Autowired
    private GameNeo4jService gameNeo4jService;

    @Autowired
    private ILoginService iLoginService;

    @PostMapping("/sync")
    public ResponseEntity<String> syncUser() {
        userNeo4jService.SyncUser();
        return ResponseEntity.ok("Sincronizzazione completata");
    }

    //DA MODIFICARE NEL MAIN->TROVA LA LISTA DI GIOCHI DEGLI AMICI(DA MODIFICARE ANCHE SERVICE E REPOSITORY)
    @GetMapping("/wishlist")
    public ResponseEntity<List<GameNeo4j>> getUSerWishlist(@RequestParam String username, String friendUsername) {
        List<GameNeo4j> gameList = userNeo4jService.getUserWishlist(username,friendUsername);
        if (!gameList.isEmpty()) {
            return ResponseEntity.ok(gameList);
        }
        System.out.println("gamelist empty");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }

    @PostMapping("/addWishlistGame")
    public ResponseEntity<String> addGameToWishlist(@RequestParam String username,String name) {
        userNeo4jService.addGameToWishlist(username,name);
        if (name!=null) {
            return ResponseEntity.ok("gioco aggiunto");
        }
        System.out.println("nessun gioco aggiunto");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }

    @PostMapping("/deleteWishlistGame")
    public ResponseEntity<String> deleteGameToWishlist(@RequestParam String username,String name) {
        userNeo4jService.deleteGameToWishlist(username,name);
        if (name!=null) {
            return ResponseEntity.ok("gioco eliminato");
        }
        System.out.println("nessun gioco eliminato");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }

    @GetMapping("/followedUser")
    public ResponseEntity<List<UserNeo4j>> getFollowedUser(@RequestParam String username) {
        List<UserNeo4j> usersList = userNeo4jService.getFollowedUser(username);
        if (!usersList.isEmpty()) {
            return ResponseEntity.ok(usersList);
        }
        System.out.println("gamelist empty");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }

    @GetMapping("/FriendsOfFriends")
    public ResponseEntity<List<UserNeo4j>> getFriendsOfFriends(@RequestParam String username){
        List<UserNeo4j> userNeo4jList=userNeo4jService.getFriendsOfFriends(username);
        if (!userNeo4jList.isEmpty()) {
            return ResponseEntity.ok(userNeo4jList);
        }
        System.out.println("no friends of friends");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }

    //Query per amici suggeriti
    @GetMapping("/SuggestFriends")
    public ResponseEntity<List<UserNeo4j>> getSuggestFriends(@RequestParam String username){
        List<UserNeo4j> userNeo4jList=userNeo4jService.getSuggestedFriends(username);
        if (!userNeo4jList.isEmpty()) {
            return ResponseEntity.ok(userNeo4jList);
        }
        System.out.println("no suggested friends empty");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }


   /* @PostMapping("/addLikeReview")
    public ResponseEntity<String> addLikeToReview(@RequestParam String username,String id) {
        userNeo4jService.addGameToWishlist(username,id);
        if (id!=null) {
            return ResponseEntity.ok("like aggiunto");
        }
        System.out.println("nessun like aggiunto");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }*/

    //DA MODIFICARE NEL MAIN->AGGIUNGE LIKE AD UNA REVIEW(DA MODIFICARE SIA IN SERVICE CHE REPOSITORY)
    @PostMapping("/addLikeReview")
    public ResponseEntity<String> addLikeToReview(@RequestParam String username,String id) {
        boolean likeAdded=userNeo4jService.addLikeToReview(username,id);
        if (id!=null && likeAdded) {
            return ResponseEntity.ok("like aggiunto");
        }
        System.out.println("nessun like aggiunto");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }

    @PostMapping("/loadgames")
    public ResponseEntity<String> reqGames() {
        userNeo4jService.loadGames();
        return ResponseEntity.ok("Giochi caricati");
    }

    //DA AGGIUNGERE NEL MAIN-> CONTA IL NUMERO TOTALE DI GIOCHI E PUò FARLO SOLO L'ADMIN(AGGIUNGERE PARTI ANCHE DEL SERVICE)
    @GetMapping("/countUser/{userId}")
    public ResponseEntity<Object> countGame(@PathVariable String userId){
        ResponseEntity<Object> responseEntity= iLoginService.roleUser(userId);
        if(responseEntity.getStatusCode() == HttpStatus.UNAUTHORIZED) {
            return responseEntity;
        }
        else if (responseEntity.getStatusCode() == HttpStatus.INTERNAL_SERVER_ERROR) {
            return responseEntity;
        }

        long count= userNeo4jService.countUserDocument();
        return ResponseEntity.ok(count);
    }

}
