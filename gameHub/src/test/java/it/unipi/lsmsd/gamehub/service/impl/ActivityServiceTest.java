package it.unipi.lsmsd.gamehub.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import it.unipi.lsmsd.gamehub.DTO.ActivityDTO;
import it.unipi.lsmsd.gamehub.DTO.CommunityHighlightsDTO;
import it.unipi.lsmsd.gamehub.DTO.UserStatsDTO;
import it.unipi.lsmsd.gamehub.model.Activity;
import it.unipi.lsmsd.gamehub.model.ActivityType;
import it.unipi.lsmsd.gamehub.model.FeedState;
import it.unipi.lsmsd.gamehub.model.Game;
import it.unipi.lsmsd.gamehub.model.Review;
import it.unipi.lsmsd.gamehub.model.URL;
import it.unipi.lsmsd.gamehub.model.UserNeo4j;
import it.unipi.lsmsd.gamehub.repository.ActivityRepository;
import it.unipi.lsmsd.gamehub.repository.GameRepository;
import it.unipi.lsmsd.gamehub.repository.ReviewRepository;
import it.unipi.lsmsd.gamehub.repository.UserNeo4jRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.UpdateDefinition;

@ExtendWith(MockitoExtension.class)
class ActivityServiceTest {

    @Mock private ActivityRepository activityRepository;
    @Mock private UserNeo4jRepository userNeo4jRepository;
    @Mock private GameRepository gameRepository;
    @Mock private ReviewRepository reviewRepository;
    @Mock private MongoTemplate mongoTemplate;

    @InjectMocks private ActivityService activityService;

    private final Pageable pageable = PageRequest.of(0, 15);

    private Activity activity(
            ActivityType type,
            String username,
            String gameName,
            String reviewId,
            String targetUsername,
            Instant createdAt) {
        return new Activity(
                null, username, type, gameName, reviewId, null, targetUsername, createdAt);
    }

    private Game game(String name) {
        Game game = new Game();
        game.setId("g-" + name);
        game.setName(name);
        game.setGenres("Indie");
        URL url = new URL();
        url.setHeaderImage("http://img/" + name + ".jpg");
        game.setURL(url);
        return game;
    }

    private Review review(String id, String title, String author, int likes) {
        Review review = new Review();
        review.setId(id);
        review.setTitle(title);
        review.setUsername(author);
        review.setComment("great");
        review.setUserScore(9);
        review.setLikeCount(likes);
        return review;
    }

    private void followsOnly(String... friends) {
        when(userNeo4jRepository.findFollowedUsers("toni"))
                .thenReturn(
                        java.util.Arrays.stream(friends)
                                .map(f -> new UserNeo4j("id-" + f, f))
                                .toList());
    }

    @SuppressWarnings("unchecked")
    private void stubFeed(List<Activity> activities) {
        when(activityRepository.findByUsernameInAndCreatedAtAfterOrderByCreatedAtDesc(
                        anyList(), any(Instant.class), eq(pageable)))
                .thenReturn(new PageImpl<>(activities, pageable, activities.size()));
    }

    // --- recording ----------------------------------------------------------------------------

    @Test
    void recordLikeReview_savesLikeActivityWithReviewIdAndGame() {
        activityService.recordLikeReview("anna", "Portal 2", "r1");

        ArgumentCaptor<Activity> saved = ArgumentCaptor.forClass(Activity.class);
        verify(activityRepository).save(saved.capture());
        assertThat(saved.getValue().getType()).isEqualTo(ActivityType.LIKE_REVIEW);
        assertThat(saved.getValue().getUsername()).isEqualTo("anna");
        assertThat(saved.getValue().getGameName()).isEqualTo("Portal 2");
        assertThat(saved.getValue().getReviewId()).isEqualTo("r1");
        assertThat(saved.getValue().getCreatedAt()).isNotNull();
    }

    @Test
    void recordFollow_savesFollowActivityWithTargetAndNoGame() {
        activityService.recordFollow("anna", "bob");

        ArgumentCaptor<Activity> saved = ArgumentCaptor.forClass(Activity.class);
        verify(activityRepository).save(saved.capture());
        assertThat(saved.getValue().getType()).isEqualTo(ActivityType.FOLLOW);
        assertThat(saved.getValue().getTargetUsername()).isEqualTo("bob");
        assertThat(saved.getValue().getGameName()).isNull();
    }

