package it.unipi.lsmsd.gamehub.service.impl;

import it.unipi.lsmsd.gamehub.model.Game;
import it.unipi.lsmsd.gamehub.model.GameNeo4j;
import it.unipi.lsmsd.gamehub.model.User;

import it.unipi.lsmsd.gamehub.model.UserNeo4j;
import it.unipi.lsmsd.gamehub.repository.GameNeo4jRepository;
import it.unipi.lsmsd.gamehub.repository.GameRepository;
import it.unipi.lsmsd.gamehub.repository.LoginRepository;
import it.unipi.lsmsd.gamehub.repository.UserNeo4jRepository;
import it.unipi.lsmsd.gamehub.service.IUserNeo4jService;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
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
    public List<GameNeo4j> getUserWishlist(String username) {
        try {
            return userNeo4jRepository.findByUsername(username);
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
            List<UserNeo4j> resultFriendsOfFriends = friendsOfFriends.stream()
                    .filter(friend -> followedUser.stream().noneMatch(f -> f.getUsername().equals(friend.getUsername())))
                    //.limit(2)
                    .collect(Collectors.toList());

            //final result
            List<UserNeo4j> suggestedFriends = new ArrayList<>();


            for (int i = 0; i < resultFriendsOfFriends.size(); i++) {
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
            System.out.println("gamelist empty");
            return suggestedFriends;
        }catch (Exception e){
            System.out.println(e.getMessage());
            return null;
        }
    }

    @Override
    public void addLikeToReview(String username, String id) {
        try {
            userNeo4jRepository.addLikeToReview(username,id);
        }catch (Exception e){
            System.out.println(e.getMessage());
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
    public void likeGame(String username, String name) {
        // You can add additional business logic here if needed
        userNeo4jRepository.likeGame(username, name);
    }
    public void dislikeGame(String username, String name) {
        // You can add additional business logic here if needed
        userNeo4jRepository.dislikeGame(username, name);
    }
    public void removeUser(String username) {
        userNeo4jRepository.removeUser(username);
    }
    public void addUser(String id, String username){
        userNeo4jRepository.addUser(id, username);
    }
    public UserNeo4j getUser(String username){
        return userNeo4jRepository.getUser(username);
    }
    public boolean updateUser(String username, String newUsername){
        userNeo4jRepository.updateUser(username, newUsername);
        return false;
    }

}
