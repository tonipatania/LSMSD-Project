package it.unipi.lsmsd.gamehub.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import it.unipi.lsmsd.gamehub.DTO.SuggestedUserDTO;
import it.unipi.lsmsd.gamehub.model.Game;
import it.unipi.lsmsd.gamehub.model.GameNeo4j;
import it.unipi.lsmsd.gamehub.model.Review;
import it.unipi.lsmsd.gamehub.model.UserNeo4j;
import it.unipi.lsmsd.gamehub.repository.GameNeo4jRepository;
import it.unipi.lsmsd.gamehub.repository.GameRepository;
import it.unipi.lsmsd.gamehub.repository.LoginRepository;
import it.unipi.lsmsd.gamehub.repository.ReviewRepository;
import it.unipi.lsmsd.gamehub.repository.UserNeo4jRepository;
import it.unipi.lsmsd.gamehub.service.IGameService;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class UserNeo4jServiceTest {

    @Mock private UserNeo4jRepository userNeo4jRepository;
    @Mock private LoginRepository loginRepository;
    @Mock private GameRepository gameRepository;
    @Mock private GameNeo4jRepository gameNeo4jRepository;
    @Mock private ReviewRepository reviewRepository;
    @Mock private RedisTemplate<String, Object> redisTemplate;
    @Mock private ValueOperations<String, Object> valueOperations;
    @Mock private Executor suggestionsExecutor;
    @Mock private IGameService gameService;

    @InjectMocks private UserNeo4jService userNeo4jService;

    /**
     * Runs CompletableFuture.supplyAsync tasks synchronously so getSuggestedFriends is
     * deterministic.
     */
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

    private Game mongoGame(String id, String name, double price, String releaseDate) {
        Game game = mongoGame(id, name);
        game.setPrice(price);
        game.setReleaseDate(releaseDate);
        return game;
    }

    // --- getUserWishlist / resolveWishlistOwner --------------------------------------------

    @Test
    void getUserWishlist_noFriendUsername_returnsOwnWishlist() {
        when(userNeo4jRepository.findByUsername("Lunark"))
                .thenReturn(List.of(neo4jGame("g1", "BARRIER X")));
        when(gameRepository.findAllById(List.of("g1")))
                .thenReturn(List.of(mongoGame("g1", "BARRIER X")));

        List<Game> result = userNeo4jService.getUserWishlist("Lunark", null);

        assertThat(result).extracting(Game::getId).containsExactly("g1");
        verify(userNeo4jRepository).findByUsername("Lunark");
    }

    @Test
    void getUserWishlist_blankFriendUsername_treatedAsOwnWishlist() {
        when(userNeo4jRepository.findByUsername("Lunark")).thenReturn(List.of());

        userNeo4jService.getUserWishlist("Lunark", "   ");

        verify(userNeo4jRepository).findByUsername("Lunark");
    }

    @Test
    void getUserWishlist_friendUsernameProvided_returnsFriendWishlist() {
        when(userNeo4jRepository.findByUsername("Kaistlin")).thenReturn(List.of());

        userNeo4jService.getUserWishlist("Lunark", "Kaistlin");

        verify(userNeo4jRepository).findByUsername("Kaistlin");
    }

    @Test
    void getUserWishlist_mongoDocumentMissing_returnsPlaceholderWithIdAndName() {
        when(userNeo4jRepository.findByUsername("Lunark"))
                .thenReturn(List.of(neo4jGame("ghost", "Deleted Game")));
        when(gameRepository.findAllById(List.of("ghost"))).thenReturn(List.of());

        List<Game> result = userNeo4jService.getUserWishlist("Lunark", null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo("ghost");
        assertThat(result.get(0).getName()).isEqualTo("Deleted Game");
    }

    @Test
    void getUserWishlist_repositoryThrows_returnsNull() {
        when(userNeo4jRepository.findByUsername(anyString()))
                .thenThrow(new RuntimeException("boom"));

        assertThat(userNeo4jService.getUserWishlist("Lunark", null)).isNull();
    }

    // --- getUserWishlistPage: sorting and release-date parsing ------------------------------

    @Test
    void getUserWishlistPage_sortByPrice_ordersAscendingThenByName() {
        when(userNeo4jRepository.findByUsername("Lunark"))
                .thenReturn(List.of(neo4jGame("g1", "Expensive"), neo4jGame("g2", "Cheap")));
        when(gameRepository.findAllById(List.of("g1", "g2")))
                .thenReturn(
                        List.of(
                                mongoGame("g1", "Expensive", 60.0, null),
                                mongoGame("g2", "Cheap", 10.0, null)));

        Page<Game> page =
                userNeo4jService.getUserWishlistPage(
                        "Lunark", null, PageRequest.of(0, 10), "price", false);

        assertThat(page.getContent()).extracting(Game::getId).containsExactly("g2", "g1");
    }

    @Test
    void getUserWishlistPage_sortByRelease_unparsableDateSortsLast() {
        when(userNeo4jRepository.findByUsername("Lunark"))
                .thenReturn(List.of(neo4jGame("g1", "Recent"), neo4jGame("g2", "Broken Date")));
        when(gameRepository.findAllById(List.of("g1", "g2")))
                .thenReturn(
                        List.of(
                                mongoGame("g1", "Recent", 0, "Oct 21, 2020"),
                                mongoGame("g2", "Broken Date", 0, "not-a-date")));

        Page<Game> page =
                userNeo4jService.getUserWishlistPage(
                        "Lunark", null, PageRequest.of(0, 10), "release", false);

        // most recent first; the unparsable date falls back to Long.MIN_VALUE and sorts last
        assertThat(page.getContent()).extracting(Game::getId).containsExactly("g1", "g2");
    }

    @Test
    void getUserWishlistPage_onlyCommonForAnotherProfile_filtersToSharedGamesById() {
        when(userNeo4jRepository.findByUsername("Kaistlin"))
                .thenReturn(List.of(neo4jGame("g1", "Shared"), neo4jGame("g2", "NotShared")));
        when(gameRepository.findAllById(List.of("g1", "g2")))
                .thenReturn(List.of(mongoGame("g1", "Shared"), mongoGame("g2", "NotShared")));
        when(userNeo4jRepository.findCommonWishlistGames("Lunark", "Kaistlin"))
                .thenReturn(List.of(neo4jGame("g1", "Shared")));

        Page<Game> page =
                userNeo4jService.getUserWishlistPage(
                        "Lunark", "Kaistlin", PageRequest.of(0, 10), "name", true);

        assertThat(page.getContent()).extracting(Game::getId).containsExactly("g1");
    }

    @Test
    void getUserWishlistPage_pageableBeyondContent_returnsEmptyPageWithCorrectTotal() {
        when(userNeo4jRepository.findByUsername("Lunark"))
                .thenReturn(List.of(neo4jGame("g1", "Only Game")));
        when(gameRepository.findAllById(List.of("g1")))
                .thenReturn(List.of(mongoGame("g1", "Only Game")));

        Page<Game> page =
                userNeo4jService.getUserWishlistPage(
                        "Lunark", null, PageRequest.of(5, 10), "name", false);

        assertThat(page.getContent()).isEmpty();
        assertThat(page.getTotalElements()).isEqualTo(1);
    }

    @Test
    void getUserWishlistPage_repositoryThrows_returnsEmptyPage() {
        when(userNeo4jRepository.findByUsername(anyString()))
                .thenThrow(new RuntimeException("boom"));

        Page<Game> page =
                userNeo4jService.getUserWishlistPage(
                        "Lunark", null, PageRequest.of(0, 10), "name", false);

        assertThat(page.getContent()).isEmpty();
    }

    // --- getCommonWishlistGames --------------------------------------------------------------

    @Test
    void getCommonWishlistGames_friendUsernameNull_returnsEmptyWithoutQuerying() {
        assertThat(userNeo4jService.getCommonWishlistGames("Lunark", null)).isEmpty();
        verify(userNeo4jRepository, never()).findCommonWishlistGames(anyString(), anyString());
    }

    @Test
    void getCommonWishlistGames_sameUserAsFriend_returnsEmptyWithoutQuerying() {
        assertThat(userNeo4jService.getCommonWishlistGames("Lunark", "Lunark")).isEmpty();
        verify(userNeo4jRepository, never()).findCommonWishlistGames(anyString(), anyString());
    }

    @Test
    void getCommonWishlistGames_differentFriend_delegatesToRepository() {
        when(userNeo4jRepository.findCommonWishlistGames("Lunark", "Kaistlin"))
                .thenReturn(List.of(neo4jGame("g1", "Shared")));

        assertThat(userNeo4jService.getCommonWishlistGames("Lunark", "Kaistlin")).hasSize(1);
    }

    @Test
    void getCommonWishlistGames_repositoryThrows_returnsEmptyList() {
        when(userNeo4jRepository.findCommonWishlistGames(anyString(), anyString()))
                .thenThrow(new RuntimeException("boom"));

        assertThat(userNeo4jService.getCommonWishlistGames("Lunark", "Kaistlin")).isEmpty();
    }

    // --- addGameToWishlist / deleteGameToWishlist --------------------------------------------

    @Test
    void addGameToWishlist_gameAndUserExist_linksAndReturnsTrue() {
        when(gameNeo4jRepository.findGameByName("BARRIER X"))
                .thenReturn(neo4jGame("g1", "BARRIER X"));
        when(userNeo4jRepository.getUser("Lunark")).thenReturn(new UserNeo4j("u1", "Lunark"));

        assertThat(userNeo4jService.addGameToWishlist("Lunark", "BARRIER X")).isTrue();
        verify(userNeo4jRepository).addGameToUser("Lunark", "BARRIER X");
    }

    @Test
    void addGameToWishlist_gameMissing_returnsFalseWithoutLinking() {
        when(gameNeo4jRepository.findGameByName("Unknown")).thenReturn(null);

        assertThat(userNeo4jService.addGameToWishlist("Lunark", "Unknown")).isFalse();
        verify(userNeo4jRepository, never()).addGameToUser(anyString(), anyString());
    }

    @Test
    void addGameToWishlist_repositoryThrows_returnsNull() {
        when(gameNeo4jRepository.findGameByName(anyString()))
                .thenThrow(new RuntimeException("boom"));

        assertThat(userNeo4jService.addGameToWishlist("Lunark", "BARRIER X")).isNull();
    }

    @Test
    void deleteGameToWishlist_gamePresentInWishlist_removesAndReturnsTrue() {
        when(userNeo4jRepository.findByUsername("Lunark"))
                .thenReturn(List.of(neo4jGame("g1", "BARRIER X")));

        assertThat(userNeo4jService.deleteGameToWishlist("Lunark", "BARRIER X")).isTrue();
        verify(userNeo4jRepository).deleteGameFromUser("Lunark", "BARRIER X");
    }

    @Test
    void deleteGameToWishlist_gameNotInWishlist_returnsFalse() {
        when(userNeo4jRepository.findByUsername("Lunark")).thenReturn(List.of());

        assertThat(userNeo4jService.deleteGameToWishlist("Lunark", "BARRIER X")).isFalse();
        verify(userNeo4jRepository, never()).deleteGameFromUser(anyString(), anyString());
    }

    @Test
    void deleteGameToWishlist_repositoryThrows_returnsNull() {
        when(userNeo4jRepository.findByUsername(anyString()))
                .thenThrow(new RuntimeException("boom"));

        assertThat(userNeo4jService.deleteGameToWishlist("Lunark", "BARRIER X")).isNull();
    }

    // --- getSuggestedFriends: cache + three-level cascade ------------------------------------

    @Test
    void getSuggestedFriends_cacheHit_returnsCachedWithoutQueryingNeo4j() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        List<SuggestedUserDTO> cached =
                List.of(new SuggestedUserDTO("u2", "Cached", "POPULAR", 0, 5));
        when(valueOperations.get("gamehub:suggestions:friends:Lunark")).thenReturn(cached);

        List<SuggestedUserDTO> result = userNeo4jService.getSuggestedFriends("Lunark");

        assertThat(result).isEqualTo(cached);
        verify(userNeo4jRepository, never())
                .findSuggestedFriends(anyString(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void getSuggestedFriends_level1HasResults_returnsLevel1WithoutFallback() {
        useDirectExecutor();
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null);
        SuggestedUserDTO level1 = new SuggestedUserDTO("u2", "Friend", "COMMON_FRIENDS", 6, 0);
        when(userNeo4jRepository.findSuggestedFriends(eq("Lunark"), eq(10)))
                .thenReturn(List.of(level1));

        List<SuggestedUserDTO> result = userNeo4jService.getSuggestedFriends("Lunark");

        // level1 and level2 queries are dispatched concurrently regardless of outcome (see the
        // comment in UserNeo4jService.getSuggestedFriends on why), so this only asserts that a
        // non-empty level1 result wins -- it deliberately does not assert level2 was skipped.
        assertThat(result).containsExactly(level1);
    }

    @Test
    void getSuggestedFriends_level1Empty_fallsBackToLevel2() {
        useDirectExecutor();
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null);
        when(userNeo4jRepository.findSuggestedFriends(eq("Lunark"), eq(10))).thenReturn(List.of());
        SuggestedUserDTO level2 =
                new SuggestedUserDTO("u3", "SimilarTaste", "SIMILAR_TASTES", 3, 0);
        when(userNeo4jRepository.findUsersWithSimilarTastes(eq("Lunark"), eq(10)))
                .thenReturn(List.of(level2));

        List<SuggestedUserDTO> result = userNeo4jService.getSuggestedFriends("Lunark");

        assertThat(result).containsExactly(level2);
    }

    @Test
    void getSuggestedFriends_bothLevelsEmpty_fallsBackToMostFollowedExcludingSelfAndFollowed() {
        useDirectExecutor();
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null);
        when(userNeo4jRepository.findSuggestedFriends(eq("Lunark"), eq(10))).thenReturn(List.of());
        when(userNeo4jRepository.findUsersWithSimilarTastes(eq("Lunark"), eq(10)))
                .thenReturn(List.of());
        when(userNeo4jRepository.findFollowedUsers("Lunark"))
                .thenReturn(List.of(new UserNeo4j("u4", "AlreadyFollowed")));
        SuggestedUserDTO popularSelf = new SuggestedUserDTO("Lunark", "Lunark", "POPULAR", 0, 20);
        SuggestedUserDTO popularFollowed =
                new SuggestedUserDTO("u4", "AlreadyFollowed", "POPULAR", 0, 15);
        SuggestedUserDTO popularOther = new SuggestedUserDTO("u5", "Popular", "POPULAR", 0, 10);
        when(userNeo4jRepository.findMostFollowedUsers(100))
                .thenReturn(List.of(popularSelf, popularFollowed, popularOther));

        List<SuggestedUserDTO> result = userNeo4jService.getSuggestedFriends("Lunark");

        assertThat(result).containsExactly(popularOther);
    }

    @Test
    void getSuggestedFriends_repositoryThrows_returnsNull() {
        useDirectExecutor();
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null);
        when(userNeo4jRepository.findSuggestedFriends(eq("Lunark"), eq(10)))
                .thenThrow(new RuntimeException("boom"));
        when(userNeo4jRepository.findUsersWithSimilarTastes(eq("Lunark"), eq(10)))
                .thenReturn(List.of());

        assertThat(userNeo4jService.getSuggestedFriends("Lunark")).isNull();
    }

    // --- addLikeToReview: Neo4j + Mongo + embedded-review sync -------------------------------

    private Review reviewWithLikes(String id, int likeCount) {
        Review review = new Review();
        review.setId(id);
        review.setTitle("BARRIER X");
        review.setLikeCount(likeCount);
        return review;
    }

    @Test
    void addLikeToReview_likeAlreadyPresentInNeo4j_returnsFalseWithoutTouchingMongo() {
        when(userNeo4jRepository.addLikeToReview("Lunark", "r1")).thenReturn(true);

        assertThat(userNeo4jService.addLikeToReview("Lunark", "r1")).isFalse();
        verify(reviewRepository, never()).findById(anyString());
    }

    @Test
    void
            addLikeToReview_newLikeButBelowLeastLikedEmbedded_incrementsMongoWithoutTouchingEmbeddedList() {
        when(userNeo4jRepository.addLikeToReview("Lunark", "r1")).thenReturn(false);
        Review review = reviewWithLikes("r1", 1);
        when(reviewRepository.findById("r1")).thenReturn(Optional.of(review));
        Game game = mongoGame("g1", "BARRIER X");
        game.setReviews(List.of(reviewWithLikes("embedded", 50)));
        when(gameRepository.findByName("BARRIER X")).thenReturn(List.of(game));

        Boolean result = userNeo4jService.addLikeToReview("Lunark", "r1");

        assertThat(result).isTrue();
        assertThat(review.getLikeCount()).isEqualTo(2);
        verify(reviewRepository).save(review);
        verify(gameService, never()).updateGameEmbeddedReview(any());
        verify(gameService, never())
                .updateGameReviewFromScratch(any(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void addLikeToReview_reviewAlreadyEmbedded_updatesEmbeddedLikeCountInPlace() {
        when(userNeo4jRepository.addLikeToReview("Lunark", "r1")).thenReturn(false);
        Review review = reviewWithLikes("r1", 10);
        when(reviewRepository.findById("r1")).thenReturn(Optional.of(review));
        Game game = mongoGame("g1", "BARRIER X");
        Review embeddedSameReview = reviewWithLikes("r1", 9);
        game.setReviews(new ArrayList<>(List.of(embeddedSameReview, reviewWithLikes("other", 1))));
        when(gameRepository.findByName("BARRIER X")).thenReturn(List.of(game));

        Boolean result = userNeo4jService.addLikeToReview("Lunark", "r1");

        assertThat(result).isTrue();
        verify(gameService).updateGameEmbeddedReview(game);
        verify(gameService, never())
                .updateGameReviewFromScratch(any(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void addLikeToReview_reviewNotYetEmbeddedButNowQualifies_rebuildsEmbeddedListFromScratch() {
        when(userNeo4jRepository.addLikeToReview("Lunark", "r1")).thenReturn(false);
        Review review = reviewWithLikes("r1", 10);
        when(reviewRepository.findById("r1")).thenReturn(Optional.of(review));
        Game game = mongoGame("g1", "BARRIER X");
        game.setReviews(new ArrayList<>(List.of(reviewWithLikes("other", 1))));
        when(gameRepository.findByName("BARRIER X")).thenReturn(List.of(game));

        Boolean result = userNeo4jService.addLikeToReview("Lunark", "r1");

        assertThat(result).isTrue();
        verify(gameService).updateGameReviewFromScratch(game, 20);
        verify(gameService, never()).updateGameEmbeddedReview(any());
    }

    @Test
    void addLikeToReview_reviewMissingInMongoAfterNeo4jLike_deletesNeo4jLikeAndReturnsFalse() {
        when(userNeo4jRepository.addLikeToReview("Lunark", "r1")).thenReturn(false);
        when(reviewRepository.findById("r1")).thenReturn(Optional.empty());

        Boolean result = userNeo4jService.addLikeToReview("Lunark", "r1");

        assertThat(result).isFalse();
        verify(userNeo4jRepository).deleteLikeFromReview("Lunark", "r1");
    }

    @Test
    void addLikeToReview_repositoryThrows_returnsNull() {
        when(userNeo4jRepository.addLikeToReview(anyString(), anyString()))
                .thenThrow(new RuntimeException("boom"));

        assertThat(userNeo4jService.addLikeToReview("Lunark", "r1")).isNull();
    }

    // --- removeLikeFromReview -----------------------------------------------------------------

    @Test
    void removeLikeFromReview_likeDidNotExist_returnsFalseWithoutTouchingMongo() {
        when(userNeo4jRepository.removeLikeFromReview("Lunark", "r1")).thenReturn(0L);

        assertThat(userNeo4jService.removeLikeFromReview("Lunark", "r1")).isFalse();
        verify(reviewRepository, never()).findById(anyString());
    }

    @Test
    void removeLikeFromReview_likeExistedAndReviewNotEmbedded_decrementsMongoOnly() {
        when(userNeo4jRepository.removeLikeFromReview("Lunark", "r1")).thenReturn(1L);
        Review review = reviewWithLikes("r1", 5);
        when(reviewRepository.findById("r1")).thenReturn(Optional.of(review));
        Game game = mongoGame("g1", "BARRIER X");
        game.setReviews(List.of(reviewWithLikes("other", 1)));
        when(gameRepository.findByName("BARRIER X")).thenReturn(List.of(game));

        Boolean result = userNeo4jService.removeLikeFromReview("Lunark", "r1");

        assertThat(result).isTrue();
        assertThat(review.getLikeCount()).isEqualTo(4);
        verify(gameService, never()).updateGameEmbeddedReview(any());
    }

    @Test
    void removeLikeFromReview_likeCountNeverGoesNegative() {
        when(userNeo4jRepository.removeLikeFromReview("Lunark", "r1")).thenReturn(1L);
        Review review = reviewWithLikes("r1", 0);
        when(reviewRepository.findById("r1")).thenReturn(Optional.of(review));
        when(gameRepository.findByName("BARRIER X")).thenReturn(List.of());

        userNeo4jService.removeLikeFromReview("Lunark", "r1");

        assertThat(review.getLikeCount()).isEqualTo(0);
    }

    @Test
    void removeLikeFromReview_reviewEmbedded_syncsEmbeddedLikeCountAndReorders() {
        when(userNeo4jRepository.removeLikeFromReview("Lunark", "r1")).thenReturn(1L);
        Review review = reviewWithLikes("r1", 5);
        when(reviewRepository.findById("r1")).thenReturn(Optional.of(review));
        Game game = mongoGame("g1", "BARRIER X");
        Review embedded = reviewWithLikes("r1", 5);
        game.setReviews(new ArrayList<>(List.of(embedded)));
        when(gameRepository.findByName("BARRIER X")).thenReturn(List.of(game));

        Boolean result = userNeo4jService.removeLikeFromReview("Lunark", "r1");

        assertThat(result).isTrue();
        assertThat(embedded.getLikeCount()).isEqualTo(4);
        verify(gameService).updateGameEmbeddedReview(game);
    }

    @Test
    void removeLikeFromReview_reviewMissingInMongo_returnsFalse() {
        when(userNeo4jRepository.removeLikeFromReview("Lunark", "r1")).thenReturn(1L);
        when(reviewRepository.findById("r1")).thenReturn(Optional.empty());

        assertThat(userNeo4jService.removeLikeFromReview("Lunark", "r1")).isFalse();
    }

    @Test
    void removeLikeFromReview_repositoryThrows_returnsNull() {
        when(userNeo4jRepository.removeLikeFromReview(anyString(), anyString()))
                .thenThrow(new RuntimeException("boom"));

        assertThat(userNeo4jService.removeLikeFromReview("Lunark", "r1")).isNull();
    }

    // --- follow / unfollow ---------------------------------------------------------------------

    @Test
    void followUser_bothUsersExistInGraph_linksAndReturnsTrue() {
        when(userNeo4jRepository.getUser("Lunark")).thenReturn(new UserNeo4j("u1", "Lunark"));
        when(userNeo4jRepository.getUser("Kaistlin")).thenReturn(new UserNeo4j("u2", "Kaistlin"));

        assertThat(userNeo4jService.followUser("Lunark", "Kaistlin")).isTrue();
        verify(userNeo4jRepository).followUser("Lunark", "Kaistlin");
    }

    @Test
    void followUser_followedUserMissingFromGraph_returnsFalseWithoutLinking() {
        when(userNeo4jRepository.getUser("Lunark")).thenReturn(new UserNeo4j("u1", "Lunark"));
        when(userNeo4jRepository.getUser("Ghost")).thenReturn(null);

        assertThat(userNeo4jService.followUser("Lunark", "Ghost")).isFalse();
        verify(userNeo4jRepository, never()).followUser(anyString(), anyString());
    }

    @Test
    void followUser_repositoryThrows_returnsNull() {
        when(userNeo4jRepository.getUser(anyString())).thenThrow(new RuntimeException("boom"));

        assertThat(userNeo4jService.followUser("Lunark", "Kaistlin")).isNull();
    }

    @Test
    void unfollowUser_currentlyFollowed_unlinksAndReturnsTrue() {
        when(userNeo4jRepository.findFollowedUsers("Lunark"))
                .thenReturn(List.of(new UserNeo4j("u2", "Kaistlin")));

        assertThat(userNeo4jService.unfollowUser("Lunark", "Kaistlin")).isTrue();
        verify(userNeo4jRepository).unfollowUser("Lunark", "Kaistlin");
    }

    @Test
    void unfollowUser_notCurrentlyFollowed_returnsFalseWithoutUnlinking() {
        when(userNeo4jRepository.findFollowedUsers("Lunark")).thenReturn(List.of());

        assertThat(userNeo4jService.unfollowUser("Lunark", "Kaistlin")).isFalse();
        verify(userNeo4jRepository, never()).unfollowUser(anyString(), anyString());
    }

    @Test
    void unfollowUser_repositoryThrows_returnsNull() {
        when(userNeo4jRepository.findFollowedUsers(anyString()))
                .thenThrow(new RuntimeException("boom"));

        assertThat(userNeo4jService.unfollowUser("Lunark", "Kaistlin")).isNull();
    }

    // --- getUser / updateUser -------------------------------------------------------------------

    @Test
    void getUser_userExists_returnsIt() {
        when(userNeo4jRepository.getUser("Lunark")).thenReturn(new UserNeo4j("u1", "Lunark"));

        UserNeo4j result = userNeo4jService.getUser("Lunark");

        assertThat(result.getId()).isEqualTo("u1");
    }

    @Test
    void getUser_userMissing_returnsNullIdSentinel() {
        when(userNeo4jRepository.getUser("Ghost")).thenReturn(null);

        UserNeo4j result = userNeo4jService.getUser("Ghost");

        assertThat(result.getId()).isEqualTo("null");
    }

    @Test
    void getUser_repositoryThrows_returnsNull() {
        when(userNeo4jRepository.getUser(anyString())).thenThrow(new RuntimeException("boom"));

        assertThat(userNeo4jService.getUser("Lunark")).isNull();
    }

    @Test
    void updateUser_repositorySucceeds_returnsOk() {
        ResponseEntity<String> response = userNeo4jService.updateUser("oldName", "newName");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(userNeo4jRepository).updateUser("oldName", "newName");
    }

    @Test
    void updateUser_repositoryThrows_returnsInternalServerError() {
        org.mockito.Mockito.doThrow(new RuntimeException("boom"))
                .when(userNeo4jRepository)
                .updateUser(anyString(), anyString());

        ResponseEntity<String> response = userNeo4jService.updateUser("oldName", "newName");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    // --- thin wrappers: one happy-path + one failure-path test each ----------------------------

    @Test
    void countUserDocument_repositorySucceeds_returnsCount() {
        when(loginRepository.count()).thenReturn(99L);

        assertThat(userNeo4jService.countUserDocument()).isEqualTo(99L);
    }

    @Test
    void countUserDocument_repositoryThrows_returnsMinusOne() {
        when(loginRepository.count()).thenThrow(new RuntimeException("boom"));

        assertThat(userNeo4jService.countUserDocument()).isEqualTo(-1L);
    }

    @Test
    void getLikedReviewIds_repositoryThrows_returnsEmptyList() {
        when(userNeo4jRepository.findLikedReviewIds(anyString()))
                .thenThrow(new RuntimeException("boom"));

        assertThat(userNeo4jService.getLikedReviewIds("Lunark")).isEmpty();
    }

    @Test
    void searchUsers_repositoryThrows_returnsNull() {
        when(userNeo4jRepository.searchUsers(anyString(), anyString()))
                .thenThrow(new RuntimeException("boom"));

        assertThat(userNeo4jService.searchUsers("query", "Lunark")).isNull();
    }
}
