package it.unipi.lsmsd.gamehub.e2e;

import static org.hamcrest.Matchers.equalTo;

import io.restassured.http.ContentType;
import it.unipi.lsmsd.gamehub.DTO.RegistrationDTO;
import it.unipi.lsmsd.gamehub.DTO.ReviewDTO;
import it.unipi.lsmsd.gamehub.model.Game;
import it.unipi.lsmsd.gamehub.support.E2ETestSupport;
import org.junit.jupiter.api.Test;

// Registration -> create a review -> like it, all through real HTTP. The game itself is seeded
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

    @Test
    void registerCreateReviewThenLike_updatesLikeCountVisibleThroughSearch() {
        seedGame();
        anonymous()
                .contentType(ContentType.JSON)
                .body(new RegistrationDTO("Kai", "Stlin", "Kaistlin", "Passw0rd!", "kai@test.it"))
                .post("/signup")
                .then()
                .statusCode(201);

        ReviewDTO review = new ReviewDTO();
        review.setTitle("BARRIER X");
        review.setUsername("Kaistlin");
        review.setComment("Amazing");
        review.setUserScore(8);

        authenticatedAs("Kaistlin", "USER")
                .contentType(ContentType.JSON)
                .body(review)
                .post("/review/gameSelected/create")
                .then()
                .statusCode(201);

        // the review-by-title endpoint was removed as dead code (never called by the frontend,
        // which reads reviews from the game's own embedded, size-capped list instead) - so the
        // review is looked up the same way the UI does, via the paginated game search.
        String reviewId =
                authenticatedAs("Kaistlin", "USER")
                        .queryParam("name", "BARRIER X")
                        .queryParam("page", 0)
                        .queryParam("size", 1)
                        .get("/game/searchFilter")
                        .then()
                        .statusCode(200)
                        .body("content[0].reviews.size()", equalTo(1))
                        .body("content[0].reviews[0].username", equalTo("Kaistlin"))
                        .body("content[0].reviews[0].likeCount", equalTo(0))
                        .extract()
                        .path("content[0].reviews[0].id");

        authenticatedAs("Kaistlin", "USER")
                .queryParam("username", "Kaistlin")
                .queryParam("id", reviewId)
                .post("/user/reviewSelected/addLikeReview")
                .then()
                .statusCode(200);

        authenticatedAs("Kaistlin", "USER")
                .queryParam("name", "BARRIER X")
                .queryParam("page", 0)
                .queryParam("size", 1)
                .get("/game/searchFilter")
                .then()
                .statusCode(200)
                .body("content[0].reviews[0].likeCount", equalTo(1));
    }
}
