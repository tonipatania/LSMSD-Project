package it.unipi.lsmsd.gamehub.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import it.unipi.lsmsd.gamehub.DTO.GameDTO;
import it.unipi.lsmsd.gamehub.model.Game;
import it.unipi.lsmsd.gamehub.model.Review;
import it.unipi.lsmsd.gamehub.repository.GameRepository;
import it.unipi.lsmsd.gamehub.repository.ReviewRepository;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class GameServiceTest {

    @Mock private GameRepository gameRepository;
    @Mock private ReviewRepository reviewRepository;

    @InjectMocks private GameService gameService;

    private Game game(String id, String name) {
        Game game = new Game();
        game.setId(id);
        game.setName(name);
        return game;
    }

    private Review review(String id, int likeCount, String comment) {
        Review review = new Review();
        review.setId(id);
        review.setLikeCount(likeCount);
        review.setComment(comment);
        return review;
    }

    @Test
    void retrieveGamesByParameters_noFiltersProvided_returnsEmptyPageWithoutQuerying() {
        Pageable pageable = PageRequest.of(0, 24);

        Page<Game> result = gameService.retrieveGamesByParameters(null, null, null, pageable);

        assertThat(result.getContent()).isEmpty();
        verify(gameRepository, never()).searchGames(any(), any(), any(), any(Pageable.class));
    }

    @Test
    void retrieveGamesByParameters_nameProvided_delegatesToRepository() {
        Pageable pageable = PageRequest.of(0, 24);
        Page<Game> expected = new PageImpl<>(List.of(game("g1", "BARRIER X")));
        when(gameRepository.searchGames("BARRIER X", null, null, pageable)).thenReturn(expected);

        Page<Game> result =
                gameService.retrieveGamesByParameters("BARRIER X", null, null, pageable);

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void retrieveGamesByParameters_repositoryThrows_returnsEmptyPage() {
        Pageable pageable = PageRequest.of(0, 24);
        when(gameRepository.searchGames(eq("BARRIER X"), any(), any(), eq(pageable)))
                .thenThrow(new RuntimeException("boom"));

        Page<Game> result =
                gameService.retrieveGamesByParameters("BARRIER X", null, null, pageable);

        assertThat(result.getContent()).isEmpty();
    }

    @Test
    void findDistinctGenres_repositorySucceeds_returnsList() {
        when(gameRepository.findDistinctGenres()).thenReturn(List.of("RPG", "Strategy"));

        List<String> result = gameService.findDistinctGenres();

        assertThat(result).containsExactly("RPG", "Strategy");
    }

    @Test
    void findDistinctGenres_repositoryThrows_returnsNull() {
        when(gameRepository.findDistinctGenres()).thenThrow(new RuntimeException("boom"));

        assertThat(gameService.findDistinctGenres()).isNull();
    }

    @Test
    void getGamesWithReviews_repositorySucceeds_returnsGames() {
        when(gameRepository.findGamesWithReviews(any(Pageable.class)))
                .thenReturn(List.of(game("g1", "BARRIER X")));

        List<Game> result = gameService.getGamesWithReviews(20);

        assertThat(result).hasSize(1);
    }

    @Test
    void getGamesWithReviews_repositoryThrows_returnsEmptyList() {
        when(gameRepository.findGamesWithReviews(any(Pageable.class)))
                .thenThrow(new RuntimeException("boom"));

        assertThat(gameService.getGamesWithReviews(20)).isEmpty();
    }

    @Test
    void getAll_repositoryThrows_returnsEmptyPage() {
        Pageable pageable = PageRequest.of(0, 50);
        when(gameRepository.findAll(pageable)).thenThrow(new RuntimeException("boom"));

        Page<Game> result = gameService.getAll(pageable);

        assertThat(result.getContent()).isEmpty();
    }

    @Test
    void updateGameReviewFromScratch_firstReviewHasComment_setsHasReviewsTrue() {
        Game game = game("g1", "BARRIER X");
        game.setReviews(null);
        List<Review> top = List.of(review("r1", 10, "Amazing"), review("r2", 3, ""));
        when(reviewRepository.findByTitleOrderByLikeCountDesc(eq("BARRIER X"), any(Pageable.class)))
                .thenReturn(top);

        List<Review> result = gameService.updateGameReviewFromScratch(game, 20);

        assertThat(result).hasSize(2);
        assertThat(game.isHasReviews()).isTrue();
        verify(gameRepository).save(game);
    }

    @Test
    void updateGameReviewFromScratch_firstReviewHasEmptyComment_setsHasReviewsFalse() {
        Game game = game("g1", "BARRIER X");
        game.setReviews(new ArrayList<>(List.of(review("old", 1, "stale"))));
        List<Review> top = List.of(review("r1", 10, ""));
        when(reviewRepository.findByTitleOrderByLikeCountDesc(eq("BARRIER X"), any(Pageable.class)))
                .thenReturn(top);

        gameService.updateGameReviewFromScratch(game, 20);

        assertThat(game.isHasReviews()).isFalse();
    }

    @Test
    void updateGameReviewFromScratch_noReviewsFound_setsHasReviewsFalse() {
        Game game = game("g1", "BARRIER X");
        when(reviewRepository.findByTitleOrderByLikeCountDesc(eq("BARRIER X"), any(Pageable.class)))
                .thenReturn(Collections.emptyList());

        List<Review> result = gameService.updateGameReviewFromScratch(game, 20);

        assertThat(result).isEmpty();
        assertThat(game.isHasReviews()).isFalse();
    }

    @Test
    void updateGameReviewFromScratch_repositoryThrows_returnsNull() {
        Game game = game("g1", "BARRIER X");
        when(reviewRepository.findByTitleOrderByLikeCountDesc(anyString(), any(Pageable.class)))
                .thenThrow(new RuntimeException("boom"));

        assertThat(gameService.updateGameReviewFromScratch(game, 20)).isNull();
    }

    @Test
    void updateGameEmbeddedReview_sortsByLikeCountDescendingAndSaves() {
        Game game = game("g1", "BARRIER X");
        game.setReviews(new ArrayList<>(List.of(review("r1", 2, "a"), review("r2", 9, "b"))));

        List<Review> result = gameService.updateGameEmbeddedReview(game);

        assertThat(result).extracting(Review::getId).containsExactly("r2", "r1");
        verify(gameRepository).save(game);
    }

    @Test
    void createGame_gameNameAlreadyExists_returnsConflict() {
        when(gameRepository.findByName("BARRIER X")).thenReturn(List.of(game("g1", "BARRIER X")));
        GameDTO dto =
                new GameDTO(
                        null, "BARRIER X", null, null, null, null, null, null, null, null, null);

        ResponseEntity<String> response = gameService.createGame(dto);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        verify(gameRepository, never()).save(any(Game.class));
    }

    @Test
    void createGame_newGame_savesAndReturnsCreatedWithGeneratedId() {
        when(gameRepository.findByName("BARRIER X")).thenReturn(Collections.emptyList());
        when(gameRepository.save(any(Game.class)))
                .thenAnswer(
                        invocation -> {
                            Game saved = invocation.getArgument(0);
                            saved.setId("newId");
                            return saved;
                        });
        GameDTO dto =
                new GameDTO(
                        null,
                        "BARRIER X",
                        "Oct 21, 2008",
                        40.0,
                        "about",
                        "English",
                        "dev",
                        "pub",
                        "Action",
                        "Strategy",
                        0);

        ResponseEntity<String> response = gameService.createGame(dto);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isEqualTo("newId");
    }

    @Test
    void createGame_repositoryThrows_returnsInternalServerError() {
        when(gameRepository.findByName(anyString())).thenReturn(Collections.emptyList());
        when(gameRepository.save(any(Game.class))).thenThrow(new RuntimeException("boom"));
        GameDTO dto =
                new GameDTO(
                        null, "BARRIER X", null, null, null, null, null, null, null, null, null);

        ResponseEntity<String> response = gameService.createGame(dto);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    void countGameDocument_repositorySucceeds_returnsCount() {
        when(gameRepository.count()).thenReturn(42L);

        assertThat(gameService.countGameDocument()).isEqualTo(42L);
    }

    @Test
    void countGameDocument_repositoryThrows_returnsMinusOne() {
        when(gameRepository.count()).thenThrow(new RuntimeException("boom"));

        assertThat(gameService.countGameDocument()).isEqualTo(-1L);
    }

    @Test
    void deleteGame_gameNotFound_returnsNotFound() {
        when(gameRepository.findById("g1")).thenReturn(Optional.empty());

        ResponseEntity<String> response = gameService.deleteGame("g1");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        verify(gameRepository, never()).deleteById(anyString());
    }

    @Test
    void deleteGame_gameFound_deletesAndReturnsOk() {
        when(gameRepository.findById("g1")).thenReturn(Optional.of(game("g1", "BARRIER X")));

        ResponseEntity<String> response = gameService.deleteGame("g1");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(gameRepository).deleteById("g1");
    }

    @Test
    void deleteGame_repositoryThrows_returnsInternalServerError() {
        when(gameRepository.findById("g1")).thenThrow(new RuntimeException("boom"));

        ResponseEntity<String> response = gameService.deleteGame("g1");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
