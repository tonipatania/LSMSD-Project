package it.unipi.lsmsd.gamehub.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.unipi.lsmsd.gamehub.DTO.ReviewDTO;
import it.unipi.lsmsd.gamehub.model.Review;
import it.unipi.lsmsd.gamehub.security.JwtService;
import it.unipi.lsmsd.gamehub.service.ILoginService;
import it.unipi.lsmsd.gamehub.service.IReviewNeo4jService;
import it.unipi.lsmsd.gamehub.service.IReviewService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;

@ExtendWith(SpringExtension.class)
@WebMvcTest(ReviewController.class)
@AutoConfigureMockMvc(addFilters = false)
class ReviewControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private IReviewService review2Service;
    @MockBean private ILoginService iLoginService;
    @MockBean private IReviewNeo4jService reviewNeo4jService;

    // see LoginControllerTest for why this is required even with addFilters = false
    @MockBean private JwtService jwtService;

    private Review review(String id, String title) {
        Review review = new Review();
        review.setId(id);
        review.setTitle(title);
        return review;
    }

    private ReviewDTO reviewDto() {
        return new ReviewDTO(null, "BARRIER X", 8, "Amazing", "Kaistlin");
    }

    @Test
    void createReview_mongoCreationFails_returnsOkWithErrorMessage() throws Exception {
        when(review2Service.createReview(any(ReviewDTO.class))).thenReturn(null);

        mockMvc.perform(
                        post("/review/gameSelected/create")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(reviewDto())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value("error in review creation"));

        verify(reviewNeo4jService, never()).createReview(anyString());
    }

    @Test
    void createReview_mongoAndNeo4jSucceed_returnsCreated() throws Exception {
        when(review2Service.createReview(any(ReviewDTO.class)))
                .thenReturn(review("r1", "BARRIER X"));
        when(reviewNeo4jService.createReview("r1"))
                .thenReturn(new ResponseEntity<>("corrected created review", HttpStatus.CREATED));

        mockMvc.perform(
                        post("/review/gameSelected/create")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(reviewDto())))
                .andExpect(status().isCreated());
    }

    @Test
    void createReview_neo4jFails_rollsBackByDeletingReviewUsingMongoAssignedId() throws Exception {
        // No client-supplied id, matching real create requests (see the create-review Postman
        // example in the controller): the rollback must use the id Mongo actually assigned
        // (review.getId() == "r1"), not reviewDTO.getId(), which is null here.
        ReviewDTO dto = new ReviewDTO(null, "BARRIER X", 8, "Amazing", "Kaistlin");
        when(review2Service.createReview(any(ReviewDTO.class)))
                .thenReturn(review("r1", "BARRIER X"));
        when(reviewNeo4jService.createReview("r1"))
                .thenReturn(new ResponseEntity<>("error", HttpStatus.INTERNAL_SERVER_ERROR));
        when(review2Service.deleteReview(anyString()))
                .thenReturn(new ResponseEntity<>("review deleted", HttpStatus.OK));

        mockMvc.perform(
                        post("/review/gameSelected/create")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk());

        verify(review2Service).deleteReview("r1");
    }

    @Test
    void deleteReview_callerNotAdmin_doesNotDeleteReview() throws Exception {
        when(iLoginService.roleUser("u1"))
                .thenReturn(new ResponseEntity<>("forbidden", HttpStatus.UNAUTHORIZED));

        mockMvc.perform(delete("/review/reviewSelected/delete/u1").param("reviewId", "r1"))
                .andExpect(status().isUnauthorized());

        verify(review2Service, never()).deleteReview(anyString());
    }

    @Test
    void deleteReview_adminAndMongoDeleteSucceeds_alsoDeletesFromNeo4j() throws Exception {
        when(iLoginService.roleUser("u1")).thenReturn(new ResponseEntity<>("ADMIN", HttpStatus.OK));
        when(review2Service.deleteReview("r1"))
                .thenReturn(new ResponseEntity<>("review deleted", HttpStatus.OK));
        when(reviewNeo4jService.removeReview("r1"))
                .thenReturn(new ResponseEntity<>("remove correct", HttpStatus.OK));

        mockMvc.perform(delete("/review/reviewSelected/delete/u1").param("reviewId", "r1"))
                .andExpect(status().isOk());

        verify(reviewNeo4jService).removeReview("r1");
    }

    @Test
    void deleteReview_mongoDeleteFails_doesNotTouchNeo4j() throws Exception {
        when(iLoginService.roleUser("u1")).thenReturn(new ResponseEntity<>("ADMIN", HttpStatus.OK));
        when(review2Service.deleteReview("r1"))
                .thenReturn(new ResponseEntity<>("Review not found", HttpStatus.NOT_FOUND));

        mockMvc.perform(delete("/review/reviewSelected/delete/u1").param("reviewId", "r1"))
                .andExpect(status().isNotFound());

        verify(reviewNeo4jService, never()).removeReview(anyString());
    }
}
