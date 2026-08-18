package it.unipi.lsmsd.gamehub.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.unipi.lsmsd.gamehub.DTO.ReviewDTO;
import it.unipi.lsmsd.gamehub.model.Game;
import it.unipi.lsmsd.gamehub.model.Review;
import it.unipi.lsmsd.gamehub.model.User;
import it.unipi.lsmsd.gamehub.security.JwtService;
import it.unipi.lsmsd.gamehub.service.IReviewNeo4jService;
import it.unipi.lsmsd.gamehub.support.IntegrationTestSupport;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;

// The Mongo<->Neo4j write-then-rollback pair for review creation lives in
// ReviewController.createGame() itself (see backend-integration-tests skill), so it needs a real
// Mongo instance to prove the rollback actually leaves the store consistent - a mocked unit test
// can only assert the compensating call was invoked, not that it worked.
//
// ReviewNeo4j has no unique constraint to trip like UserNeo4j.username does (see LoginControllerIT
// for that trick), so there's no reliable way to force ReviewNeo4jService.createReview() to fail
// against a *real* Neo4j without pausing the container - instead only that one collaborator is
// mocked here, everything else (Mongo, ReviewService) stays real.
@AutoConfigureMockMvc
class ReviewControllerIT extends IntegrationTestSupport {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JwtService jwtService;

    @MockBean private IReviewNeo4jService reviewNeo4jService;

    private Game seedGame() {
        Game game = new Game();
        game.setName("BARRIER X");
        game.setGenres("Action");
        game.setReleaseDate("Oct 21, 2008");
        game.setAvgScore(0);
        game.setPrice(9.99);
        return mongoTemplate.save(game);
    }

    private User seedUser() {
        User user = new User();
        user.setUsername("Kaistlin");
        user.setName("Kai");
        user.setSurname("Stlin");
        user.setPassword("pwd");
        user.setEmail("kaistlin@test.it");
        return mongoTemplate.save(user);
    }

    private ReviewDTO reviewDTO() {
        ReviewDTO dto = new ReviewDTO();
        dto.setTitle("BARRIER X");
        dto.setUsername("Kaistlin");
        dto.setComment("Amazing");
        dto.setUserScore(8);
        return dto;
    }

    @Test
    void createReview_neo4jSucceeds_persistsReviewInMongo() throws Exception {
        seedGame();
        User user = seedUser();
        when(reviewNeo4jService.createReview(any()))
                .thenReturn(new ResponseEntity<>("created", HttpStatus.CREATED));

        mockMvc.perform(
                        post("/review/gameSelected/create")
                                .header(
                                        "Authorization",
                                        "Bearer "
                                                + jwtService.generateToken(
                                                        user.getUsername(), "USER"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(reviewDTO())))
                .andExpect(status().isCreated());

        List<Review> reviews = mongoTemplate.findAll(Review.class);
        assertThat(reviews).hasSize(1);
        assertThat(reviews.get(0).getUsername()).isEqualTo("Kaistlin");
    }

    @Test
    void createReview_neo4jFails_rollsBackMongoReview() throws Exception {
        seedGame();
        User user = seedUser();
        when(reviewNeo4jService.createReview(any()))
                .thenReturn(new ResponseEntity<>("boom", HttpStatus.INTERNAL_SERVER_ERROR));

        mockMvc.perform(
                        post("/review/gameSelected/create")
                                .header(
                                        "Authorization",
                                        "Bearer "
                                                + jwtService.generateToken(
                                                        user.getUsername(), "USER"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(reviewDTO())))
                .andExpect(status().isOk());

        // A Neo4j failure must roll back the Mongo review that was just created, same as the
        // registration flow does for users - never leave the two stores inconsistent (see
        // CLAUDE.md, "write-then-rollback pattern"). This regression-tests a real bug this test
        // caught: ReviewController.createGame() used to roll back with
        // review2Service.deleteReview(reviewDTO.getId()) instead of
        // review2Service.deleteReview(review.getId()) - reviewDTO.getId() is whatever the client
        // sent (null on a create request, per the Postman example body), not the id Mongo just
        // generated, so the rollback silently did nothing.
        assertThat(mongoTemplate.findAll(Review.class)).isEmpty();
    }

    @Test
    void deleteReview_userWithAdminRole_deletesFromMongo() throws Exception {
        Game game = seedGame();
        User admin = seedUser();
        admin.setRole("ADMIN");
        mongoTemplate.save(admin);

        Review review = new Review();
        review.setTitle(game.getName());
        review.setUsername(admin.getUsername());
        review.setUserScore(7);
        review.setComment("fine");
        review.setLikeCount(0);
        mongoTemplate.save(review);

        when(reviewNeo4jService.removeReview(review.getId()))
                .thenReturn(new ResponseEntity<>("removed", HttpStatus.OK));

        String token = jwtService.generateToken(admin.getUsername(), "ADMIN");

        mockMvc.perform(
                        delete("/review/reviewSelected/delete/{userId}", admin.getId())
                                .param("reviewId", review.getId())
                                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        assertThat(mongoTemplate.findById(review.getId(), Review.class)).isNull();
    }

    @Test
    void deleteReview_userWithoutAdminRole_returnsUnauthorizedAndKeepsReview() throws Exception {
        Game game = seedGame();
        User plainUser = seedUser();

        Review review = new Review();
        review.setTitle(game.getName());
        review.setUsername(plainUser.getUsername());
        review.setUserScore(7);
        review.setComment("fine");
        mongoTemplate.save(review);

        String token = jwtService.generateToken(plainUser.getUsername(), "USER");

        mockMvc.perform(
                        delete("/review/reviewSelected/delete/{userId}", plainUser.getId())
                                .param("reviewId", review.getId())
                                .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());

        assertThat(mongoTemplate.findById(review.getId(), Review.class)).isNotNull();
    }
}
