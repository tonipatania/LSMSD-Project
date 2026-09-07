package it.unipi.lsmsd.gamehub.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import it.unipi.lsmsd.gamehub.DTO.SuggestedUserDTO;
import it.unipi.lsmsd.gamehub.model.Game;
import it.unipi.lsmsd.gamehub.model.GameNeo4j;
import it.unipi.lsmsd.gamehub.model.UserNeo4j;
import it.unipi.lsmsd.gamehub.repository.GameNeo4jRepository;
import it.unipi.lsmsd.gamehub.repository.GameRepository;
import it.unipi.lsmsd.gamehub.repository.UserNeo4jRepository;
import it.unipi.lsmsd.gamehub.service.IActivityService;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.ResponseEntity;

/**
 * Core-behavior contract: interacting with the platform (following someone, adding a game to the
 * wishlist, ...) is supposed to change what the user sees under "suggested games"/"suggested
 * friends". Both {@link UserNeo4jService#getSuggestedFriends} and {@link
 * GameNeo4jService#getSuggestGames} sit behind a Redis cache (see the {@code gamehub:suggestions:*}
 * keys) with a TTL and no eviction on write. These tests pin down the actual, observable
 * consequence of that: an interaction never invalidates the cache, so a suggestions read that lands
 * inside the TTL window keeps serving the pre-interaction list: the update is eventual (bounded by
 * the TTL), not reactive. Only once the cache entry is gone (expired, or never written) does a read
 * reflect the interaction.
 */
@ExtendWith(MockitoExtension.class)
class SuggestionReactivityTest {

    @Mock private UserNeo4jRepository userNeo4jRepository;
    @Mock private GameNeo4jRepository gameNeo4jRepository;
    @Mock private GameRepository gameRepository;
    @Mock private RedisTemplate<String, Object> redisTemplate;
    @Mock private ValueOperations<String, Object> valueOperations;
    @Mock private Executor suggestionsExecutor;
    @Mock private IActivityService activityService;

    @InjectMocks private UserNeo4jService userNeo4jService;
    @InjectMocks private GameNeo4jService gameNeo4jService;

    /** Runs getSuggestedFriends' CompletableFuture tasks synchronously and deterministically. */
    private void useDirectExecutor() {
        doAnswer(
                        invocation -> {
                            invocation.getArgument(0, Runnable.class).run();
                            return null;
                        })
                .when(suggestionsExecutor)
                .execute(any(Runnable.class));
    }

    private GameNeo4j neo4jGame(String id, String name) {
        return new GameNeo4j(id, name);
    }

    private Game mongoGame(String id, String name) {
        Game game = new Game();
        game.setId(id);
        game.setName(name);
        return game;
    }

    // --- getSuggestGames: does adding a game to the wishlist change the suggestions? ---------

    @Test
    void getSuggestGames_wishlistInteractionWithinCacheTtl_staleSuggestionsSurviveUnchanged() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        String cacheKey = "gamehub:suggestions:games:Lunark";

        // first read: cache empty, computed from the graph and cached
        when(valueOperations.get(cacheKey)).thenReturn(null);
        when(gameNeo4jRepository.findSuggestGames(eq("Lunark"), eq(10)))
                .thenReturn(List.of(neo4jGame("gOld", "Old Suggested Game")));
        when(gameRepository.findAllById(List.of("gOld")))
                .thenReturn(List.of(mongoGame("gOld", "Old Suggested Game")));

        ResponseEntity<List<Game>> before = gameNeo4jService.getSuggestGames("Lunark");
        assertThat(before.getBody()).extracting(Game::getId).containsExactly("gOld");
        verify(valueOperations).set(eq(cacheKey), any(), eq(Duration.ofMinutes(2)));

