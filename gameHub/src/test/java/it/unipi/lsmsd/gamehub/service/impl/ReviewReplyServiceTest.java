package it.unipi.lsmsd.gamehub.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import it.unipi.lsmsd.gamehub.DTO.ReplyRequestDTO;
import it.unipi.lsmsd.gamehub.model.Review;
import it.unipi.lsmsd.gamehub.model.ReviewReply;
import it.unipi.lsmsd.gamehub.repository.ReviewReplyRepository;
import it.unipi.lsmsd.gamehub.repository.ReviewRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class ReviewReplyServiceTest {

    @Mock private ReviewReplyRepository replyRepository;
    @Mock private ReviewRepository reviewRepository;
    @Mock private MongoTemplate mongoTemplate;

    @InjectMocks private ReviewReplyService replyService;

    private Review reviewBy(String username) {
        Review review = new Review();
        review.setId("r1");
        review.setUsername(username);
        return review;
    }

    @Test
    void createReply_validReplyToSomeoneElsesReview_savesItWithTheTokenUsername() {
        when(reviewRepository.findById("r1")).thenReturn(Optional.of(reviewBy("Kaistlin")));
        when(replyRepository.save(any(ReviewReply.class))).thenAnswer(i -> i.getArgument(0));

        ResponseEntity<Object> response =
                replyService.createReply("Lunark", new ReplyRequestDTO("r1", "  Concordo!  "));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        ReviewReply saved = (ReviewReply) response.getBody();
        assertThat(saved.getUsername()).isEqualTo("Lunark");
        assertThat(saved.getReviewId()).isEqualTo("r1");
        // il testo viene ripulito dagli spazi
        assertThat(saved.getComment()).isEqualTo("Concordo!");
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    void createReply_toOwnReview_isForbiddenAndNothingIsSaved() {
        when(reviewRepository.findById("r1")).thenReturn(Optional.of(reviewBy("Lunark")));

        ResponseEntity<Object> response =
                replyService.createReply("Lunark", new ReplyRequestDTO("r1", "Grazie a me"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        verify(replyRepository, never()).save(any(ReviewReply.class));
    }

    @Test
    void createReply_reviewDoesNotExist_returnsNotFound() {
        when(reviewRepository.findById("missing")).thenReturn(Optional.empty());

        ResponseEntity<Object> response =
                replyService.createReply("Lunark", new ReplyRequestDTO("missing", "Ciao"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        verify(replyRepository, never()).save(any(ReviewReply.class));
    }

    @Test
    void createReply_blankOrMissingFields_returnsBadRequest() {
        assertThat(
                        replyService
                                .createReply("Lunark", new ReplyRequestDTO("r1", "   "))
                                .getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(
                        replyService
                                .createReply("Lunark", new ReplyRequestDTO(null, "Ciao"))
                                .getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(replyService.createReply("Lunark", null).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        verify(reviewRepository, never()).findById(anyString());
    }

    @Test
    void createReply_textTooLong_returnsBadRequest() {
        String tooLong = "x".repeat(ReviewReplyService.MAX_REPLY_LENGTH + 1);

        ResponseEntity<Object> response =
                replyService.createReply("Lunark", new ReplyRequestDTO("r1", tooLong));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(replyRepository, never()).save(any(ReviewReply.class));
    }

    @Test
    void createReply_repositoryThrows_returnsInternalServerError() {
        when(reviewRepository.findById("r1")).thenThrow(new RuntimeException("boom"));

        ResponseEntity<Object> response =
                replyService.createReply("Lunark", new ReplyRequestDTO("r1", "Ciao"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    void deleteReply_byItsAuthor_deletesIt() {
        when(replyRepository.findById("p1"))
                .thenReturn(
                        Optional.of(new ReviewReply("p1", "r1", "Lunark", "Ciao", Instant.now())));

        ResponseEntity<String> response = replyService.deleteReply("p1", "Lunark");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(replyRepository).deleteById("p1");
    }

    @Test
    void deleteReply_bySomeoneElse_isForbidden() {
        when(replyRepository.findById("p1"))
                .thenReturn(
                        Optional.of(new ReviewReply("p1", "r1", "Lunark", "Ciao", Instant.now())));

        ResponseEntity<String> response = replyService.deleteReply("p1", "Kaistlin");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        verify(replyRepository, never()).deleteById(anyString());
    }

    @Test
    void deleteReply_unknownReply_returnsNotFound() {
        when(replyRepository.findById("p1")).thenReturn(Optional.empty());

        assertThat(replyService.deleteReply("p1", "Lunark").getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void getReplies_repositoryThrows_returnsEmptyList() {
        when(replyRepository.findByReviewIdOrderByCreatedAtAsc(anyString(), any()))
                .thenThrow(new RuntimeException("boom"));

        assertThat(replyService.getReplies("r1")).isEmpty();
    }

    @Test
    void countReplies_noIds_returnsEmptyWithoutQuerying() {
        assertThat(replyService.countReplies(List.of())).isEmpty();
        assertThat(replyService.countReplies(null)).isEmpty();
        verify(mongoTemplate, never())
                .aggregate(
                        any(org.springframework.data.mongodb.core.aggregation.Aggregation.class),
                        anyString(),
                        any(Class.class));
    }
}
