package it.unipi.lsmsd.gamehub.service.impl;


import it.unipi.lsmsd.gamehub.DTO.GameDTO;
import it.unipi.lsmsd.gamehub.DTO.ReviewDTO;
import it.unipi.lsmsd.gamehub.model.*;


import it.unipi.lsmsd.gamehub.repository.*;
import it.unipi.lsmsd.gamehub.service.IGameService;
import it.unipi.lsmsd.gamehub.service.IUserNeo4jService;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

import static java.util.Arrays.stream;

@Service
public class UserNeo4jService implements IUserNeo4jService {
    @Autowired
    private UserNeo4jRepository userNeo4jRepository;
    @Autowired
    private LoginRepository loginRepository;
    @Autowired
    private GameRepository gameRepository;
    @Autowired
    private GameNeo4jRepository gameNeo4jRepository;

    @Autowired
    private ReviewRepository reviewRepository;

    @Autowired
    private IGameService gameService;

    @Override
    public void SyncUser() {
        List<User> usersMongo = loginRepository.findAll();
        ModelMapper modelMapper = new ModelMapper();
        List<UserNeo4j> userNeo4js = usersMongo.stream().map(User -> modelMapper.map(User, UserNeo4j.class)).toList();
        userNeo4jRepository.saveAll(userNeo4js);
    }

    public void loadGames() {
        List<Game> games = gameRepository.findAll();
        ModelMapper modelMapper = new ModelMapper();
        List<GameNeo4j> graphGames = games.stream().map(Game -> modelMapper.map(Game, GameNeo4j.class)).toList();
        gameNeo4jRepository.saveAll(graphGames);
    }



    @Override
    public List<GameNeo4j> getUserWishlist(String username,String friendUsername) {
        try {
            if (username.equals(friendUsername)){
                return userNeo4jRepository.findByUsername(username);

            }
            List<UserNeo4j> userNeo4jList=userNeo4jRepository.findFollowedUsers(username);
            for (UserNeo4j userNeo4j : userNeo4jList) {
                if (userNeo4j.getUsername().equals(friendUsername)) {
                    // User with the desired username is present in the list
                    return userNeo4jRepository.findByUsername(friendUsername);
                }
            }

            return userNeo4jRepository.findByUsername(friendUsername);
        }catch (Exception e){
            System.out.println(e.getMessage());
            return null;
        }
    }


    @Override
    public Boolean addGameToWishlist(String username, String name) {
        try {
            //check if the game exists
            GameNeo4j gameNeo4j=gameNeo4jRepository.findGameByName(name);
            if(gameNeo4j!=null){
                userNeo4jRepository.addGameToUser(username, name);
                return true;
            }

            return false;

        }catch (Exception e){
            System.out.println(e.getMessage());
            return null;
        }
    }

    @Override
    public Boolean deleteGameToWishlist(String username, String name) {
        try {
            //check if the game is present in the list of the added game
            List<GameNeo4j> gameNeo4jList= userNeo4jRepository.findByUsername(username);
            for (GameNeo4j game : gameNeo4jList) {
                if (game.getName().equals(name)) {
                    // The game with the provided name is present in the list
                    userNeo4jRepository.deleteGameFromUser(username, name);
                    return true;
                }
            }

            return false;
        }catch (Exception e){
            System.out.println(e.getMessage());
            return null;
        }
    }


    @Override
    public List<UserNeo4j> getFollowedUser(String username) {
        try {
            return userNeo4jRepository.findFollowedUsers(username);
        }catch (Exception e){
            System.out.println(e.getMessage());
            return null;
        }
    }

    @Override
    public List<UserNeo4j> getFriendsOfFriends(String username) {
        try {
            return userNeo4jRepository.findFriendsOfFriends(username);
        }catch (Exception e){
            System.out.println(e.getMessage());
            return null;
        }
    }

    private UserNeo4j convertUser(User user) {
        UserNeo4j userNeo4j = new UserNeo4j();
        userNeo4j.setId(user.getId());
        userNeo4j.setUsername(user.getUsername());
        return userNeo4j;
    }


    @Override
    public List<UserNeo4j> getSuggestedFriends(String username) {
        try {

            //get the user followed by the username
            List<UserNeo4j> followedUser = userNeo4jRepository.findFollowedUsers(username);
            //get the user wishlist by the username
            List<GameNeo4j> addedGames = userNeo4jRepository.findByUsername(username);
            //get the list of friends of the followed users
            List<UserNeo4j> friendsOfFriends = userNeo4jRepository.findFriendsOfFriends(username);
            //if the userFriendList is empty return an empy list
            if(friendsOfFriends.isEmpty()){
                return friendsOfFriends;
            }

            // Generate a list of friends of friends not in the followedUser list
            List<UserNeo4j> selectFriendsOfFriends = friendsOfFriends.stream()
                    .filter(friend -> followedUser.stream().noneMatch(f -> f.getUsername().equals(friend.getUsername())))
                    //.limit(2)
                    .collect(Collectors.toList());


            //limit the number of friendOfFriends to compare the wishlist in order to reduce the time to compare the wishlists
            Random random = new Random();
            Set<Integer> randomPositions = new HashSet<>();

            // Generate 100 random different integers
            while (randomPositions.size() < 50) {
                randomPositions.add(random.nextInt(selectFriendsOfFriends.size()));
            }

            // Create a new list with elements at the random positions
            //The map operation takes each position and retrieves the corresponding element from the selectFriendsOfFriends
            // list using selectFriendsOfFriends::get. Finally, collect(Collectors.toList()) collects these elements into a new list.
            List<UserNeo4j> resultFriendsOfFriends = randomPositions.stream()
                    .map(selectFriendsOfFriends::get)
                    .collect(Collectors.toList());

            /*for(int i=0;i<resultFriendsOfFriends.size();i++){
                System.out.println(resultFriendsOfFriends.get(i).getUsername());
            }*/


            //final result
            List<UserNeo4j> suggestedFriends = new ArrayList<>();




            for (int i = 0; i < resultFriendsOfFriends.size(); i++) {
                System.out.println(resultFriendsOfFriends.get(i).getUsername());
                List<GameNeo4j> friendOfFriendWishlist = userNeo4jRepository.findByUsername(resultFriendsOfFriends.get(i).getUsername());
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
                return suggestedFriends;
            }
            //System.out.println("suggestFriendsList empty");
            return suggestedFriends;
        }catch (Exception e){
            System.out.println(e.getMessage());
            return null;
        }
    }




