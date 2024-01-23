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



}