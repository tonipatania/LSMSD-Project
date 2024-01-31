package it.unipi.lsmsd.gamehub.service;

import it.unipi.lsmsd.gamehub.model.GameNeo4j;
import it.unipi.lsmsd.gamehub.model.UserNeo4j;
import org.springframework.http.ResponseEntity;

import java.util.List;

public interface IUserNeo4jService {
    public void SyncUser();
    public void loadGames();



    List<UserNeo4j> getFollowedUser(String username);

    List<UserNeo4j> getFriendsOfFriends(String username);

    List<UserNeo4j> getSuggestedFriends(String username);

    public void addLikeToReview(String username,String id);


    void followUser(String followerUsername, String followedUsername);
    void unfollowUser(String followerUsername, String followedUsername);
    public ResponseEntity<String> addUser(String id, String username);
    public UserNeo4j getUser(String username);
    ResponseEntity<String > updateUser(String username, String newUsername);
}