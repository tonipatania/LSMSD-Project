package it.unipi.lsmsd.gamehub.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import it.unipi.lsmsd.gamehub.model.Activity;
import it.unipi.lsmsd.gamehub.model.ActivityType;
import it.unipi.lsmsd.gamehub.model.Game;
import it.unipi.lsmsd.gamehub.model.Notification;
import it.unipi.lsmsd.gamehub.model.NotificationType;
import it.unipi.lsmsd.gamehub.model.Review;
import it.unipi.lsmsd.gamehub.model.ReviewReply;
import it.unipi.lsmsd.gamehub.model.User;
import it.unipi.lsmsd.gamehub.repository.UserNeo4jRepository;
import it.unipi.lsmsd.gamehub.service.IAccountService;
import it.unipi.lsmsd.gamehub.service.IGameService;
import it.unipi.lsmsd.gamehub.support.IntegrationTestSupport;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;

// La cancellazione account e' quasi tutta Cypher e query Mongo su piu' collezioni: un test con i
// repository mockati non puo' dire se i nodi spariscono davvero (DETACH DELETE) o se i likeCount
// tornano corretti, quindi qui si usano Mongo e Neo4j veri.
class AccountServiceIT extends IntegrationTestSupport {

    private static final String PASSWORD = "Password1!";

    @Autowired private IAccountService accountService;
    @Autowired private UserNeo4jRepository userNeo4jRepository;
    @Autowired private IGameService gameService;
    @Autowired private PasswordEncoder passwordEncoder;

    private Review marioReview;
    private Review luigiReview;

    @BeforeEach
    void seed() {
        mongoTemplate.save(newUser("mario"));
        mongoTemplate.save(newUser("luigi"));
        for (String name : List.of("mario", "luigi")) {
            neo4jClient
                    .query("CREATE (:UserNeo4j {id: $id, username: $name})")
                    .bindAll(Map.of("id", "id-" + name, "name", name))
                    .run();
        }
        Game portal = new Game();
        portal.setName("Portal");
        mongoTemplate.save(portal);
        neo4jClient.query("CREATE (:GameNeo4j {id: 'g1', name: 'Portal'})").run();

        marioReview = mongoTemplate.save(newReview("Portal", "mario", 1));
        luigiReview = mongoTemplate.save(newReview("Portal", "luigi", 1));
        for (Review review : List.of(marioReview, luigiReview)) {
            neo4jClient
                    .query("CREATE (:ReviewNeo4j {id: $id})")
                    .bindAll(Map.of("id", review.getId()))
                    .run();
        }
        // mario segue luigi e viceversa, mario ha Portal in wishlist,
        // mario ha messo like alla recensione di luigi e luigi a quella di mario
        neo4jClient
                .query(
                        "MATCH (m:UserNeo4j {username:'mario'}), (l:UserNeo4j {username:'luigi'}), "
                                + "(g:GameNeo4j {id:'g1'}), (rm:ReviewNeo4j {id:$rm}), "
                                + "(rl:ReviewNeo4j {id:$rl}) "
                                + "CREATE (m)-[:FOLLOW]->(l), (l)-[:FOLLOW]->(m), (m)-[:ADD]->(g), "
                                + "(m)-[:LIKE]->(rl), (l)-[:LIKE]->(rm)")
                .bindAll(Map.of("rm", marioReview.getId(), "rl", luigiReview.getId()))
                .run();
        gameService.updateGameReviewFromScratch(
                mongoTemplate.findOne(Query.query(Criteria.where("name").is("Portal")), Game.class),
                20);

        mongoTemplate.save(
                new ReviewReply(null, marioReview.getId(), "luigi", "bella", Instant.now()));
        mongoTemplate.save(
                new ReviewReply(null, luigiReview.getId(), "mario", "anche no", Instant.now()));
        mongoTemplate.save(notification("luigi", "mario", NotificationType.FOLLOW));
        mongoTemplate.save(notification("mario", "luigi", NotificationType.FOLLOW));
        mongoTemplate.save(activity("mario", ActivityType.REVIEW, marioReview.getId(), null));
        mongoTemplate.save(activity("luigi", ActivityType.FOLLOW, null, "mario"));
        mongoTemplate.save(activity("luigi", ActivityType.FOLLOW, null, "peach"));
    }

    @AfterEach
    void cleanUpExtraCollections() {
        for (String collection :
                new String[] {"review_replies", "notifications", "activities", "feed_states"}) {
            mongoTemplate.getCollection(collection).deleteMany(new org.bson.Document());
        }
    }