    @Test
    void record_repositoryThrows_isSwallowedSoTheOriginalActionIsNotFailed() {
        when(activityRepository.save(any(Activity.class))).thenThrow(new RuntimeException("boom"));

        activityService.recordFollow("anna", "bob");
        activityService.recordLikeReview("anna", "Portal 2", "r1");
        // nessuna eccezione risale al chiamante
    }

    @Test
    void removeLikeReview_deletesTheLikeActivity() {
        activityService.removeLikeReview("anna", "r1");

        verify(activityRepository)
                .deleteByUsernameAndTypeAndReviewId("anna", ActivityType.LIKE_REVIEW, "r1");
    }

    @Test
    void removeFollow_deletesTheFollowActivity() {
        activityService.removeFollow("anna", "bob");

        verify(activityRepository)
                .deleteByUsernameAndTypeAndTargetUsername("anna", ActivityType.FOLLOW, "bob");
    }

    // --- getFriendsActivity -------------------------------------------------------------------

    @Test
    void getFriendsActivity_followsNobody_returnsEmptyPageWithoutQueryingActivities() {
        followsOnly();

        Page<ActivityDTO> result = activityService.getFriendsActivity("toni", pageable);

        assertThat(result.getContent()).isEmpty();
        verify(activityRepository, never())
                .findByUsernameInAndCreatedAtAfterOrderByCreatedAtDesc(any(), any(), any());
    }

    @Test
    void getFriendsActivity_queriesOnlyTheRecentWindow() {
        followsOnly("anna");
        stubFeed(List.of());

        activityService.getFriendsActivity("toni", pageable);

        ArgumentCaptor<Instant> since = ArgumentCaptor.forClass(Instant.class);
        verify(activityRepository)
                .findByUsernameInAndCreatedAtAfterOrderByCreatedAtDesc(
                        eq(List.of("anna")), since.capture(), eq(pageable));
        assertThat(Duration.between(since.getValue(), Instant.now()))
                .isBetween(
                        ActivityService.FEED_WINDOW.minusMinutes(1),
                        ActivityService.FEED_WINDOW.plusMinutes(1));
    }

    @Test
    void getFriendsActivity_marksUnseenOnlyWhatIsNewerThanTheBookmark() {
        followsOnly("anna");
        Instant bookmark = Instant.now().minus(Duration.ofHours(2));
        when(mongoTemplate.findById("toni", FeedState.class))
                .thenReturn(new FeedState("toni", bookmark));
        stubFeed(
                List.of(
                        activity(
                                ActivityType.WISHLIST_ADD,
                                "anna",
                                "Portal 2",
                                null,
                                null,
                                bookmark.plusSeconds(60)),
                        activity(
                                ActivityType.WISHLIST_ADD,
                                "anna",
                                "Celeste",
                                null,
                                null,
                                bookmark.minusSeconds(60))));
        when(gameRepository.findByNameIn(anyList()))
                .thenReturn(List.of(game("Portal 2"), game("Celeste")));

        List<ActivityDTO> result =
                activityService.getFriendsActivity("toni", pageable).getContent();

        assertThat(result).extracting(ActivityDTO::isUnseen).containsExactly(true, false);
    }

    @Test
    void getFriendsActivity_firstVisitWithoutBookmark_marksNothingAsUnseen() {
        followsOnly("anna");
        when(mongoTemplate.findById("toni", FeedState.class)).thenReturn(null);
        stubFeed(
                List.of(
                        activity(
                                ActivityType.WISHLIST_ADD,
                                "anna",
                                "Portal 2",
                                null,
                                null,
                                Instant.now())));
        when(gameRepository.findByNameIn(anyList())).thenReturn(List.of(game("Portal 2")));

        List<ActivityDTO> result =
                activityService.getFriendsActivity("toni", pageable).getContent();

        assertThat(result).extracting(ActivityDTO::isUnseen).containsExactly(false);
    }

