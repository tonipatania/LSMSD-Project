package it.unipi.lsmsd.gamehub.repository;

import it.unipi.lsmsd.gamehub.model.GameNeo4j;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface GameNeo4jRepository extends Neo4jRepository<GameNeo4j, String> {

    @Query("MATCH (g:GameNeo4j) WHERE g.name = $name RETURN g ")
    GameNeo4j findGameByName(@Param("name") String name);
   //@Query("MATCH (g:GameNeo4j)<-[:ADD]-(u:UserNeo4j) RETURN g.id as id, g.name as name, g.developers as developers, g.categories as categories, g.genres as genres, count(u) as numberOfLinks ORDER BY numberOfLinks DESC LIMIT 10")
   @Query("MATCH (g:GameNeo4j)<-[:ADD]-(u:UserNeo4j) WHERE g.name = $name RETURN count(u) as numberOfLinks")
    int findGameIngoingLinks(@Param("name") String name);

   // Collaborative filtering sull'intera wishlist: parte da tutti i giochi dell'utente, non da uno
   // solo scelto a caso. Con un singolo gioco seme la lista risultava spesso vuota, perche' basta
   // che quel gioco non sia in wishlist a nessun altro. Il punteggio e' il numero di utenti che
   // hanno sia un gioco della mia wishlist sia quello consigliato.
   @Query("MATCH (me:UserNeo4j {username: $username})-[:ADD]->(mine:GameNeo4j) " +
           "WITH me, collect(mine) AS myGames " +
           "UNWIND myGames AS seed " +
           "MATCH (seed)<-[:ADD]-(other:UserNeo4j)-[:ADD]->(suggested:GameNeo4j) " +
           "WHERE other <> me AND NOT suggested IN myGames " +
           "WITH suggested, count(DISTINCT other) AS score " +
           "RETURN suggested.id AS id, suggested.name AS name " +
           "ORDER BY score DESC LIMIT $limit")
    List<GameNeo4j> findSuggestGames(@Param("username") String username, @Param("limit") int limit);

   // Fallback per chi ha la wishlist vuota: i giochi piu presenti nelle wishlist della piattaforma.
   @Query("MATCH (g:GameNeo4j)<-[:ADD]-(:UserNeo4j) " +
           "WITH g, count(*) AS wishlistCount " +
           "RETURN g.id AS id, g.name AS name " +
           "ORDER BY wishlistCount DESC LIMIT $limit")
    List<GameNeo4j> findMostWishlistedGames(@Param("limit") int limit);

  @Query("MATCH (a:GameNeo4j) WHERE a.id = $gameId DELETE a")
  void removeGame(String gameId);
  @Query("CREATE (a:GameNeo4j {id: $id, name: $name})")
  void addGame(String id, String name);

 //@Query("MERGE (a:GameNeo4j {name: $name}) SET a.name = $name")
  @Query("MERGE (a:GameNeo4j {name: $name}) SET a.name = $newName, a.developers = $newDevelopers, a.categories = $newCategories, a.genres = $newGenres")
  void updateGame(String name, String newName, String newDevelopers, String newCategories, String newGenres);
}
