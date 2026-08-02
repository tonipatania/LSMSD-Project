package it.unipi.lsmsd.gamehub.repository;

import it.unipi.lsmsd.gamehub.DTO.SuggestedUserDTO;
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

    @Query("MATCH (u:UserNeo4j {username: $username})-[:ADD]->(g:GameNeo4j) RETURN count(g)")
    long countWishlist(@Param("username") String username);

    // giochi presenti in entrambe le wishlist: e' lo stesso criterio con cui la Home calcola
    // "giochi in comune", cosi la card dei suggerimenti e il profilo raccontano la stessa cosa
    @Query("MATCH (:UserNeo4j {username: $username})-[:ADD]->(g:GameNeo4j)" +
           "<-[:ADD]-(:UserNeo4j {username: $friendUsername}) " +
           "RETURN DISTINCT g.id AS id, g.name AS name ORDER BY g.name")
    List<GameNeo4j> findCommonWishlistGames(@Param("username") String username,
                                            @Param("friendUsername") String friendUsername);

    // Il gioco va selezionato prima del MERGE: con un nome duplicato il MATCH restituisce piu nodi
    // e il MERGE creerebbe una relazione ADD verso ognuno, mettendo in wishlist giochi che l'utente
    // non ha scelto. Stesso ORDER BY di findGameByName per colpire lo stesso nodo.
    @Query("MATCH (g:GameNeo4j {name: $name}) WITH g ORDER BY g.id LIMIT 1 " +
           "MATCH (u:UserNeo4j {username: $username}) MERGE (u)-[:ADD]->(g)")
    void addGameToUser(@Param("username") String username, @Param("name") String name);

    @Query("MATCH (u:UserNeo4j {username: $username})-[r:ADD]->(g:GameNeo4j {name: $name}) DELETE r")
    void deleteGameFromUser(@Param("username") String username, @Param("name") String name);

    @Query("MATCH (u:UserNeo4j)-[:FOLLOW]->(following:UserNeo4j) WHERE u.username = $username RETURN following")
    List<UserNeo4j> findFollowedUsers (@Param("username") String username);

    @Query("MATCH (u:UserNeo4j) WHERE toLower(u.username) CONTAINS toLower($query) AND u.username <> $currentUsername RETURN u ORDER BY u.username LIMIT 20")
    List<UserNeo4j> searchUsers(@Param("query") String query, @Param("currentUsername") String currentUsername);

    @Query("MATCH (u:UserNeo4j {username: $username})-[:FOLLOW]->()-[:FOLLOW]->(friends) RETURN DISTINCT friends;")
    List<UserNeo4j> findFriendsOfFriends (@Param("username") String username);

    // Livello 1: amici di amici non ancora seguiti, con almeno 5 giochi in comune in wishlist.
    // Computed entirely in a single Cypher query to avoid per-candidate round trips.
    // Ogni RETURN proietta tutte le proprieta' di SuggestedUserDTO (anche quelle non pertinenti,
    // come null/0): un campo assente dal record fa emettere a DtoInstantiatingConverter un warning
    // "Cannot retrieve a value for property ..." per ogni riga risultante.
    @Query("MATCH (u:UserNeo4j {username: $username})-[:ADD]->(g:GameNeo4j) " +
           "WITH u, collect(g) AS myGames " +
           "MATCH (u)-[:FOLLOW]->()-[:FOLLOW]->(candidate:UserNeo4j) " +
           "WHERE candidate <> u AND NOT (u)-[:FOLLOW]->(candidate) " +
           "MATCH (candidate)-[:ADD]->(cg:GameNeo4j) WHERE cg IN myGames " +
           "WITH candidate, count(DISTINCT cg) AS commonGames " +
           "WHERE commonGames >= 5 " +
           "RETURN candidate.id AS id, candidate.username AS username, " +
           "'COMMON_FRIENDS' AS reason, commonGames AS commonGames, 0 AS followers " +
           "ORDER BY commonGames DESC LIMIT $limit")
    List<SuggestedUserDTO> findSuggestedFriends(@Param("username") String username, @Param("limit") int limit);

    // Livello 2: chiunque non ancora seguito condivida giochi in wishlist, anche fuori dalla
    // rete di follow. Copre il caso di utenti che non seguono ancora nessuno.
    @Query("MATCH (u:UserNeo4j {username: $username})-[:ADD]->(g:GameNeo4j)<-[:ADD]-(candidate:UserNeo4j) " +
           "WHERE candidate <> u AND NOT (u)-[:FOLLOW]->(candidate) " +
           "WITH candidate, count(DISTINCT g) AS commonGames " +
           "RETURN candidate.id AS id, candidate.username AS username, " +
           "'SIMILAR_TASTES' AS reason, commonGames AS commonGames, 0 AS followers " +
           "ORDER BY commonGames DESC LIMIT $limit")
    List<SuggestedUserDTO> findUsersWithSimilarTastes(@Param("username") String username, @Param("limit") int limit);

    // Livello 3: utenti piu seguiti della rete. Fallback che non e mai vuoto finche esiste almeno
    // una relazione FOLLOW nel grafo. La classifica e' globale (non dipende da chi la chiede), per
    // questo non filtra qui l'utente corrente: il filtro lo applica il service sul risultato in
    // cache, cosi la scansione di tutte le relazioni FOLLOW avviene una volta sola e non a ogni
    // caricamento della home.
    @Query("MATCH (candidate:UserNeo4j)<-[:FOLLOW]-() " +
           "WITH candidate, count(*) AS followers " +
           "RETURN candidate.id AS id, candidate.username AS username, " +
           "'POPULAR' AS reason, 0 AS commonGames, followers AS followers " +
           "ORDER BY followers DESC LIMIT $limit")
    List<SuggestedUserDTO> findMostFollowedUsers(@Param("limit") int limit);

    @Query("MATCH (u:UserNeo4j)-[:FOLLOW]->(following:UserNeo4j) WHERE u.username = $username RETURN following ORDER BY following.username SKIP $skip LIMIT $limit")
    List<UserNeo4j> findFollowedUsersPage(@Param("username") String username, @Param("skip") long skip, @Param("limit") long limit);

    @Query("MATCH (u:UserNeo4j)-[:FOLLOW]->(following:UserNeo4j) WHERE u.username = $username RETURN count(following)")
    long countFollowedUsers(@Param("username") String username);

 //DA MODIFICARE NEL MAIN->AGGIUNGE LIKE AD UNA REVIEW
 @Query("MATCH (u:UserNeo4j {username:$username}), (g:ReviewNeo4j {id: $id}) OPTIONAL MATCH (u)-[r:LIKE]->(g) WITH u, g, r MERGE (u)-[:LIKE]->(g) RETURN r IS NOT NULL AS relationshipExists")
    Boolean addLikeToReview(@Param("username") String username, @Param("id") String id);

 @Query("MATCH (user:UserNeo4j{username:$username})-[like:LIKE]->(review:ReviewNeo4j{id: $id}) DELETE like")
 void deleteLikeFromReview(@Param("username") String username, @Param("id") String id);

 //rimuove il like e dice se il like esisteva davvero
 @Query("MATCH (user:UserNeo4j{username:$username})-[like:LIKE]->(review:ReviewNeo4j{id: $id}) DELETE like RETURN count(like) AS removed")
 Long removeLikeFromReview(@Param("username") String username, @Param("id") String id);

 //id delle review a cui l'utente ha messo like
 @Query("MATCH (user:UserNeo4j{username:$username})-[:LIKE]->(review:ReviewNeo4j) RETURN review.id")
 List<String> findLikedReviewIds(@Param("username") String username);

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