        // interaction: the user adds a new game to their wishlist, which changes the graph this
        // suggestion is computed from
        when(gameNeo4jRepository.findGameByName("New Game"))
                .thenReturn(neo4jGame("gNew", "New Game"));
        when(userNeo4jRepository.getUser("Lunark")).thenReturn(new UserNeo4j("u1", "Lunark"));

        assertThat(userNeo4jService.addGameToWishlist("Lunark", "New Game")).isTrue();

        // the interaction must not have touched the suggestions cache at all: nothing in
        // addGameToWishlist evicts or overwrites gamehub:suggestions:games:*
        verify(valueOperations, times(1)).set(anyString(), any(), any(Duration.class));
        verify(redisTemplate, never()).delete(anyString());

        // second read, still inside the 2-minute TTL: simulate the entry still being in Redis.
        // Even though the graph changed, a naive re-query would now return something different -
        // set that up too, to prove it is never actually called.
        when(valueOperations.get(cacheKey))
                .thenReturn(List.of(mongoGame("gOld", "Old Suggested Game")));
        // deliberately never consumed: proves below that the cache hit shortcuts the repository
        // call, so it stays stubbed-but-unused instead of overriding the answer above
        lenient()
                .when(gameNeo4jRepository.findSuggestGames(eq("Lunark"), eq(10)))
                .thenReturn(List.of(neo4jGame("gNew", "New Game")));

        ResponseEntity<List<Game>> after = gameNeo4jService.getSuggestGames("Lunark");

