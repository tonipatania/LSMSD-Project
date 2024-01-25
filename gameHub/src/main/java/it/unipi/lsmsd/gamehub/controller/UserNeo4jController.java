package it.unipi.lsmsd.gamehub.controller;


import it.unipi.lsmsd.gamehub.model.GameNeo4j;
import it.unipi.lsmsd.gamehub.model.UserNeo4j;
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

    @GetMapping("/wishlist")
    public ResponseEntity<List<GameNeo4j>> getUSerWishlist(@RequestParam String username) {
        List<GameNeo4j> gameList = userNeo4jService.getUserWishlist(username);
        if (!gameList.isEmpty()) {
            return ResponseEntity.ok(gameList);
        }
        System.out.println("gamelist empty");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }

    @PostMapping("/addWishlistGame")
    public ResponseEntity<String> addGameToWishlist(@RequestParam String username, String name) {
        userNeo4jService.addGameToWishlist(username, name);
        if (name != null) {
            return ResponseEntity.ok("gioco aggiunto");
        }
        System.out.println("nessun gioco aggiunto");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }

    @PostMapping("/deleteWishlistGame")
    public ResponseEntity<String> deleteGameToWishlist(@RequestParam String username, String name) {
        userNeo4jService.deleteGameToWishlist(username, name);
        if (name != null) {
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
    public ResponseEntity<List<UserNeo4j>> getFriendsOfFriends(@RequestParam String username) {
        List<UserNeo4j> userNeo4jList = userNeo4jService.getFriendsOfFriends(username);
        if (!userNeo4jList.isEmpty()) {
            return ResponseEntity.ok(userNeo4jList);
        }
        System.out.println("no friends of friends");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }

    //Query per amici suggeriti
    @GetMapping("/SuggestFriends")
    public ResponseEntity<List<UserNeo4j>> getSuggestFriends(@RequestParam String username) {
        List<UserNeo4j> userNeo4jList = userNeo4jService.getSuggestedFriends(username);
        if (!userNeo4jList.isEmpty()) {
            return ResponseEntity.ok(userNeo4jList);
        }
        System.out.println("no suggested friends empty");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }

    //DA CAPIRE SE MODIFICARE ANCHE IL CAMPO likeCount in mongo
    @PostMapping("/addLikeReview")
    public ResponseEntity<String> addLikeToReview(@RequestParam String username, String id) {
        userNeo4jService.addGameToWishlist(username, id);
        if (id != null) {
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

    // Endpoint to like a game
    @PostMapping("/like")
    public ResponseEntity<String> likeGame(@RequestParam String username, @RequestParam String name) {
        userNeo4jService.likeGame(username, name);
        return ResponseEntity.ok("Added successfully");
    }

    // Endpoint to dislike a game
    @DeleteMapping("/dislike")
    public ResponseEntity<String> dislikeGame(@RequestParam String username, @RequestParam String name) {
        userNeo4jService.dislikeGame(username, name);
        return ResponseEntity.ok("Removed successfully");
    }

    //remove user
    @DeleteMapping("/removeUser")
    public ResponseEntity<String> removeUser(@RequestParam String username) {
        userNeo4jService.removeUser(username);
        return ResponseEntity.ok("User Removed");
    }

    //add new user
    @PostMapping("/addUser")
    public ResponseEntity<String> addUser(@RequestParam String id, @RequestParam String username) {
        userNeo4jService.addUser(id, username);
        return ResponseEntity.ok("User Added");
    }

    //get id and username
    @GetMapping("/getUser")
    public ResponseEntity<UserNeo4j> getUser(@RequestParam String username) {
        UserNeo4j user = userNeo4jService.getUser(username);

        if (user != null) {
            return ResponseEntity.ok(user);
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    //update username on the basis of old username
    @PutMapping("/updateUser")
    public ResponseEntity<String> updateUser(@RequestParam String username, @RequestParam String newUsername) {
        boolean updated = userNeo4jService.updateUser(username, newUsername);

        if (updated) {
            return ResponseEntity.ok("User Updated");
        } else {
            return ResponseEntity.notFound().build();
        }


        //Query per amici suggeriti
    /*@GetMapping("/SuggestFriends")
    public ResponseEntity<List<UserNeo4j>> getSuggestUsers(@RequestParam String username){

        //get the user followed by the username
        List<UserNeo4j> followedUser=userNeo4jService.getFollowedUser(username);
        //get the user wishlist by the username
        List<GameNeo4j> addedGames=userNeo4jService.getUserWishlist(username);
        //get the list of friends of the followed users
        List<UserNeo4j> friendsOfFriends=userNeo4jService.getFriendsOfFriends(username);

        // Generate a list of friends of friends not in the followedUser list
        List<UserNeo4j> resultFriendsOfFriends = friendsOfFriends.stream()
                .filter(friend -> followedUser.stream().noneMatch(f -> f.getUsername().equals(friend.getUsername())))
                //.limit(2)
                .collect(Collectors.toList());

        //final result
        List<UserNeo4j> suggestedFriends=new ArrayList<>();


        for(int i=0;i<resultFriendsOfFriends.size();i++){
            List<GameNeo4j> friendOfFriendWishlist=userNeo4jService.getUserWishlist(resultFriendsOfFriends.get(i).getUsername());
            Set<String> friendOfFriendGameNames = friendOfFriendWishlist.stream()
                    .map(GameNeo4j::getName)
                    .collect(Collectors.toSet());

            // Compare the two sets and count the common games
            long commonGamesCount = addedGames.stream()
                    .map(GameNeo4j::getName)
                    .filter(friendOfFriendGameNames::contains)
                    .distinct()
                    .count();

            // If there are 5 or more common games, add the user to a userlist
            if (commonGamesCount >= 5) {
                suggestedFriends.add(resultFriendsOfFriends.get(i));
            }
        }

        if (!suggestedFriends.isEmpty()) {
            return ResponseEntity.ok(suggestedFriends);
        }
        System.out.println("gamelist empty");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }*/
    }
}
