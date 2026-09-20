package it.unipi.lsmsd.gamehub.e2e;

import static org.hamcrest.Matchers.equalTo;

import io.restassured.http.ContentType;
import it.unipi.lsmsd.gamehub.DTO.RegistrationDTO;
import it.unipi.lsmsd.gamehub.DTO.ReviewDTO;
import it.unipi.lsmsd.gamehub.model.Game;
import it.unipi.lsmsd.gamehub.support.E2ETestSupport;
import org.junit.jupiter.api.Test;

// Registration -> create a review -> like it (from another user), all through real HTTP. The game
// itself is seeded
// directly (mongoTemplate) rather than through POST /game/create/{userId}: creating a game that
// way needs an already-admin Mongo user, which is orthogonal to what this journey is exercising -
// see backend-e2e-tests on seeding only what a real client couldn't create through the API.
class ReviewJourneyE2EIT extends E2ETestSupport {

    private Game seedGame() {
        Game game = new Game();
        game.setName("BARRIER X");
        game.setGenres("Action");
        game.setReleaseDate("Oct 21, 2008");
        game.setAvgScore(0);
        return mongoTemplate.save(game);
    }

    private void signUp(String name, String username) {
        anonymous()
                .contentType(ContentType.JSON)
                .body(
                        new RegistrationDTO(
                                name, "Test", username, "Passw0rd!", username + "@test.it"))
                .post("/signup")
                .then()
                .statusCode(201);
    }

    private void createReview(String author) {
        ReviewDTO review = new ReviewDTO();
        review.setTitle("BARRIER X");
        review.setUsername(author);
        review.setComment("Amazing");
        review.setUserScore(8);

        authenticatedAs(author, "USER")
                .contentType(ContentType.JSON)
                .body(review)
                .post("/review/gameSelected/create")
                .then()
                .statusCode(201);
    }

    // the review-by-title endpoint was removed as dead code (never called by the frontend, which
    // reads reviews from the game's own embedded, size-capped list instead) - so the review is
    // looked up the same way the UI does, via the paginated game search.
    private String firstReviewId() {
        return authenticatedAs("Kaistlin", "USER")
                .queryParam("name", "BARRIER X")
                .queryParam("page", 0)
                .queryParam("size", 1)
                .get("/game/searchFilter")
                .then()
                .statusCode(200)
                .body("content[0].reviews.size()", equalTo(1))
                .body("content[0].reviews[0].username", equalTo("Kaistlin"))
                .extract()
                .path("content[0].reviews[0].id");
    }

    private void expectLikeCount(int expected) {
        authenticatedAs("Kaistlin", "USER")
                .queryParam("name", "BARRIER X")
                .queryParam("page", 0)
                .queryParam("size", 1)
                .get("/game/searchFilter")
                .then()
                .statusCode(200)
                .body("content[0].reviews[0].likeCount", equalTo(expected));
    }

    @Test
    void registerCreateReviewThenAnotherUserLikesIt_updatesLikeCountVisibleThroughSearch() {
        seedGame();
        signUp("Kai", "Kaistlin");
        signUp("Lu", "Lunark");
        createReview("Kaistlin");
        String reviewId = firstReviewId();
        expectLikeCount(0);

        authenticatedAs("Lunark", "USER")
                .queryParam("id", reviewId)
                .post("/user/reviewSelected/addLikeReview")
                .then()
                .statusCode(200)
                .body(equalTo("added like"));

        expectLikeCount(1);
    }

    @Test
    void likingYourOwnReview_isRefusedAndLikeCountStaysZero() {
        seedGame();
        signUp("Kai", "Kaistlin");
        createReview("Kaistlin");
        String reviewId = firstReviewId();

        authenticatedAs("Kaistlin", "USER")
                .queryParam("id", reviewId)
                .post("/user/reviewSelected/addLikeReview")
                .then()
                .statusCode(200)
                .body(equalTo("no added like"));

        expectLikeCount(0);
    }

    @Test
    void repliesToAReview_areAcceptedFromOthersRefusedFromTheAuthorAndCounted() {
        seedGame();
        signUp("Kai", "Kaistlin");
        signUp("Lu", "Lunark");
        createReview("Kaistlin");
        String reviewId = firstReviewId();
        String body = "{\"reviewId\":\"" + reviewId + "\",\"comment\":\"Agreed!\"}";

        authenticatedAs("Kaistlin", "USER")
                .contentType(ContentType.JSON)
                .body(body)
                .post("/review/reply")
                .then()
                .statusCode(403);

        authenticatedAs("Lunark", "USER")
                .contentType(ContentType.JSON)
                .body(body)
                .post("/review/reply")
                .then()
                .statusCode(201)
                .body("username", equalTo("Lunark"));

        authenticatedAs("Kaistlin", "USER")
                .queryParam("reviewId", reviewId)
                .get("/review/replies")
                .then()
                .statusCode(200)
                .body("size()", equalTo(1))
                .body("[0].comment", equalTo("Agreed!"));

        authenticatedAs("Kaistlin", "USER")
                .queryParam("ids", reviewId)
                .get("/review/replies/counts")
                .then()
                .statusCode(200)
                .body(reviewId, equalTo(1));
    }
}
