package it.unipi.lsmsd.gamehub.repository;

import it.unipi.lsmsd.gamehub.model.GameNeo4j;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.data.neo4j.core.Neo4jOperations;

import java.util.List;

@Repository
public interface GameNeo4jRepository extends Neo4jRepository<GameNeo4j, String> {

    @Query("MATCH (g:GameNeo4j) WHERE g.name = $name RETURN g ")
    GameNeo4j findGameByName(@Param("name") String name);
   //@Query("MATCH (g:GameNeo4j)<-[:ADD]-(u:UserNeo4j) RETURN g.id as id, g.name as name, g.developers as developers, g.categories as categories, g.genres as genres, count(u) as numberOfLinks ORDER BY numberOfLinks DESC LIMIT 10")
   @Query("MATCH (g:GameNeo4j)<-[:ADD]-(u:UserNeo4j) WHERE g.name = $name RETURN count(u) as numberOfLinks")
    int findGameIngoingLinks(@Param("name") String name);

   @Query("MATCH (g:GameNeo4j {id: $gameId})<-[r:WISHLIST]-(utente:UserNeo4j)-[:WISHLIST]->(giochi:GameNeo4j)\n" +
           "WHERE utente.username <> $username\n" +
           "AND NOT (giochi:GameNeo4j)<-[:WISHLIST]-(:UserNeo4j {username:$username})\n" +
           "RETURN giochi")
    List<GameNeo4j> findSuggestGames(@Param("gameId") String gameId, @Param("username") String username);

  @Query("MATCH (a:GameNeo4j) WHERE a.id = $gameId DELETE a")
  void removeGame(String gameId);
  @Query("CREATE (a:GameNeo4j {id: $id, name: $name})")
  void addGame(String id, String name);

 //@Query("MERGE (a:GameNeo4j {name: $name}) SET a.name = $name")
  @Query("MERGE (a:GameNeo4j {name: $name}) SET a.name = $newName, a.developers = $newDevelopers, a.categories = $newCategories, a.genres = $newGenres")
  void updateGame(String name, String newName, String newDevelopers, String newCategories, String newGenres);
}
