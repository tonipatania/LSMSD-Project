package it.unipi.lsmsd.gamehub.controller;


import it.unipi.lsmsd.gamehub.model.GameNeo4j;
import it.unipi.lsmsd.gamehub.model.UserNeo4j;
import it.unipi.lsmsd.gamehub.service.impl.GameNeo4jService;
import it.unipi.lsmsd.gamehub.service.impl.UserNeo4jService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
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

    @PostMapping("/sync")
    public ResponseEntity<String> syncUser() {
        userNeo4jService.SyncUser();
        return ResponseEntity.ok("Sincronizzazione completata");
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
        System.out.println("gamelist empty");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }

    //Query per amici suggeriti
    @GetMapping("/SuggestFriends")
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
    }

    @PostMapping("/loadgames")
    public ResponseEntity<String> reqGames() {
        userNeo4jService.loadGames();
        return ResponseEntity.ok("Giochi caricati");
    }

}