    @Test
    void getFriendsActivity_likeReview_carriesReviewAuthorAndGame() {
        followsOnly("anna");
        Activity like =
                activity(ActivityType.LIKE_REVIEW, "anna", "Portal 2", "r1", null, Instant.now());
        like.setId("a1");
        stubFeed(List.of(like));
        when(gameRepository.findByNameIn(anyList())).thenReturn(List.of(game("Portal 2")));
        when(reviewRepository.findAllById(List.of("r1")))
                .thenReturn(List.of(review("r1", "Portal 2", "bob", 7)));

        ActivityDTO dto = activityService.getFriendsActivity("toni", pageable).getContent().get(0);

        assertThat(dto.getType()).isEqualTo(ActivityType.LIKE_REVIEW);
        assertThat(dto.getId()).isEqualTo("a1");
        assertThat(dto.getReview().getUsername()).isEqualTo("bob");
        assertThat(dto.getReview().getLikeCount()).isEqualTo(7);
        assertThat(dto.getGame().getHeaderImage()).isEqualTo("http://img/Portal 2.jpg");
        assertThat(dto.getGameHeaderImage()).isEqualTo("http://img/Portal 2.jpg");
    }

    @Test
    void getFriendsActivity_reviewDeletedMeanwhile_dropsTheItem() {
        followsOnly("anna");
        stubFeed(
                List.of(
                        activity(
                                ActivityType.LIKE_REVIEW,
                                "anna",
                                "Portal 2",
                                "gone",
                                null,
                                Instant.now()),
                        activity(
                                ActivityType.WISHLIST_ADD,
                                "anna",
                                "Portal 2",
                                null,
                                null,
                                Instant.now())));
        when(gameRepository.findByNameIn(anyList())).thenReturn(List.of(game("Portal 2")));
        when(reviewRepository.findAllById(List.of("gone"))).thenReturn(List.of());

        List<ActivityDTO> result =
                activityService.getFriendsActivity("toni", pageable).getContent();

        assertThat(result)
                .extracting(ActivityDTO::getType)
                .containsExactly(ActivityType.WISHLIST_ADD);
    }

    @Test
    void getFriendsActivity_follow_carriesTargetStatsFromOneBatchQuery() {
        followsOnly("anna");
        stubFeed(
                List.of(
                        activity(ActivityType.FOLLOW, "anna", null, null, "bob", Instant.now()),
                        activity(ActivityType.FOLLOW, "anna", null, null, "carl", Instant.now())));
        when(userNeo4jRepository.findUserStats(List.of("bob", "carl")))
                .thenReturn(List.of(new UserStatsDTO("bob", 12, 340)));

        List<ActivityDTO> result =
                activityService.getFriendsActivity("toni", pageable).getContent();

        assertThat(result)
                .extracting(ActivityDTO::getTargetUsername)
                .containsExactly("bob", "carl");
        assertThat(result.get(0).getTargetWishlistCount()).isEqualTo(12);
        assertThat(result.get(0).getTargetFollowers()).isEqualTo(340);
        // nessuna statistica disponibile: la card resta comunque mostrabile
        assertThat(result.get(1).getTargetWishlistCount()).isNull();
        verify(userNeo4jRepository).findUserStats(anyList());
    }

    @Test
    void getFriendsActivity_repositoryThrows_returnsEmptyPage() {
        when(userNeo4jRepository.findFollowedUsers("toni")).thenThrow(new RuntimeException("boom"));

        assertThat(activityService.getFriendsActivity("toni", pageable).getContent()).isEmpty();
    }

    // --- markFeedSeen -------------------------------------------------------------------------

    @Test
    void markFeedSeen_upsertsTheBookmarkForTheUser() {
        Instant upTo = Instant.now().minusSeconds(30);

        assertThat(activityService.markFeedSeen("toni", upTo)).isTrue();

        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<UpdateDefinition> update = ArgumentCaptor.forClass(UpdateDefinition.class);
        verify(mongoTemplate).upsert(query.capture(), update.capture(), eq(FeedState.class));
        assertThat(query.getValue().getQueryObject()).containsEntry("_id", "toni");
        // $max: il segnalibro puo' solo avanzare
        assertThat(update.getValue().getUpdateObject()).containsKey("$max");
    }

    @Test
    void markFeedSeen_futureInstant_isClampedToNow() {
        Instant future = Instant.now().plus(Duration.ofDays(1));

        activityService.markFeedSeen("toni", future);

        ArgumentCaptor<UpdateDefinition> update = ArgumentCaptor.forClass(UpdateDefinition.class);
        verify(mongoTemplate).upsert(any(Query.class), update.capture(), eq(FeedState.class));
        Document max = (Document) update.getValue().getUpdateObject().get("$max");
        assertThat((Instant) max.get("lastSeenAt")).isBefore(future);
    }

