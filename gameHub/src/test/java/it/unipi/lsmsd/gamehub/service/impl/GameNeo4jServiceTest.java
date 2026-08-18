package it.unipi.lsmsd.gamehub.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import it.unipi.lsmsd.gamehub.model.Game;
import it.unipi.lsmsd.gamehub.model.GameNeo4j;
import it.unipi.lsmsd.gamehub.repository.GameNeo4jRepository;
import it.unipi.lsmsd.gamehub.repository.GameRepository;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class GameNeo4jServiceTest {

    @Mock private GameNeo4jRepository gameNeo4jRepository;
    @Mock private GameRepository gameRepository;
    @Mock private RedisTemplate<String, Object> redisTemplate;
    @Mock private ValueOperations<String, Object> valueOperations;

    @InjectMocks private GameNeo4jService gameNeo4jService;

    private GameNeo4j neo4jGame(String id, String name) {
        return new GameNeo4j(id, name);
    }

    private Game mongoGame(String id, String name) {
        Game game = new Game();
        game.setId(id);
        game.setName(name);
        return game;
    }

    @Test
    void getGamesIngoingLinks_repositorySucceeds_returnsCount() {
        when(gameNeo4jRepository.findGameIngoingLinks("BARRIER X")).thenReturn(5);

        assertThat(gameNeo4jService.getGamesIngoingLinks("BARRIER X")).isEqualTo(5);
    }

    @Test
    void getGamesIngoingLinks_repositoryThrows_returnsNull() {
        when(gameNeo4jRepository.findGameIngoingLinks(anyString()))
                .thenThrow(new RuntimeException("boom"));

        assertThat(gameNeo4jService.getGamesIngoingLinks("BARRIER X")).isNull();
    }

    @Test
    void getSuggestGames_cacheHit_returnsCachedWithoutQueryingNeo4j() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        List<Game> cached = List.of(mongoGame("g1", "Cached Game"));
        when(valueOperations.get("gamehub:suggestions:games:Lunark")).thenReturn(cached);

        ResponseEntity<List<Game>> response = gameNeo4jService.getSuggestGames("Lunark");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(cached);
        verify(gameNeo4jRepository, never()).findSuggestGames(anyString(), anyInt());
    }

    @Test
    void getSuggestGames_wishlistBasedSuggestionsFound_enrichesFromMongoAndCaches() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null);
        when(gameNeo4jRepository.findSuggestGames(eq("Lunark"), eq(10)))
                .thenReturn(List.of(neo4jGame("g1", "Suggested")));
        when(gameRepository.findAllById(List.of("g1")))
                .thenReturn(List.of(mongoGame("g1", "Suggested")));

        ResponseEntity<List<Game>> response = gameNeo4jService.getSuggestGames("Lunark");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).extracting(Game::getId).containsExactly("g1");
        verify(gameNeo4jRepository, never()).findMostWishlistedGames(anyInt());
        verify(valueOperations)
                .set(eq("gamehub:suggestions:games:Lunark"), any(), eq(Duration.ofMinutes(2)));
    }

    @Test
    void getSuggestGames_emptyWishlistSuggestions_fallsBackToMostWishlisted() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null);
        when(gameNeo4jRepository.findSuggestGames(eq("Lunark"), eq(10))).thenReturn(List.of());
        when(gameNeo4jRepository.findMostWishlistedGames(eq(10)))
                .thenReturn(List.of(neo4jGame("g2", "Popular")));
        when(gameRepository.findAllById(List.of("g2")))
                .thenReturn(List.of(mongoGame("g2", "Popular")));

        ResponseEntity<List<Game>> response = gameNeo4jService.getSuggestGames("Lunark");

        assertThat(response.getBody()).extracting(Game::getId).containsExactly("g2");
    }

    @Test
    void getSuggestGames_redisReadFails_stillReturnsFreshSuggestions() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenThrow(new RuntimeException("redis down"));
        when(gameNeo4jRepository.findSuggestGames(eq("Lunark"), eq(10)))
                .thenReturn(List.of(neo4jGame("g1", "Suggested")));
        when(gameRepository.findAllById(List.of("g1")))
                .thenReturn(List.of(mongoGame("g1", "Suggested")));

        ResponseEntity<List<Game>> response = gameNeo4jService.getSuggestGames("Lunark");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).extracting(Game::getId).containsExactly("g1");
    }

    @Test
    void getSuggestGames_neo4jThrows_returnsInternalServerError() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null);
        when(gameNeo4jRepository.findSuggestGames(eq("Lunark"), eq(10)))
                .thenThrow(new RuntimeException("boom"));

        ResponseEntity<List<Game>> response = gameNeo4jService.getSuggestGames("Lunark");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNull();
    }

    @Test
    void removeGame_repositorySucceeds_returnsOk() {
        ResponseEntity<String> response = gameNeo4jService.removeGame("g1");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(gameNeo4jRepository, times(1)).removeGame("g1");
    }

    @Test
    void removeGame_repositoryThrows_returnsInternalServerError() {
        doThrow(new RuntimeException("boom")).when(gameNeo4jRepository).removeGame("g1");

        ResponseEntity<String> response = gameNeo4jService.removeGame("g1");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    void addGame_repositorySucceeds_returnsCreated() {
        ResponseEntity<String> response = gameNeo4jService.addGame("g1", "BARRIER X");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        verify(gameNeo4jRepository).addGame("g1", "BARRIER X");
    }

    @Test
    void addGame_repositoryThrows_returnsInternalServerError() {
        doThrow(new RuntimeException("boom"))
                .when(gameNeo4jRepository)
                .addGame(anyString(), anyString());

        ResponseEntity<String> response = gameNeo4jService.addGame("g1", "BARRIER X");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
