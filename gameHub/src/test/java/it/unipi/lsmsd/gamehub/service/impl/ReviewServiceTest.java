package it.unipi.lsmsd.gamehub.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import it.unipi.lsmsd.gamehub.DTO.ReviewDTO;
import it.unipi.lsmsd.gamehub.model.Game;
import it.unipi.lsmsd.gamehub.model.Review;
import it.unipi.lsmsd.gamehub.model.User;
import it.unipi.lsmsd.gamehub.repository.GameRepository;
import it.unipi.lsmsd.gamehub.repository.LoginRepository;
import it.unipi.lsmsd.gamehub.repository.ReviewRepository;
import it.unipi.lsmsd.gamehub.service.IGameService;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

    @Mock private ReviewRepository reviewRepository;
    @Mock private GameRepository gameRepository;
    @Mock private LoginRepository loginRepository;
    @Mock private IGameService gameService;

    @InjectMocks private ReviewService reviewService;

    private Game game(String id, String name) {
        Game game = new Game();
        game.setId(id);
        game.setName(name);
        return game;
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
    void createReview_gameAndUserExist_savesReviewAndSyncsEmbeddedList() {
        when(gameRepository.findByName("BARRIER X")).thenReturn(List.of(game("g1", "BARRIER X")));
        when(loginRepository.findByUsername("Kaistlin")).thenReturn(new User());

        Review result = reviewService.createReview(reviewDto());

        assertThat(result).isNotNull();
        assertThat(result.getTitle()).isEqualTo("BARRIER X");
        assertThat(result.getComment()).isEqualTo("Amazing");
        verify(reviewRepository).save(any(Review.class));
        verify(gameService).updateGameReviewFromScratch(any(Game.class), eq(20));
    }

    @Test
    void createReview_userNotFound_returnsNullWithoutSaving() {
        when(gameRepository.findByName("BARRIER X")).thenReturn(List.of(game("g1", "BARRIER X")));
        when(loginRepository.findByUsername("Kaistlin")).thenReturn(null);

        Review result = reviewService.createReview(reviewDto());

        assertThat(result).isNull();
        verify(reviewRepository, never()).save(any(Review.class));
    }

    @Test
    void createReview_gameRepositoryThrows_returnsNull() {
        when(gameRepository.findByName("BARRIER X")).thenThrow(new RuntimeException("boom"));

        assertThat(reviewService.createReview(reviewDto())).isNull();
    }

    @Test
    void deleteReview_reviewNotFound_returnsNotFound() {
        when(reviewRepository.findById("r1")).thenReturn(Optional.empty());

        ResponseEntity<String> response = reviewService.deleteReview("r1");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        verify(reviewRepository, never()).deleteById("r1");
    }

    @Test
    void deleteReview_reviewFoundAndGameFound_deletesAndSyncsEmbeddedList() {
        Review review = review("r1", "BARRIER X");
        when(reviewRepository.findById("r1")).thenReturn(Optional.of(review));
        when(gameRepository.findByName("BARRIER X")).thenReturn(List.of(game("g1", "BARRIER X")));

        ResponseEntity<String> response = reviewService.deleteReview("r1");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(reviewRepository).deleteById("r1");
        verify(gameService).updateGameReviewFromScratch(any(Game.class), eq(20));
    }

    @Test
    void deleteReview_reviewFoundButNoMatchingGame_deletesWithoutSyncingEmbeddedList() {
        Review review = review("r1", "Unknown Game");
        when(reviewRepository.findById("r1")).thenReturn(Optional.of(review));
        when(gameRepository.findByName("Unknown Game")).thenReturn(Collections.emptyList());

        ResponseEntity<String> response = reviewService.deleteReview("r1");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(reviewRepository).deleteById("r1");
        verify(gameService, never()).updateGameReviewFromScratch(any(Game.class), anyInt());
    }

    @Test
    void deleteReview_repositoryThrows_returnsInternalServerError() {
        when(reviewRepository.findById("r1")).thenThrow(new RuntimeException("boom"));

        ResponseEntity<String> response = reviewService.deleteReview("r1");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
