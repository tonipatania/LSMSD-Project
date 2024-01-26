package it.unipi.lsmsd.gamehub.service;

import it.unipi.lsmsd.gamehub.model.GameNeo4j;
import it.unipi.lsmsd.gamehub.model.UserNeo4j;
import java.util.List;

public interface IUserNeo4jService {
    public void SyncUser();
    public void loadGames();

    public List<GameNeo4j> getUserWishlist(String username);

    public void addGameToWishlist(String username,String name);

    public void deleteGameToWishlist(String username,String name);

    List<UserNeo4j> getFollowedUser(String username);

    List<UserNeo4j> getFriendsOfFriends(String username);

    List<UserNeo4j> getSuggestedFriends(String username);

    public void addLikeToReview(String username,String id);


    void followUser(String followerUsername, String followedUsername);
    void unfollowUser(String followerUsername, String followedUsername);
    public void likeGame(String username, String name);
    public void dislikeGame(String username, String name);
    public void removeUser(String username);
    public void addUser(String id, String username);
    public UserNeo4j getUser(String username);
    boolean updateUser(String username, String newUsername);
}