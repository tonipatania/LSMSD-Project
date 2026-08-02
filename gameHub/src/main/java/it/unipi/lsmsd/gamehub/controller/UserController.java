package it.unipi.lsmsd.gamehub.controller;



import it.unipi.lsmsd.gamehub.DTO.SuggestedUserDTO;
import it.unipi.lsmsd.gamehub.model.Game;
import it.unipi.lsmsd.gamehub.model.GameNeo4j;
import it.unipi.lsmsd.gamehub.model.UserNeo4j;
import it.unipi.lsmsd.gamehub.service.ILoginService;
import it.unipi.lsmsd.gamehub.service.IUserNeo4jService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;


@RequestMapping("user")
@RestController
@Slf4j
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





    @GetMapping("userSelected/wishlist")
    public ResponseEntity<Object> getUserWishlist(@RequestParam String username,
                                                  @RequestParam(required = false) String friendUsername) {
        List<Game> gameList = userNeo4jService.getUserWishlist(username,friendUsername);
        if (gameList != null) {
            // always a JSON array, even when empty: a plain-text "empty" message here is not
            // valid JSON, and Angular's HttpClient turns an unparseable 200 body into an error
            return ResponseEntity.ok(gameList);
        }

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }


    // variante paginata usata dal profilo di un altro utente, per non caricare in un colpo solo
    // wishlist che possono contenere decine di giochi
    @GetMapping("userSelected/wishlist/page")
    public ResponseEntity<Page<Game>> getUserWishlistPage(@RequestParam String username,
                                                          @RequestParam(required = false) String friendUsername,
                                                          @RequestParam(defaultValue = "name") String sort,
                                                          @RequestParam(defaultValue = "false") boolean onlyCommon,
                                                          @PageableDefault(size = 12) Pageable pageable) {
        return ResponseEntity.ok(userNeo4jService.getUserWishlistPage(
                username, friendUsername, pageable, sort, onlyCommon));
    }

    // giochi che il visitatore ha in comune con il profilo che sta guardando
    @GetMapping("userSelected/wishlist/common")
    public ResponseEntity<List<GameNeo4j>> getCommonWishlistGames(@RequestParam String username,
                                                                   @RequestParam String friendUsername) {
        return ResponseEntity.ok(userNeo4jService.getCommonWishlistGames(username, friendUsername));
    }


    //cambiato path
    @PostMapping("wishlist/addWishlistGame")
    public ResponseEntity<String> addGameToWishlist(@RequestParam String username,String name) {
        Boolean result=userNeo4jService.addGameToWishlist(username,name);
        // il service torna null quando la query fallisce: il null va intercettato prima di ogni
        // uso come boolean, altrimenti l'unboxing solleva NullPointerException e il ramo 500
        // qui sotto resta irraggiungibile
        if (result == null) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
        if (result) {
            return ResponseEntity.ok("game added");
        }
        return ResponseEntity.ok("no game added");
    }


    //cambiato path
    @PostMapping("wishlist/deleteWishlistGame")
    public ResponseEntity<String> deleteGameToWishlist(@RequestParam String username,String name) {
        Boolean result=userNeo4jService.deleteGameToWishlist(username,name);
        if (result == null) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
        if (result) {
            return ResponseEntity.ok("eliminated game");
        }
        return ResponseEntity.ok("no eliminated game");
    }



    @GetMapping("/followedUser")
    public ResponseEntity<Object> getFollowedUser(@RequestParam String username) {
        List<UserNeo4j> usersList = userNeo4jService.getFollowedUser(username);
        if (usersList != null) {
            return ResponseEntity.ok(usersList);
        }

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }

    // paginated variant used by the Community page, to avoid loading the full followed-users list at once
    @GetMapping("/followedUser/page")
    public ResponseEntity<Page<UserNeo4j>> getFollowedUserPage(@RequestParam String username,
                                                                @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(userNeo4jService.getFollowedUserPage(username, pageable));
    }


    @GetMapping("/search")
    public ResponseEntity<Object> searchUsers(@RequestParam String query, @RequestParam String username) {
        List<UserNeo4j> usersList = userNeo4jService.searchUsers(query, username);
        if (usersList != null) {
            return ResponseEntity.ok(usersList);
        }

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }

    @GetMapping("/SuggestFriends")
    public ResponseEntity<Object> getSuggestFriends(@RequestParam String username){
        List<SuggestedUserDTO> userNeo4jList=userNeo4jService.getSuggestedFriends(username);
        if (userNeo4jList != null) {
            return ResponseEntity.ok(userNeo4jList);
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

    @PostMapping("/reviewSelected/removeLikeReview")
    public ResponseEntity<String> removeLikeFromReview(@RequestParam String username, String id) {
        Boolean likeRemoved = userNeo4jService.removeLikeFromReview(username, id);
        if (id != null && likeRemoved != null && likeRemoved) {
            return ResponseEntity.ok("removed like");
        } else if (id != null && likeRemoved != null) {
            return ResponseEntity.ok("no removed like");
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }

    @GetMapping("/reviewSelected/likedReviews")
    public ResponseEntity<Object> getLikedReviews(@RequestParam String username) {
        List<String> reviewIds = userNeo4jService.getLikedReviewIds(username);
        if (reviewIds != null) {
            return ResponseEntity.ok(reviewIds);
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }

    // funzione admin
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
        Boolean result=userNeo4jService.followUser(followerUsername, followedUsername);
        if(result){
            return ResponseEntity.ok("Followed successfully");
        }else if(!result){
            return ResponseEntity.ok("Followed not successfully");
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }

    //cambiato path
    @PostMapping("userSelected/unfollow")
    public ResponseEntity<String> unfollowUser(@RequestParam String followerUsername, @RequestParam String followedUsername) {
        Boolean result=userNeo4jService.unfollowUser(followerUsername, followedUsername);
        if(result){
            return ResponseEntity.ok("Unfollowed successfully");
        }else if(!result){
            return ResponseEntity.ok("Unfollowed not successfully");
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();

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
       log.error("Aggiornamento username fallito in Neo4j per {} -> {}, rollback su Mongo", username, newUsername);
       responseEntity = iLoginService.updateUser(newUsername, username);
       return ResponseEntity.status(responseEntity.getStatusCode()).body("username update failed, please try again later");
    }

    @GetMapping("/getUser")
    public ResponseEntity<Object> getUser(@RequestParam String username) {
        UserNeo4j userNeo4j = userNeo4jService.getUser(username);
        if (userNeo4j!=null && !userNeo4j.getId().equals("null")) {
            return ResponseEntity.ok(userNeo4j);
        }else if(userNeo4j.getId().equals("null")){
            // empty body rather than a plain-text message: Spring's String converter writes
            // unquoted raw text even with an application/json content type, which Angular's
            // HttpClient can't parse and turns into an error instead of a normal 200 response.
            // An empty body is unambiguous and maps cleanly to null on the client.
            return ResponseEntity.ok().build();
        }

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }

}

