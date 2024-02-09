package it.unipi.lsmsd.gamehub.controller;


import it.unipi.lsmsd.gamehub.model.UserNeo4j;
import it.unipi.lsmsd.gamehub.service.IInteractionGameService;
import it.unipi.lsmsd.gamehub.service.ILoginService;
import it.unipi.lsmsd.gamehub.service.IUserNeo4jService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RequestMapping("user")
@RestController
public class UserController {
    @Autowired
    private IUserNeo4jService userNeo4jService;
    @Autowired
    private ILoginService iLoginService;

    // to load user from mongo to neo4j
    @PostMapping("/sync")
    public ResponseEntity<String> syncUser() {
        userNeo4jService.SyncUser();
        return ResponseEntity.ok("Sincronizzazione completata");
    }

    // to load games from mongo to neo4j
    @PostMapping("/loadgames")
    public ResponseEntity<String> reqGames() {
        userNeo4jService.loadGames();
        return ResponseEntity.ok("Giochi caricati");
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

//    @GetMapping("/FriendsOfFriends")
//    public ResponseEntity<List<UserNeo4j>> getFriendsOfFriends(@RequestParam String username) {
//        List<UserNeo4j> userNeo4jList = userNeo4jService.getFriendsOfFriends(username);
//        if (!userNeo4jList.isEmpty()) {
//            return ResponseEntity.ok(userNeo4jList);
//        }
//        System.out.println("no friends of friends");
//        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
//    }

    //Query per amici suggeriti
    @GetMapping("/suggestFriends")
    public ResponseEntity<List<UserNeo4j>> getSuggestFriends(@RequestParam String username) {
        List<UserNeo4j> userNeo4jList = userNeo4jService.getSuggestedFriends(username);
        if (!userNeo4jList.isEmpty()) {
            return ResponseEntity.ok(userNeo4jList);
        }
        System.out.println("no suggested friends empty");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }

    //DA CAPIRE SE MODIFICARE ANCHE IL CAMPO likeCount in mongo
    @PostMapping("/reviewSelected/addLikeReview")
    public ResponseEntity<String> addLikeToReview(@RequestParam String username, String idGame) {
        userNeo4jService.addLikeToReview(username, idGame);
        if (idGame != null) {
            return ResponseEntity.ok("like aggiunto");
        }
        System.out.println("nessun like aggiunto");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }

    @PostMapping("/follow")
    public ResponseEntity<String> followUser(@RequestParam String followerUsername, @RequestParam String followedUsername) {
        userNeo4jService.followUser(followerUsername, followedUsername);
        return ResponseEntity.ok("Followed successfully");
    }

    @PostMapping("/unfollow")
    public ResponseEntity<String> unfollowUser(@RequestParam String followerUsername, @RequestParam String followedUsername) {
        userNeo4jService.unfollowUser(followerUsername, followedUsername);
        return ResponseEntity.ok("Unfollowed successfully");
    }
    //update username on the basis of old username
    @PatchMapping("/updateUser")
    public ResponseEntity<String> updateUser(@RequestParam String username, @RequestParam String newUsername) {
        // aggiorno utente su mongo
        ResponseEntity<String> responseEntity = iLoginService.updateUser(username, newUsername);
        if(responseEntity.getStatusCode() != HttpStatus.OK) {
            return responseEntity;
        }
        // aggiorno su neo4j
       ResponseEntity<String> response = userNeo4jService.updateUser(username, newUsername);
       if(response.getStatusCode() == HttpStatus.OK) {
           return response;
       }
       // se fallisce riporto l username allo stato iniziale
       responseEntity = iLoginService.updateUser(newUsername, username);
       return ResponseEntity.status(responseEntity.getStatusCode()).body("username update failed, please try again later");
    }
}

