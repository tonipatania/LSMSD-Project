package it.unipi.lsmsd.gamehub.e2e;

import static org.hamcrest.Matchers.hasItem;

import io.restassured.http.ContentType;
import it.unipi.lsmsd.gamehub.DTO.RegistrationDTO;
import it.unipi.lsmsd.gamehub.model.Game;
import it.unipi.lsmsd.gamehub.support.E2ETestSupport;
import java.util.Map;
import org.junit.jupiter.api.Test;

// Follow/wishlist over real HTTP, plus the admin loadgames endpoint - the only endpoint in this
// app gated by a real hasRole("ADMIN") check at the Security layer (every other "admin" endpoint
// is only an application-level Mongo User.role lookup, see backend-e2e-tests). /user/sync was
// removed: it let anyone holding an ADMIN token trigger a full Mongo->Neo4j user resync on demand,
// an unbounded-cost operation with no place in a production attack surface.
class SocialGraphJourneyE2EIT extends E2ETestSupport {

    private void registerUser(String username) {
        anonymous()
                .contentType(ContentType.JSON)
                .body(
                        new RegistrationDTO(
                                username, username, username, "pwd123", username + "@test.it"))
                .post("/signup")
                .then()
                .statusCode(201);
    }

    @Test
    void loadGames_requiresRealAdminRoleClaim() {
        anonymous().post("/user/loadgames").then().statusCode(401);
        authenticatedAs("someone", "USER").post("/user/loadgames").then().statusCode(403);
        authenticatedAs("someone", "ADMIN").post("/user/loadgames").then().statusCode(200);
    }

    @Test
    void followUser_thenAppearsInFollowedUserList() {
        registerUser("Alice");
        registerUser("Bob");

        authenticatedAs("Alice", "USER")
                .queryParam("followerUsername", "Alice")
                .queryParam("followedUsername", "Bob")
                .post("/user/userSelected/follow")
                .then()
                .statusCode(200);

        authenticatedAs("Alice", "USER")
                .queryParam("username", "Alice")
                .get("/user/followedUser")
                .then()
                .statusCode(200)
                .body("username", hasItem("Bob"));
    }

    @Test
    void addGameToWishlist_thenAppearsInWishlist() {
        registerUser("Alice");

        Game game = new Game();
        game.setName("BARRIER X");
        game.setGenres("Action");
        game.setReleaseDate("Oct 21, 2008");
        mongoTemplate.save(game);
        neo4jClient
                .query("CREATE (g:GameNeo4j {id: $id, name: $name})")
                .bindAll(Map.of("id", game.getId(), "name", game.getName()))
                .run();

        authenticatedAs("Alice", "USER")
                .queryParam("username", "Alice")
                .queryParam("name", "BARRIER X")
                .post("/user/wishlist/addWishlistGame")
                .then()
                .statusCode(200);

        authenticatedAs("Alice", "USER")
                .queryParam("username", "Alice")
                .get("/user/userSelected/wishlist")
                .then()
                .statusCode(200)
                .body("name", hasItem("BARRIER X"));
    }
}
