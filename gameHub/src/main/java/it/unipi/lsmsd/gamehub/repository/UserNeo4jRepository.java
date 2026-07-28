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



  //DA MODIFICARE NEL MAIN->TROVA LA LISTA DI GIOCHI DEGLI AMICI
   @Query("MATCH (u:UserNeo4j)-[:ADD]->(g:GameNeo4j) WHERE u.username = $username RETURN g.id as id, g.name as name")
   List<GameNeo4j> findByUsername(@Param("username") String username);

    @Query("MATCH (u:UserNeo4j {username: $username}), (g:GameNeo4j {name: $name}) MERGE (u)-[:ADD]->(g)")
    void addGameToUser(@Param("username") String username, @Param("name") String name);

    @Query("MATCH (u:UserNeo4j {username: $username})-[r:ADD]->(g:GameNeo4j {name: $name}) DELETE r")
    void deleteGameFromUser(@Param("username") String username, @Param("name") String name);

    @Query("MATCH (u:UserNeo4j)-[:FOLLOW]->(following:UserNeo4j) WHERE u.username = $username RETURN following")
    List<UserNeo4j> findFollowedUsers (@Param("username") String username);

    @Query("MATCH (u:UserNeo4j) WHERE toLower(u.username) CONTAINS toLower($query) AND u.username <> $currentUsername RETURN u ORDER BY u.username LIMIT 20")
    List<UserNeo4j> searchUsers(@Param("query") String query, @Param("currentUsername") String currentUsername);

    @Query("MATCH (u:UserNeo4j {username: $username})-[:FOLLOW]->()-[:FOLLOW]->(friends) RETURN DISTINCT friends;")
    List<UserNeo4j> findFriendsOfFriends (@Param("username") String username);

    // Friends of friends not already followed, with at least 5 games in common wishlist.
    // Computed entirely in a single Cypher query to avoid per-candidate round trips.
    @Query("MATCH (u:UserNeo4j {username: $username})-[:ADD]->(g:GameNeo4j) " +
           "WITH u, collect(g) AS myGames " +
           "MATCH (u)-[:FOLLOW]->()-[:FOLLOW]->(candidate:UserNeo4j) " +
           "WHERE candidate <> u AND NOT (u)-[:FOLLOW]->(candidate) " +
           "MATCH (candidate)-[:ADD]->(cg:GameNeo4j) WHERE cg IN myGames " +
           "WITH candidate, count(DISTINCT cg) AS commonGames " +
           "WHERE commonGames >= 5 " +
           "RETURN candidate ORDER BY rand() LIMIT 50")
    List<UserNeo4j> findSuggestedFriends(@Param("username") String username);

    @Query("MATCH (u:UserNeo4j)-[:FOLLOW]->(following:UserNeo4j) WHERE u.username = $username RETURN following ORDER BY following.username SKIP $skip LIMIT $limit")
    List<UserNeo4j> findFollowedUsersPage(@Param("username") String username, @Param("skip") long skip, @Param("limit") long limit);

    @Query("MATCH (u:UserNeo4j)-[:FOLLOW]->(following:UserNeo4j) WHERE u.username = $username RETURN count(following)")
    long countFollowedUsers(@Param("username") String username);

 //DA MODIFICARE NEL MAIN->AGGIUNGE LIKE AD UNA REVIEW
 @Query("MATCH (u:UserNeo4j {username:$username}), (g:ReviewNeo4j {id: $id}) OPTIONAL MATCH (u)-[r:LIKE]->(g) WITH u, g, r MERGE (u)-[:LIKE]->(g) RETURN r IS NOT NULL AS relationshipExists")
    Boolean addLikeToReview(@Param("username") String username, @Param("id") String id);

 @Query("MATCH (user:UserNeo4j{username:$username})-[like:LIKE]->(review:ReviewNeo4j{id: $id}) DELETE like")
 void deleteLikeFromReview(@Param("username") String username, @Param("id") String id);

    @Query("MATCH (a:UserNeo4j {username: $followerUsername}), (b:UserNeo4j {username: $followedUsername}) MERGE (a)-[:FOLLOW]->(b)")
    void followUser(String followerUsername, String followedUsername);
    @Query("MATCH (a:UserNeo4j {username: $followerUsername})-[r:FOLLOW]->(b:UserNeo4j {username: $followedUsername}) DELETE r")
    void unfollowUser(String followerUsername, String followedUsername);

    // Method to remove a like based on username and game ID
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
