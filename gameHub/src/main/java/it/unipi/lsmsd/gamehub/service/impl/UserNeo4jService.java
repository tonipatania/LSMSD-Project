package it.unipi.lsmsd.gamehub.service.impl;

import it.unipi.lsmsd.gamehub.DTO.GameDTO;
import it.unipi.lsmsd.gamehub.model.*;

import it.unipi.lsmsd.gamehub.repository.*;
import it.unipi.lsmsd.gamehub.service.IUserNeo4jService;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
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

    //aggiunta per modigicare il campo likeCount
    @Autowired
    private ReviewRepository reviewRepository;

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


    //DA MODIFICARE NEL MAIN->TROVA LA LISTA DI GIOCHI DEGLI AMICI
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
    public void addGameToWishlist(String username, String name) {
        try {
            userNeo4jRepository.addGameToUser(username, name);
        }catch (Exception e){
            System.out.println(e.getMessage());
        }
    }

    @Override
    public void deleteGameToWishlist(String username, String name) {
        try {
            userNeo4jRepository.deleteGameFromUser(username, name);
        }catch (Exception e){
            System.out.println(e.getMessage());
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

    //QUERY DA AGGIORNARE NEL MAIN(SOLO QUA NEL SERVICE)
    @Override
    public List<UserNeo4j> getSuggestedFriends(String username) {
        try {

            //get the user followed by the username
            List<UserNeo4j> followedUser = userNeo4jRepository.findFollowedUsers(username);
            //get the user wishlist by the username
            List<GameNeo4j> addedGames = userNeo4jRepository.findByUsername(username);
            //get the list of friends of the followed users
            List<UserNeo4j> friendsOfFriends = userNeo4jRepository.findFriendsOfFriends(username);

            // Generate a list of friends of friends not in the followedUser list
            List<UserNeo4j> selectFriendsOfFriends = friendsOfFriends.stream()
                    .filter(friend -> followedUser.stream().noneMatch(f -> f.getUsername().equals(friend.getUsername())))
                    //.limit(2)
                    .collect(Collectors.toList());

            //limit the number of friendOfFriends to compare the wishlist in order to reduce the time to compare the wishlists
            /*Random random = new Random();
            int randomPosition1 = random.nextInt(selectFriendsOfFriends.size());
            int randomPosition2 = random.nextInt(selectFriendsOfFriends.size());

           // Create a new list with elements at the random positions
            List<UserNeo4j> resultFriendsOfFriends = new ArrayList<>();
            resultFriendsOfFriends.add(selectFriendsOfFriends.get(randomPosition1));
            resultFriendsOfFriends.add(selectFriendsOfFriends.get(randomPosition2));*/

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

            for(int i=0;i<resultFriendsOfFriends.size();i++){
                System.out.println(resultFriendsOfFriends.get(i).getUsername());
            }


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
            System.out.println("suggestFriendsList empty");
            return suggestedFriends;
        }catch (Exception e){
            System.out.println(e.getMessage());
            return null;
        }
    }

    /*@Override
    public void addLikeToReview(String username, String id) {
        try {
            userNeo4jRepository.addLikeToReview(username,id);
        }catch (Exception e){
            System.out.println(e.getMessage());
        }
    }*/

    //DA MODIFICARE NEL MAIN->AGGIUNGE LIKE AD UNA REVIEW
    @Override
    public boolean addLikeToReview(String username, String id) {
        try {
            Boolean likePresent=userNeo4jRepository.addLikeToReview(username,id);
            System.out.println(likePresent);
            if(likePresent != null && !likePresent.booleanValue()){
                //se il like non è presente si aggiunge anche su mongoDB
                Optional<Review> optionalReview=reviewRepository.findById(id);
                if(optionalReview.isPresent()){
                    Review review = optionalReview.get();
                    int modifiedLikeCount=review.getLikeCount();
                    modifiedLikeCount+=1;
                    review.setLikeCount(modifiedLikeCount);
                    reviewRepository.save(review);
                    //return true if it is created
                    return true;
                }else{
                    System.out.println("null");
                }

            }
        }catch (Exception e){
            System.out.println(e.getMessage());

        }
        return false;

    }

    //AGGIUNGERE NEL MAIN-> FUNZIONE CHE CONTA IL NUMERO TOTALE DI UTENTI
    @Override
    public long countUserDocument(){
        try {
            return loginRepository.count();
        }catch (Exception e){
            System.out.println(e.getMessage());
            return -1;
        }
    }

}
