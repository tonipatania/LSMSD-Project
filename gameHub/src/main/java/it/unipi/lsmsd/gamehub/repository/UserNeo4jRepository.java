package it.unipi.lsmsd.gamehub.repository;

import it.unipi.lsmsd.gamehub.model.GameNeo4j;
import it.unipi.lsmsd.gamehub.model.UserNeo4j;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface UserNeo4jRepository extends Neo4jRepository<UserNeo4j, String> {
  // @Query("MATCH (u:UserNeo4j)-[:ADD]->(g:GameNeo4j) WHERE u.username = $username RETURN g")
   @Query("MATCH (u:UserNeo4j)-[:WISHLIST]->(g:GameNeo4j) WHERE u.username = $username RETURN g.id as id, g.name as name")
   List<GameNeo4j> findByUsername(@Param("username") String username);

    @Query("MATCH (u:UserNeo4j {username: $username}), (g:GameNeo4j {name: $name}) MERGE (u)-[:ADD]->(g)")
    void addGameToUser(@Param("username") String username, @Param("name") String name);

    @Query("MATCH (u:UserNeo4j {username: $username})-[r:ADD]->(g:GameNeo4j {name: $name}) DELETE r")
    void deleteGameFromUser(@Param("username") String username, @Param("name") String name);

    @Query("MATCH (u:UserNeo4j)-[:FOLLOW]->(following:UserNeo4j) WHERE u.username = $username RETURN following")
    List<UserNeo4j> findFollowedUsers (@Param("username") String username);

    @Query("MATCH (u:UserNeo4j {username: $username})-[:FOLLOW]->()-[:FOLLOW]->(friends) RETURN DISTINCT friends;")
    List<UserNeo4j> findFriendsOfFriends (@Param("username") String username);

    @Query("MATCH (u:UserNeo4j {username: $username}), (r:ReviewNeo4j {id: $id}) MERGE (u)-[:LIKE]->(r)")
    void addLikeToReview(@Param("username") String username, @Param("id") String id);

    @Query("MATCH (a:UserNeo4j {username: $followerUsername}), (b:UserNeo4j {username: $followedUsername}) MERGE (a)-[:FOLLOWS]->(b)")
    void followUser(String followerUsername, String followedUsername);
    @Query("MATCH (a:UserNeo4j {username: $followerUsername})-[r:FOLLOWS]->(b:UserNeo4j {username: $followedUsername}) DELETE r")
    void unfollowUser(String followerUsername, String followedUsername);

    // Method to remove a like based on username and game ID

    //@Query("MATCH (u:UserNeo4j {username: $username})-[r:LIKES]->(g:GameNeo4j {id: $gameId}) DELETE r")
    @Query("MATCH (a:UserNeo4j) WHERE a.username = $username DELETE a")
    void removeUser(String username);
    //@Query("MATCH (a:UserNeo4j) WHERE a.username = '$username' DELETE a")
    @Query("CREATE (a:UserNeo4j {id: $id, username: $username})")
    void addUser(String id, String username);
    @Query("MATCH (a:UserNeo4j {username: $username}) RETURN a")
    UserNeo4j getUser(String username);
    @Query("MATCH (a:UserNeo4j {username: $username}) SET a.username = $newUsername")
    void updateUser(String username, String newUsername);
 }
