package it.unipi.lsmsd.gamehub.service;

import it.unipi.lsmsd.gamehub.DTO.SuggestedUserDTO;
import it.unipi.lsmsd.gamehub.model.Game;
import it.unipi.lsmsd.gamehub.model.GameNeo4j;
import it.unipi.lsmsd.gamehub.model.UserNeo4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;

import java.util.List;

public interface IUserNeo4jService {
    public void SyncUser();
    public void loadGames();


    //DA MODIFICARE NEL MAIN->TROVA LA LISTA DI GIOCHI DEGLI AMICI
    // restituisce i documenti Mongo completi, non i soli id+nome del grafo, cosi la UI puo'
    // mostrare copertina, generi e voto
    public List<Game> getUserWishlist(String username,String friendUsername);

    Page<Game> getUserWishlistPage(String username, String friendUsername, Pageable pageable,
                                   String sort, boolean onlyCommon);

    List<GameNeo4j> getCommonWishlistGames(String username, String friendUsername);

    public Boolean addGameToWishlist(String username,String name);

    public Boolean deleteGameToWishlist(String username,String name);

    List<UserNeo4j> getFollowedUser(String username);

    Page<UserNeo4j> getFollowedUserPage(String username, Pageable pageable);

    List<UserNeo4j> getFriendsOfFriends(String username);

    List<SuggestedUserDTO> getSuggestedFriends(String username);

    List<UserNeo4j> searchUsers(String query, String currentUsername);

    public Boolean addLikeToReview(String username,String id);

    public long countUserDocument();



    Boolean followUser(String followerUsername, String followedUsername);
    Boolean unfollowUser(String followerUsername, String followedUsername);
    public ResponseEntity<String> addUser(String id, String username);
    public UserNeo4j getUser(String username);
    ResponseEntity<String > updateUser(String username, String newUsername);


}