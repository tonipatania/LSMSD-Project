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
   @Query("MATCH (u:UserNeo4j)-[:ADD]->(g:GameNeo4j) WHERE u.username = $username RETURN g.id as id, g.name as name, g.developers as developers, g.categories as categories, g.genres as genres")
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

}