    @Override
    public Boolean addLikeToReview(String username, String id) {
        try {
            Boolean likePresent=userNeo4jRepository.addLikeToReview(username,id);
            //System.out.println(likePresent);
            if(likePresent != null && !likePresent.booleanValue()){
                //se il like non è presente si aggiunge anche su mongoDB
                Optional<Review> optionalReview=reviewRepository.findById(id);
                if(optionalReview.isPresent()){
                    Review review = optionalReview.get();
                    int modifiedLikeCount=review.getLikeCount();
                    modifiedLikeCount+=1;
                    review.setLikeCount(modifiedLikeCount);
                    reviewRepository.save(review);

                    //check if in the embedded review list of the game the likeCount of this review
                    //is greater of the likeCount of the embedded review with minor likeCount, if yes
                    //we check if the review is not inside the embedded list,if yes we update the review from scratch
                    // otherwise if the review is inside the embedded review
                    //list we update the embedded review list with another function that operate only with the embedded
                    //reviews wich we have already retrieved in one read when we retrieve the game

                    //now do it step by step
                    //1)retrieve the game to retrieve the list of embedded review
                    List<Game> game=gameRepository.findByName(review.getTitle());
                    List<Review> embeddedReviews=game.get(0).getReviews();

                    //2)retrieve the embedded review with least likeCount between the embedded reviews
                    Review reviewWithLeastLikes = null;

                    // Iterate through the embedded reviews
                    for (Review compareReview : embeddedReviews) {
                        // Check if reviewWithLeastLikes is null or if the current review has fewer likes
                        if (reviewWithLeastLikes == null || compareReview.getLikeCount() < reviewWithLeastLikes.getLikeCount()) {
                            // Update reviewWithLeastLikes to the current review
                            reviewWithLeastLikes = compareReview;
                        }
                    }

                    //3)check if the review.likeCount i considered is > reviewWithLeastLikes.likecCount()
                    if(review.getLikeCount()>reviewWithLeastLikes.getLikeCount()){
                        boolean fuondEqualReview=false;
                        //4) now check if the review is already inside the embeddedReview
                        for(Review compareReview : embeddedReviews){
                            if(review.getId().equals(compareReview.getId())){
                                //if the review is already embedded we modify the also the likeCount in the embedded review
                                int embeddedLikeCount=compareReview.getLikeCount();
                                compareReview.setLikeCount(embeddedLikeCount+1);
                                gameRepository.save(game.get(0));
                                fuondEqualReview=true;
                            }
                        }

                        if(fuondEqualReview){
                            //5) if yes, run the function that act only inside the embedded review(DEFINE THIS FUNCTION)
                            gameService.updateGameEmbeddedReview(game.get(0));
                            System.out.println("update embedded reviews");

                        }else if(!fuondEqualReview){
                            //6) if not we update from scratch considering all the reviews of that game

                            gameService.updateGameReviewFromScratch(game.get(0),20);
                            System.out.println("update reviews from scratch");
                        }
                    }





                    //return true if the review it is created
                    return true;
                }else{
                    //if there are some problems we delete the link
                    userNeo4jRepository.deleteLikeFromReview(username,id);
                    return false;
                }


            }
            return false;
        }catch (Exception e){
            System.out.println(e.getMessage());
            return null;

        }


    }



    @Override
    public long countUserDocument(){
        try {
            return loginRepository.count();
        }catch (Exception e){
            System.out.println(e.getMessage());
            return -1;
        }
    }

    @Override
    public void followUser(String followerUsername, String followedUsername) {
        userNeo4jRepository.followUser(followerUsername, followedUsername);
    }
    @Override
    public void unfollowUser(String followerUsername, String followedUsername) {
        userNeo4jRepository.unfollowUser(followerUsername, followedUsername);
    }

    public void removeUser(String username) {
        userNeo4jRepository.removeUser(username);
    }
    public ResponseEntity<String> addUser(String id, String username){
        try {
            userNeo4jRepository.addUser(id, username);
            return new ResponseEntity<>("successfully registered user", HttpStatus.CREATED);
        }
        catch (Exception e) {
            return new ResponseEntity<>("Error in interaction with Neo4j" + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }

    }
    public UserNeo4j getUser(String username){

        try{
            UserNeo4j userNeo4j= userNeo4jRepository.getUser(username);
            if(userNeo4j!=null){
                return userNeo4j;
            }else {
                UserNeo4j userNeo4j1=new UserNeo4j();
                userNeo4j1.setId("null");
                return userNeo4j1;
            }
        }catch (Exception e){
            System.out.println(e.getMessage());
            return null;
        }
    }
    public ResponseEntity<String> updateUser(String username, String newUsername){
        try {
            userNeo4jRepository.updateUser(username, newUsername);
            return new ResponseEntity<>("username correctly updated", HttpStatus.OK);
        }
        catch (Exception e) {
            return new ResponseEntity<>("error in updating username in neo4j: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

}
