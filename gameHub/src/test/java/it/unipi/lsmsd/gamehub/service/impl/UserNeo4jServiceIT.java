package it.unipi.lsmsd.gamehub.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import it.unipi.lsmsd.gamehub.model.Game;
import it.unipi.lsmsd.gamehub.model.Review;
import it.unipi.lsmsd.gamehub.model.User;
import it.unipi.lsmsd.gamehub.model.UserNeo4j;
import it.unipi.lsmsd.gamehub.service.IUserNeo4jService;
import it.unipi.lsmsd.gamehub.support.IntegrationTestSupport;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

// The social graph (follow, wishlist, likes) lives only in Neo4j as edges - a mocked-repository
// unit test can assert a Cypher method was called with the right arguments, but not that the
// graph actually ends up in the shape the rest of the app (getFollowedUser, getUserWishlist, ...)
// relies on. See backend-integration-tests for the shared Testcontainers setup.
class UserNeo4jServiceIT extends IntegrationTestSupport {

    @Autowired private IUserNeo4jService userNeo4jService;

    private void seedUserNode(String id, String username) {
        neo4jClient
                .query("CREATE (a:UserNeo4j {id: $id, username: $username})")
                .bindAll(Map.of("id", id, "username", username))
                .run();
    }

    private void seedGameNode(String id, String name) {
        neo4jClient
                .query("CREATE (g:GameNeo4j {id: $id, name: $name})")
                .bindAll(Map.of("id", id, "name", name))
                .run();
    }

    @Test
    void syncUser_copiesEveryMongoUserIntoNeo4j() {
        User user = new User();
        user.setUsername("Lunark");
        user.setName("Lu");
        user.setSurname("Nark");
        user.setPassword("pwd");
        user.setEmail("lunark@test.it");
        mongoTemplate.save(user);

        userNeo4jService.SyncUser();

        Map<String, Object> node =
                neo4jClient
                        .query("MATCH (a:UserNeo4j {username: $username}) RETURN a.id AS id")
                        .bindAll(Map.of("username", "Lunark"))
                        .fetch()
                        .one()
                        .orElseThrow();
        assertThat(node.get("id")).isEqualTo(user.getId());
    }

    @Test
    void loadGames_copiesEveryMongoGameIntoNeo4j() {
        Game game = new Game();
        game.setName("BARRIER X");
        game.setGenres("Action");
        game.setReleaseDate("Oct 21, 2008");
        mongoTemplate.save(game);

        userNeo4jService.loadGames();

        Map<String, Object> node =
                neo4jClient
                        .query("MATCH (g:GameNeo4j {name: $name}) RETURN g.id AS id")
                        .bindAll(Map.of("name", "BARRIER X"))
                        .fetch()
                        .one()
                        .orElseThrow();
        assertThat(node.get("id")).isEqualTo(game.getId());
    }

    @Test
    void followUser_bothUsersExistInGraph_createsFollowRelationship() {
        seedUserNode("u1", "Lunark");
        seedUserNode("u2", "Kaistlin");

        Boolean result = userNeo4jService.followUser("Lunark", "Kaistlin");

        assertThat(result).isTrue();
        List<UserNeo4j> followed = userNeo4jService.getFollowedUser("Lunark");
        assertThat(followed).extracting(UserNeo4j::getUsername).containsExactly("Kaistlin");
    }

    @Test
    void followUser_followedUserMissingFromGraph_returnsFalseAndCreatesNoRelationship() {
        seedUserNode("u1", "Lunark");
        // "Kaistlin" is a real Mongo user in spirit but has never been synced into Neo4j.

        Boolean result = userNeo4jService.followUser("Lunark", "Kaistlin");

        assertThat(result).isFalse();
        assertThat(userNeo4jService.getFollowedUser("Lunark")).isEmpty();
    }

    @Test
    void addGameToWishlist_userAndGameExistInGraph_createsAddRelationship() {
        seedUserNode("u1", "Lunark");
        seedGameNode("g1", "BARRIER X");

        Boolean result = userNeo4jService.addGameToWishlist("Lunark", "BARRIER X");

        assertThat(result).isTrue();
        Map<String, Object> edge =
                neo4jClient
                        .query(
                                "MATCH (:UserNeo4j {username: $username})-[:ADD]->(g:GameNeo4j {name: $name}) "
                                        + "RETURN g.id AS id")
                        .bindAll(Map.of("username", "Lunark", "name", "BARRIER X"))
                        .fetch()
                        .one()
                        .orElseThrow();
        assertThat(edge.get("id")).isEqualTo("g1");
    }

    @Test
    void addGameToWishlist_gameMissingFromGraph_returnsFalse() {
        seedUserNode("u1", "Lunark");

        Boolean result = userNeo4jService.addGameToWishlist("Lunark", "Nonexistent Game");

        assertThat(result).isFalse();
    }

    @Test
    void addLikeToReview_firstLike_incrementsMongoLikeCountAndCreatesLikeRelationship() {
        Game game = new Game();
        game.setName("BARRIER X");
        game.setGenres("Action");
        game.setReleaseDate("Oct 21, 2008");

        Review review = new Review();
        review.setTitle("BARRIER X");
        review.setUsername("Kaistlin");
        review.setUserScore(8);
        review.setComment("great");
        review.setLikeCount(0);
        mongoTemplate.save(review);

        // The review must already be embedded in the game's top-reviews list: UserNeo4jService
        // .addLikeToReview looks up the least-liked embedded review to decide whether to update it
        // in place, and NPEs if the embedded list is empty - see backend-integration-tests' Review
        // section for the ReviewController-level rollback test this same class also covers.
        game.setReviews(List.of(review));
        game.setHasReviews(true);
        mongoTemplate.save(game);

        seedUserNode("u1", "Lunark");
        neo4jClient
                .query("CREATE (r:ReviewNeo4j {id: $id})")
                .bindAll(Map.of("id", review.getId()))
                .run();

        Boolean result = userNeo4jService.addLikeToReview("Lunark", review.getId());

        assertThat(result).isTrue();
        Review updated = mongoTemplate.findById(review.getId(), Review.class);
        assertThat(updated).isNotNull();
        assertThat(updated.getLikeCount()).isEqualTo(1);

        Map<String, Object> edge =
                neo4jClient
                        .query(
                                "MATCH (:UserNeo4j {username: $username})-[:LIKE]->(r:ReviewNeo4j {id: $id}) "
                                        + "RETURN r.id AS id")
                        .bindAll(Map.of("username", "Lunark", "id", review.getId()))
                        .fetch()
                        .one()
                        .orElseThrow();
        assertThat(edge.get("id")).isEqualTo(review.getId());
    }
}
