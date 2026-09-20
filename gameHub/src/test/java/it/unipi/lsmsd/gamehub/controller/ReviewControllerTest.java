package it.unipi.lsmsd.gamehub.controller;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.unipi.lsmsd.gamehub.DTO.ReplyRequestDTO;
import it.unipi.lsmsd.gamehub.DTO.ReviewDTO;
import it.unipi.lsmsd.gamehub.model.Review;
import it.unipi.lsmsd.gamehub.model.ReviewReply;
import it.unipi.lsmsd.gamehub.security.JwtService;
import it.unipi.lsmsd.gamehub.security.SecurityConfig;
import it.unipi.lsmsd.gamehub.security.TokenBlacklistService;
import it.unipi.lsmsd.gamehub.service.IActivityService;
import it.unipi.lsmsd.gamehub.service.IReviewNeo4jService;
import it.unipi.lsmsd.gamehub.service.IReviewReplyService;
import it.unipi.lsmsd.gamehub.service.IReviewService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@ExtendWith(SpringExtension.class)
@WebMvcTest(ReviewController.class)
@AutoConfigureMockMvc(addFilters = false)
// @WebMvcTest doesn't pick up SecurityConfig on its own; without this @Import,
// @EnableMethodSecurity's infrastructure never gets registered and @PreAuthorize on
// ReviewController's admin endpoint silently has no effect in this test context
@Import(SecurityConfig.class)
class ReviewControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private IReviewService review2Service;
    @MockBean private IReviewNeo4jService reviewNeo4jService;
    @MockBean private IActivityService activityService;
    @MockBean private IReviewReplyService replyService;

    // see LoginControllerTest for why this is required even with addFilters = false
    @MockBean private JwtService jwtService;

    // JwtAuthenticationFilter (wired through SecurityConfig) also needs a TokenBlacklistService
    @MockBean private TokenBlacklistService tokenBlacklistService;

    // With addFilters = false, JwtAuthenticationFilter never runs, so
    // SecurityMockMvcRequestPostProcessors.authentication() (which only bridges into
    // SecurityContextHolder via a filter) has no effect here - set the real SecurityContextHolder
    // directly instead, matching the Authentication JwtAuthenticationFilter builds in production
    // (a plain-String principal with a single ROLE_* authority from the "role" claim). MockMvc
    // dispatches synchronously on this thread, so @PreAuthorize/@AuthenticationPrincipal in the
    // controller see it; @AfterEach clears it so it can't leak into the next test.
    private static RequestPostProcessor asUser(String username) {
        return request -> {
            SecurityContextHolder.getContext()
                    .setAuthentication(
                            new UsernamePasswordAuthenticationToken(
                                    username,
                                    null,
                                    List.of(new SimpleGrantedAuthority("ROLE_USER"))));
            return request;
        };
    }

    private static RequestPostProcessor asAdmin(String username) {
        return request -> {
            SecurityContextHolder.getContext()
                    .setAuthentication(
                            new UsernamePasswordAuthenticationToken(
                                    username,
                                    null,
                                    List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
            return request;
        };
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

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
                                .with(asUser("Kaistlin"))
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
                                .with(asUser("Kaistlin"))
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
                                .with(asUser("Kaistlin"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk());

        verify(review2Service).deleteReview("r1");
    }

    @Test
    void deleteReview_callerNotAdmin_doesNotDeleteReview() {
        // ExceptionTranslationFilter (the piece that turns AccessDeniedException into an HTTP 403)
        // is part of the Spring Security filter chain, which addFilters = false disables - so this
        // slice test can only observe @PreAuthorize denying access as the exception itself
        // propagating out of the DispatcherServlet, not as a 403 response. The real HTTP-level
        // translation is covered by ReviewControllerIT, which runs with the full filter chain.
        assertThatThrownBy(
                        () ->
                                mockMvc.perform(
                                        delete("/review/reviewSelected/delete/u1")
                                                .with(asUser("someone"))
                                                .param("reviewId", "r1")))
                .hasCauseInstanceOf(AccessDeniedException.class);

        verify(review2Service, never()).deleteReview(anyString());
    }

    @Test
    void deleteReview_adminAndMongoDeleteSucceeds_alsoDeletesFromNeo4j() throws Exception {
        when(review2Service.deleteReview("r1"))
                .thenReturn(new ResponseEntity<>("review deleted", HttpStatus.OK));
        when(reviewNeo4jService.removeReview("r1"))
                .thenReturn(new ResponseEntity<>("remove correct", HttpStatus.OK));

        mockMvc.perform(
                        delete("/review/reviewSelected/delete/u1")
                                .with(asAdmin("someone"))
                                .param("reviewId", "r1"))
                .andExpect(status().isOk());

        verify(reviewNeo4jService).removeReview("r1");
    }

    @Test
    void deleteReview_mongoDeleteFails_doesNotTouchNeo4j() throws Exception {
        when(review2Service.deleteReview("r1"))
                .thenReturn(new ResponseEntity<>("Review not found", HttpStatus.NOT_FOUND));

        mockMvc.perform(
                        delete("/review/reviewSelected/delete/u1")
                                .with(asAdmin("someone"))
                                .param("reviewId", "r1"))
                .andExpect(status().isNotFound());

        verify(reviewNeo4jService, never()).removeReview(anyString());
    }

    // --- risposte alle recensioni ------------------------------------------------------------

    @Test
    void createReply_asAuthenticatedUser_usesTheTokenUsernameAndReturnsTheResponse()
            throws Exception {
        ReviewReply reply = new ReviewReply("p1", "r1", "Lunark", "Concordo", Instant.now());
        when(replyService.createReply(anyString(), any(ReplyRequestDTO.class)))
                .thenReturn(new ResponseEntity<>(reply, HttpStatus.CREATED));

        mockMvc.perform(
                        post("/review/reply")
                                .with(asUser("Lunark"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(
                                                new ReplyRequestDTO("r1", "Concordo"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("Lunark"))
                .andExpect(jsonPath("$.reviewId").value("r1"));

        verify(replyService).createReply(eq("Lunark"), any(ReplyRequestDTO.class));
    }

    @Test
    void createReply_ownReview_propagatesForbidden() throws Exception {
        when(replyService.createReply(anyString(), any(ReplyRequestDTO.class)))
                .thenReturn(
                        new ResponseEntity<>(
                                "you cannot reply to your own review", HttpStatus.FORBIDDEN));

        mockMvc.perform(
                        post("/review/reply")
                                .with(asUser("Kaistlin"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(
                                                new ReplyRequestDTO("r1", "Grazie a me"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void getReplies_returnsTheThread() throws Exception {
        when(replyService.getReplies("r1"))
                .thenReturn(
                        List.of(new ReviewReply("p1", "r1", "Lunark", "Concordo", Instant.now())));

        mockMvc.perform(get("/review/replies").param("reviewId", "r1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].comment").value("Concordo"));
    }

    @Test
    void getReplyCounts_returnsCountsById() throws Exception {
        when(replyService.countReplies(List.of("r1", "r2"))).thenReturn(Map.of("r1", 3L));

        mockMvc.perform(get("/review/replies/counts").param("ids", "r1", "r2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.r1").value(3));
    }

    @Test
    void deleteReply_usesTheTokenUsername() throws Exception {
        when(replyService.deleteReply("p1", "Lunark"))
                .thenReturn(new ResponseEntity<>("reply deleted", HttpStatus.OK));

        mockMvc.perform(delete("/review/reply/p1").with(asUser("Lunark")))
                .andExpect(status().isOk());

        verify(replyService).deleteReply("p1", "Lunark");
    }
}
