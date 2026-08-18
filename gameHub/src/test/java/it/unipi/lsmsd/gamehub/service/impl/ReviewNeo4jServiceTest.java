package it.unipi.lsmsd.gamehub.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import it.unipi.lsmsd.gamehub.model.ReviewNeo4j;
import it.unipi.lsmsd.gamehub.repository.ReviewNeo4jRepository;
import it.unipi.lsmsd.gamehub.repository.ReviewRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class ReviewNeo4jServiceTest {

    @Mock private ReviewRepository reviewRepository;
    @Mock private ReviewNeo4jRepository reviewNeo4jRepository;

    @InjectMocks private ReviewNeo4jService reviewNeo4jService;

    @Test
    void getReviewsIngoingLinks_repositorySucceeds_returnsCount() {
        when(reviewNeo4jRepository.findReviewIngoingLinks("r1")).thenReturn(3);

        assertThat(reviewNeo4jService.getReviewsIngoingLinks("r1")).isEqualTo(3);
    }

    @Test
    void getReviewsIngoingLinks_repositoryThrows_returnsNull() {
        when(reviewNeo4jRepository.findReviewIngoingLinks(anyString()))
                .thenThrow(new RuntimeException("boom"));

        assertThat(reviewNeo4jService.getReviewsIngoingLinks("r1")).isNull();
    }

    @Test
    void createReview_repositorySucceeds_returnsCreated() {
        when(reviewNeo4jRepository.save(any(ReviewNeo4j.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ResponseEntity<String> response = reviewNeo4jService.createReview("r1");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void createReview_repositoryThrows_returnsInternalServerError() {
        when(reviewNeo4jRepository.save(any(ReviewNeo4j.class)))
                .thenThrow(new RuntimeException("boom"));

        ResponseEntity<String> response = reviewNeo4jService.createReview("r1");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    void removeReview_repositorySucceeds_returnsOk() {
        ResponseEntity<String> response = reviewNeo4jService.removeReview("r1");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void removeReview_repositoryThrows_returnsInternalServerError() {
        doThrow(new RuntimeException("boom"))
                .when(reviewNeo4jRepository)
                .delete(any(ReviewNeo4j.class));

        ResponseEntity<String> response = reviewNeo4jService.removeReview("r1");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