        assertThat(after.getBody()).extracting(Game::getId).containsExactly("gOld");
        // only the very first read ever hit the graph; the post-interaction read was served
        // entirely from the stale cache entry
        verify(gameNeo4jRepository, times(1))
                .findSuggestGames(anyString(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void getSuggestGames_cacheExpiredAfterWishlistInteraction_reflectsTheInteraction() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        String cacheKey = "gamehub:suggestions:games:Lunark";

        when(valueOperations.get(cacheKey)).thenReturn(null);
        when(gameNeo4jRepository.findSuggestGames(eq("Lunark"), eq(10)))
                .thenReturn(List.of(neo4jGame("gOld", "Old Suggested Game")));
        when(gameRepository.findAllById(List.of("gOld")))
                .thenReturn(List.of(mongoGame("gOld", "Old Suggested Game")));
        ResponseEntity<List<Game>> before = gameNeo4jService.getSuggestGames("Lunark");
        assertThat(before.getBody()).extracting(Game::getId).containsExactly("gOld");

        when(gameNeo4jRepository.findGameByName("New Game"))
                .thenReturn(neo4jGame("gNew", "New Game"));
        when(userNeo4jRepository.getUser("Lunark")).thenReturn(new UserNeo4j("u1", "Lunark"));
        assertThat(userNeo4jService.addGameToWishlist("Lunark", "New Game")).isTrue();

        // second read after the cache entry has naturally expired (TTL elapsed, or was never
        // written because Redis was unreachable): now the graph is re-queried and the
        // interaction is visible
        when(valueOperations.get(cacheKey)).thenReturn(null);
        when(gameNeo4jRepository.findSuggestGames(eq("Lunark"), eq(10)))
                .thenReturn(List.of(neo4jGame("gNew", "New Game")));
        when(gameRepository.findAllById(List.of("gNew")))
                .thenReturn(List.of(mongoGame("gNew", "New Game")));

        ResponseEntity<List<Game>> after = gameNeo4jService.getSuggestGames("Lunark");

        assertThat(after.getBody()).extracting(Game::getId).containsExactly("gNew");
    }

    // --- getSuggestedFriends: does following someone change the friend suggestions? ----------

    @Test
    void getSuggestedFriends_followInteractionWithinCacheTtl_staleSuggestionsSurviveUnchanged() {
        useDirectExecutor();
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        String cacheKey = "gamehub:suggestions:friends:Lunark";

        SuggestedUserDTO oldSuggestion =
                new SuggestedUserDTO("u2", "OldSuggestion", SuggestedUserDTO.COMMON_FRIENDS, 6, 0);
        when(valueOperations.get(cacheKey)).thenReturn(null);
        when(userNeo4jRepository.findSuggestedFriends(eq("Lunark"), eq(10)))
                .thenReturn(List.of(oldSuggestion));

        List<SuggestedUserDTO> before = userNeo4jService.getSuggestedFriends("Lunark");
        assertThat(before).containsExactly(oldSuggestion);
        verify(valueOperations).set(eq(cacheKey), any(), eq(Duration.ofMinutes(2)));

        // interaction: the user follows someone
        when(userNeo4jRepository.getUser("Lunark")).thenReturn(new UserNeo4j("u1", "Lunark"));
        when(userNeo4jRepository.getUser("Kaistlin")).thenReturn(new UserNeo4j("u3", "Kaistlin"));
        assertThat(userNeo4jService.followUser("Lunark", "Kaistlin")).isTrue();

        // following never touches the suggestions cache
        verify(valueOperations, times(1)).set(anyString(), any(), any(Duration.class));
        verify(redisTemplate, never()).delete(anyString());

        // second read, still inside the 2-minute TTL: the stale entry is what comes back, even
        // though a fresh query (set up here, and proven below to never run) would now differ
        SuggestedUserDTO newSuggestion =
                new SuggestedUserDTO("u4", "NewSuggestion", SuggestedUserDTO.COMMON_FRIENDS, 8, 0);
        when(valueOperations.get(cacheKey)).thenReturn(List.of(oldSuggestion));
        // deliberately never consumed: proves below that the cache hit shortcuts the repository
        // call, so it stays stubbed-but-unused instead of overriding the answer above
        lenient()
                .when(userNeo4jRepository.findSuggestedFriends(eq("Lunark"), eq(10)))
                .thenReturn(List.of(newSuggestion));

        List<SuggestedUserDTO> after = userNeo4jService.getSuggestedFriends("Lunark");

        assertThat(after).containsExactly(oldSuggestion);
        verify(userNeo4jRepository, times(1))
                .findSuggestedFriends(anyString(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void getSuggestedFriends_cacheExpiredAfterFollowInteraction_reflectsTheInteraction() {
        useDirectExecutor();
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        String cacheKey = "gamehub:suggestions:friends:Lunark";

        SuggestedUserDTO oldSuggestion =
                new SuggestedUserDTO("u2", "OldSuggestion", SuggestedUserDTO.COMMON_FRIENDS, 6, 0);
        when(valueOperations.get(cacheKey)).thenReturn(null);
        when(userNeo4jRepository.findSuggestedFriends(eq("Lunark"), eq(10)))
                .thenReturn(List.of(oldSuggestion));
        List<SuggestedUserDTO> before = userNeo4jService.getSuggestedFriends("Lunark");
        assertThat(before).containsExactly(oldSuggestion);

        when(userNeo4jRepository.getUser("Lunark")).thenReturn(new UserNeo4j("u1", "Lunark"));
        when(userNeo4jRepository.getUser("Kaistlin")).thenReturn(new UserNeo4j("u3", "Kaistlin"));
        assertThat(userNeo4jService.followUser("Lunark", "Kaistlin")).isTrue();

        // second read after the cache entry has expired: the graph is re-queried and the
        // interaction's effect (a new friend-of-friend suggestion) becomes visible
        SuggestedUserDTO newSuggestion =
                new SuggestedUserDTO("u4", "NewSuggestion", SuggestedUserDTO.COMMON_FRIENDS, 8, 0);
        when(valueOperations.get(cacheKey)).thenReturn(null);
        when(userNeo4jRepository.findSuggestedFriends(eq("Lunark"), eq(10)))
                .thenReturn(List.of(newSuggestion));

        List<SuggestedUserDTO> after = userNeo4jService.getSuggestedFriends("Lunark");

        assertThat(after).containsExactly(newSuggestion);
    }
}