    @Test
    void deleteAccount_removesTheUserAndEverythingTiedToThem() {
        ResponseEntity<String> response = accountService.deleteAccount("mario", PASSWORD);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Mongo: utente e recensione spariti, quelli di luigi no
        assertThat(mongoTemplate.findAll(User.class))
                .extracting(User::getUsername)
                .containsExactly("luigi");
        assertThat(mongoTemplate.findAll(Review.class))
                .extracting(Review::getUsername)
                .containsExactly("luigi");
        // il like di mario alla recensione di luigi e' stato tolto dal contatore
        assertThat(mongoTemplate.findById(luigiReview.getId(), Review.class).getLikeCount())
                .isZero();
        // risposte: quella di mario e quella alla sua recensione sono sparite
        assertThat(mongoTemplate.findAll(ReviewReply.class)).isEmpty();
        // notifiche e attivita' che nominano mario (come autore o come bersaglio) sono sparite
        assertThat(mongoTemplate.findAll(Notification.class)).isEmpty();
        assertThat(mongoTemplate.findAll(Activity.class))
                .extracting(Activity::getTargetUsername)
                .containsExactly("peach");
        // la lista embedded nel gioco non contiene piu' la recensione cancellata, e quella di
        // luigi ha il contatore aggiornato
        Game portal =
                mongoTemplate.findOne(Query.query(Criteria.where("name").is("Portal")), Game.class);
        assertThat(portal.getReviews()).extracting(Review::getUsername).containsExactly("luigi");
        assertThat(portal.getReviews().get(0).getLikeCount()).isZero();

        // Neo4j: nodo utente e nodo recensione di mario spariti con tutte le relazioni, il resto no
        assertThat(count("MATCH (u:UserNeo4j {username:'mario'}) RETURN count(u)")).isZero();
        assertThat(
                        count(
                                "MATCH (r:ReviewNeo4j {id:'"
                                        + marioReview.getId()
                                        + "'}) RETURN count(r)"))
                .isZero();
        assertThat(count("MATCH (u:UserNeo4j {username:'luigi'}) RETURN count(u)")).isEqualTo(1);
        assertThat(count("MATCH (:UserNeo4j)-[r]->() RETURN count(r)")).isZero();
        assertThat(count("MATCH (g:GameNeo4j {id:'g1'}) RETURN count(g)")).isEqualTo(1);
    }

    @Test
    void deleteAccount_wrongPassword_leavesEverythingUntouched() {
        ResponseEntity<String> response = accountService.deleteAccount("mario", "Wrong1!");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(mongoTemplate.findAll(User.class)).hasSize(2);
        assertThat(mongoTemplate.findAll(Review.class)).hasSize(2);
        assertThat(count("MATCH (u:UserNeo4j) RETURN count(u)")).isEqualTo(2);
    }

    @Test
    void deleteAccount_calledTwice_secondCallIsNotFound() {
        accountService.deleteAccount("mario", PASSWORD);

        ResponseEntity<String> second = accountService.deleteAccount("mario", PASSWORD);

        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    private long count(String cypher) {
        return ((Number)
                        neo4jClient
                                .query(cypher)
                                .fetch()
                                .one()
                                .orElseThrow()
                                .values()
                                .iterator()
                                .next())
                .longValue();
    }

    private User newUser(String username) {
        User user = new User();
        user.setUsername(username);
        user.setName(username);
        user.setSurname("Test");
        user.setEmail(username + "@test.it");
        user.setPassword(passwordEncoder.encode(PASSWORD));
        user.setEnabled(true);
        return user;
    }

    private Review newReview(String title, String username, int likeCount) {
        Review review = new Review();
        review.setTitle(title);
        review.setUsername(username);
        review.setComment("ottimo");
        review.setUserScore(9);
        review.setLikeCount(likeCount);
        review.setCreatedAt(Instant.now());
        return review;
    }

    private Notification notification(String recipient, String actor, NotificationType type) {
        Notification notification = new Notification();
        notification.setRecipient(recipient);
        notification.setActor(actor);
        notification.setType(type);
        notification.setCreatedAt(Instant.now());
        return notification;
    }

    private Activity activity(String username, ActivityType type, String reviewId, String target) {
        Activity activity = new Activity();
        activity.setUsername(username);
        activity.setType(type);
        activity.setReviewId(reviewId);
        activity.setTargetUsername(target);
        activity.setCreatedAt(Instant.now());
        return activity;
    }

    @Test
    void consumeAllLikedReviewIds_removesTheRelationshipSoARetryFindsNothing() {
        // mario likes luigi's review (seeded in @BeforeEach): the first call must return it and
        // remove the relationship, a repeated call (simulating a retry after a later failure)
        // must find nothing left to re-decrement
        List<String> firstCall = userNeo4jRepository.consumeAllLikedReviewIds("mario");

        assertThat(firstCall).containsExactly(luigiReview.getId());
        assertThat(userNeo4jRepository.consumeAllLikedReviewIds("mario")).isEmpty();
    }
}
