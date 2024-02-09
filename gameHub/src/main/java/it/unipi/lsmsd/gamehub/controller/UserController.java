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
    private ILoginService iLoginService;


    // to load user from mongo to neo4j

    @PostMapping("/sync")
    public ResponseEntity<String> syncUser() {
        userNeo4jService.SyncUser();
        return ResponseEntity.ok("Sincronizzazione completata");
    }


    //DA MODIFICARE NEL MAIN->TROVA LA LISTA DI GIOCHI DEGLI AMICI(DA MODIFICARE ANCHE SERVICE E REPOSITORY)

    // to load games from mongo to neo4j
    @PostMapping("/loadgames")
    public ResponseEntity<String> reqGames() {
        userNeo4jService.loadGames();
        return ResponseEntity.ok("Giochi caricati");
    }


    //tengo locale(da spostare in interactionGame)
    @GetMapping("/wishlist")
    public ResponseEntity<Object> getUserWishlist(@RequestParam String username, String friendUsername) {
        List<GameNeo4j> gameList = userNeo4jService.getUserWishlist(username,friendUsername);
        if (gameList != null && !gameList.isEmpty()) {
            return ResponseEntity.ok(gameList);
        } else if (gameList != null && gameList.isEmpty()) {
            return ResponseEntity.ok("empty gameList");
        }

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }

    //tengo locale(da spostare in interactionGame)
    @PostMapping("/addWishlistGame")
    public ResponseEntity<String> addGameToWishlist(@RequestParam String username,String name) {
        Boolean result=userNeo4jService.addGameToWishlist(username,name);
        if (result) {
            return ResponseEntity.ok("game added");
        }else if(!result){
            return ResponseEntity.ok("no game added");
        }

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }

    //tengo locale(da spostare in interactionGame)
    @PostMapping("/deleteWishlistGame")
    public ResponseEntity<String> deleteGameToWishlist(@RequestParam String username,String name) {
        Boolean result=userNeo4jService.deleteGameToWishlist(username,name);
        if (result) {
            return ResponseEntity.ok("eliminated game");
        }else if(!result){
            return ResponseEntity.ok("no eliminated game");
        }

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }

    //tengo locale
    @GetMapping("/followedUser")
    public ResponseEntity<Object> getFollowedUser(@RequestParam String username) {
        List<UserNeo4j> usersList = userNeo4jService.getFollowedUser(username);
        if (usersList!=null && !usersList.isEmpty()) {
            return ResponseEntity.ok(usersList);
        }else if(usersList!=null && usersList.isEmpty()){
            return ResponseEntity.ok("friendsList empty");
        }

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }

    //tengo locale
    @GetMapping("/SuggestFriends")
    public ResponseEntity<Object> getSuggestFriends(@RequestParam String username){
        List<UserNeo4j> userNeo4jList=userNeo4jService.getSuggestedFriends(username);
        if (userNeo4jList!=null && !userNeo4jList.isEmpty()) {
            return ResponseEntity.ok(userNeo4jList);
        }else if (userNeo4jList!=null && userNeo4jList.isEmpty()){
            return ResponseEntity.ok("suggestFriendsList empty");
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }


    //DA MODIFICARE NEL MAIN->AGGIUNGE LIKE AD UNA REVIEW(DA MODIFICARE SIA IN SERVICE CHE REPOSITORY)
    //tengo locale
    @PostMapping("/addLikeReview")
    public ResponseEntity<String> addLikeToReview(@RequestParam String username,String id) {
        Boolean likeAdded=userNeo4jService.addLikeToReview(username,id);
        if (id!=null && likeAdded) {
            return ResponseEntity.ok("added like");
        } else if (id!=null && !likeAdded) {
            return ResponseEntity.ok("no added like");
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
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