    @Test
    void markFeedSeen_writeFails_returnsFalse() {
        when(mongoTemplate.upsert(
                        any(Query.class), any(UpdateDefinition.class), eq(FeedState.class)))
                .thenThrow(new RuntimeException("boom"));

        assertThat(activityService.markFeedSeen("toni", Instant.now())).isFalse();
    }

    // --- getCommunityHighlights ---------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private void stubAggregation(List<Document>... perCall) {
        AggregationResults<Document>[] results = new AggregationResults[perCall.length];
        for (int i = 0; i < perCall.length; i++) {
            results[i] = new AggregationResults<>(perCall[i], new Document());
        }
        var stubbing =
                when(
                        mongoTemplate.aggregate(
                                any(Aggregation.class), eq("activities"), eq(Document.class)));
        stubbing.thenReturn(results[0], java.util.Arrays.copyOfRange(results, 1, results.length));
    }

    @Test
    void getCommunityHighlights_recentLikesFound_ranksReviewsAndReportsTheWindow() {
        stubAggregation(
                List.of(new Document("_id", "r1").append("count", 5)),
                List.of()); // giochi: nessuna aggiunta recente
        when(reviewRepository.findAllById(List.of("r1")))
                .thenReturn(List.of(review("r1", "Portal 2", "bob", 40)));
        when(gameRepository.findByNameIn(anyList())).thenReturn(List.of(game("Portal 2")));

        CommunityHighlightsDTO result = activityService.getCommunityHighlights();

        assertThat(result.getTrendingReviews()).hasSize(1);
        assertThat(result.getTrendingReviews().get(0).getRecentLikes()).isEqualTo(5);
        assertThat(result.getTrendingReviews().get(0).getWindowHours()).isEqualTo(48);
        assertThat(result.getTrendingReviews().get(0).getReview().getId()).isEqualTo("r1");
        assertThat(result.getHotGames()).isEmpty();
    }

    @Test
    void getCommunityHighlights_quietLast48h_widensToTheWeek() {
        stubAggregation(
                List.of(), // 48h: niente
                List.of(new Document("_id", "r1").append("count", 3)), // 7 giorni
                List.of()); // giochi
        when(reviewRepository.findAllById(List.of("r1")))
                .thenReturn(List.of(review("r1", "Portal 2", "bob", 40)));
        when(gameRepository.findByNameIn(anyList())).thenReturn(List.of(game("Portal 2")));

        CommunityHighlightsDTO result = activityService.getCommunityHighlights();

        assertThat(result.getTrendingReviews()).hasSize(1);
        assertThat(result.getTrendingReviews().get(0).getWindowHours()).isEqualTo(168);
    }

    @Test
    void getCommunityHighlights_noActivityAtAll_returnsEmptySections() {
        stubAggregation(List.of(), List.of(), List.of());

        CommunityHighlightsDTO result = activityService.getCommunityHighlights();

        assertThat(result.getTrendingReviews()).isEmpty();
        assertThat(result.getHotGames()).isEmpty();
    }

    @Test
    void getCommunityHighlights_hotGames_listedWithTheirRecentWishlistAdds() {
        stubAggregation(
                List.of(), List.of(), List.of(new Document("_id", "Celeste").append("count", 4)));
        when(gameRepository.findByNameIn(List.of("Celeste"))).thenReturn(List.of(game("Celeste")));

        CommunityHighlightsDTO result = activityService.getCommunityHighlights();

        assertThat(result.getHotGames()).hasSize(1);
        assertThat(result.getHotGames().get(0).getGame().getName()).isEqualTo("Celeste");
        assertThat(result.getHotGames().get(0).getRecentWishlistAdds()).isEqualTo(4);
    }

    @Test
    void getCommunityHighlights_aggregationFails_returnsEmptySectionsInsteadOfThrowing() {
        when(mongoTemplate.aggregate(any(Aggregation.class), eq("activities"), eq(Document.class)))
                .thenThrow(new RuntimeException("boom"));

        CommunityHighlightsDTO result = activityService.getCommunityHighlights();

        assertThat(result.getTrendingReviews()).isEmpty();
        assertThat(result.getHotGames()).isEmpty();
    }
}
