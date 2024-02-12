package it.unipi.lsmsd.gamehub.controller;



import it.unipi.lsmsd.gamehub.DTO.ReviewDTO;
import it.unipi.lsmsd.gamehub.model.GameNeo4j;
import it.unipi.lsmsd.gamehub.model.Review;
import it.unipi.lsmsd.gamehub.model.UserNeo4j;
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



    //cambiato path
    @GetMapping("userSelected/wishlist")
    public ResponseEntity<Object> getUserWishlist(@RequestParam String username, String friendUsername) {
        List<GameNeo4j> gameList = userNeo4jService.getUserWishlist(username,friendUsername);
        if (gameList != null && !gameList.isEmpty()) {
            return ResponseEntity.ok(gameList);
        } else if (gameList != null && gameList.isEmpty()) {
            return ResponseEntity.ok("empty gameList");
        }

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }

    //cambiato path
    @PostMapping("userSelected/wishlist/addWishlistGame")
    public ResponseEntity<String> addGameToWishlist(@RequestParam String username,String name) {
        Boolean result=userNeo4jService.addGameToWishlist(username,name);
        if (result) {
            return ResponseEntity.ok("game added");
        }else if(!result){
            return ResponseEntity.ok("no game added");
        }

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }

    //cambiato path
    @PostMapping("userSelected/wishlist/deleteWishlistGame")
    public ResponseEntity<String> deleteGameToWishlist(@RequestParam String username,String name) {
        Boolean result=userNeo4jService.deleteGameToWishlist(username,name);
        if (result) {
            return ResponseEntity.ok("eliminated game");
        }else if(!result){
            return ResponseEntity.ok("no eliminated game");
        }

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }

    //cambiato path
    @GetMapping("userSelected/followedUser")
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



    @PostMapping("/reviewSelected/addLikeReview")
    public ResponseEntity<String> addLikeToReview(@RequestParam String username,String id) {
        Boolean likeAdded=userNeo4jService.addLikeToReview(username,id);
        if (id!=null && likeAdded) {
            return ResponseEntity.ok("added like");
        } else if (id!=null && !likeAdded) {
            return ResponseEntity.ok("no added like");
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }


    @GetMapping("/countUser/{userId}")
    public ResponseEntity<Object> countGame(@PathVariable String userId){
        ResponseEntity<String> responseEntity= iLoginService.roleUser(userId);
        if(responseEntity.getStatusCode() != HttpStatus.OK) {
            return ResponseEntity.status(responseEntity.getStatusCode()).body(responseEntity.getBody());
        }

        long count= userNeo4jService.countUserDocument();
        return ResponseEntity.ok(count);
    }


    //cambiato path
    @PostMapping("userSelected/follow")
    public ResponseEntity<String> followUser(@RequestParam String followerUsername, @RequestParam String followedUsername) {
        userNeo4jService.followUser(followerUsername, followedUsername);
        return ResponseEntity.ok("Followed successfully");
    }

    //cambiato path
    @PostMapping("userSelected/unfollow")
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

