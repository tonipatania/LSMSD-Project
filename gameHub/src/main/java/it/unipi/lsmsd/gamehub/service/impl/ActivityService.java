package it.unipi.lsmsd.gamehub.service.impl;

import it.unipi.lsmsd.gamehub.DTO.ActivityDTO;
import it.unipi.lsmsd.gamehub.DTO.CommunityHighlightsDTO;
import it.unipi.lsmsd.gamehub.DTO.GameSnippetDTO;
import it.unipi.lsmsd.gamehub.DTO.HotGameDTO;
import it.unipi.lsmsd.gamehub.DTO.ReviewSnippetDTO;
import it.unipi.lsmsd.gamehub.DTO.TrendingReviewDTO;
import it.unipi.lsmsd.gamehub.DTO.UserStatsDTO;
import it.unipi.lsmsd.gamehub.model.Activity;
import it.unipi.lsmsd.gamehub.model.ActivityType;
import it.unipi.lsmsd.gamehub.model.FeedState;
import it.unipi.lsmsd.gamehub.model.Game;
import it.unipi.lsmsd.gamehub.model.Review;
import it.unipi.lsmsd.gamehub.model.UserNeo4j;
import it.unipi.lsmsd.gamehub.repository.ActivityRepository;
import it.unipi.lsmsd.gamehub.repository.GameRepository;
import it.unipi.lsmsd.gamehub.repository.ReviewRepository;
import it.unipi.lsmsd.gamehub.repository.UserNeo4jRepository;
import it.unipi.lsmsd.gamehub.service.IActivityService;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class ActivityService implements IActivityService {
    @Autowired private ActivityRepository activityRepository;
    @Autowired private UserNeo4jRepository userNeo4jRepository;
    @Autowired private GameRepository gameRepository;
    @Autowired private ReviewRepository reviewRepository;
    @Autowired private MongoTemplate mongoTemplate;

    // Oltre questa finestra un'attivita' non e' piu' una novita': il feed resta paginabile a
    // ritroso solo fin qui, cosi non e' mai un archivio infinito e l'indice resta selettivo.
    static final Duration FEED_WINDOW = Duration.ofDays(30);

    // Recensioni in tendenza: si guarda prima l'ultimo giorno e mezzo, e solo se nessuna ha
    // raccolto abbastanza like si allarga alla settimana (community piccola o giorno tranquillo).
    static final int[] TRENDING_WINDOWS_HOURS = {48, 168};
    static final int TRENDING_MIN_LIKES = 2;
    static final int TRENDING_LIMIT = 5;

    static final Duration HOT_GAMES_WINDOW = Duration.ofDays(7);
    static final int HOT_GAMES_LIMIT = 5;

    // il feed e' un arricchimento della Home, non un'operazione critica: un fallimento qui non
    // deve far fallire l'azione (wishlist/review/like/follow) gia' andata a buon fine altrove,
    // quindi ogni eccezione viene solo loggata invece di risalire al chiamante.
    @Override
    public void recordWishlistAdd(String username, String gameName) {
        save("recordWishlistAdd", username, ActivityType.WISHLIST_ADD, gameName, null, null, null);
    }

    @Override
    public void recordReview(String username, String gameName, String reviewId, int score) {
        save("recordReview", username, ActivityType.REVIEW, gameName, reviewId, score, null);
    }

    @Override
    public void recordLikeReview(String username, String gameName, String reviewId) {
        save(
                "recordLikeReview",
                username,
                ActivityType.LIKE_REVIEW,
                gameName,
                reviewId,
                null,
                null);
    }

    @Override
    public void recordFollow(String username, String targetUsername) {
        save("recordFollow", username, ActivityType.FOLLOW, null, null, null, targetUsername);
    }

    @Override
    public void removeLikeReview(String username, String reviewId) {
        try {
            activityRepository.deleteByUsernameAndTypeAndReviewId(
                    username, ActivityType.LIKE_REVIEW, reviewId);
        } catch (Exception e) {
            log.error("Errore in removeLikeReview", e);
        }
    }

    @Override
    public void removeFollow(String username, String targetUsername) {
        try {
            activityRepository.deleteByUsernameAndTypeAndTargetUsername(
                    username, ActivityType.FOLLOW, targetUsername);
        } catch (Exception e) {
            log.error("Errore in removeFollow", e);
        }
    }

    private void save(
            String operation,
            String username,
            ActivityType type,
            String gameName,
            String reviewId,
            Integer score,
            String targetUsername) {
        try {
            activityRepository.save(
                    new Activity(
                            null,
                            username,
                            type,
                            gameName,
                            reviewId,
                            score,
                            targetUsername,
                            Instant.now()));
        } catch (Exception e) {
            log.error("Errore in {}", operation, e);
        }
    }

    @Override
    public Page<ActivityDTO> getFriendsActivity(String username, Pageable pageable) {
        try {
            List<String> friends =
                    userNeo4jRepository.findFollowedUsers(username).stream()
                            .map(UserNeo4j::getUsername)
                            .toList();
            if (friends.isEmpty()) {
                return Page.empty(pageable);
            }

            Page<Activity> activities =
                    activityRepository.findByUsernameInAndCreatedAtAfterOrderByCreatedAtDesc(
                            friends, Instant.now().minus(FEED_WINDOW), pageable);
            List<Activity> content = activities.getContent();

            // Una query batch per ciascun tipo di contenuto della pagina, invece di una per riga.
            // I nomi dei giochi non sono univoci nel dataset, ma per una card di feed basta un
            // gioco plausibile: stesso compromesso gia' accettato altrove (vedi
            // UserNeo4jRepository.addGameToUser) per i nomi duplicati.
            Map<String, Game> gamesByName =
                    gamesByName(content.stream().map(Activity::getGameName).toList());
            Map<String, Review> reviewsById = reviewsById(content);
            Map<String, UserStatsDTO> statsByUser = statsForFollowTargets(content);
            Instant lastSeenAt = readLastSeenAt(username);

            List<ActivityDTO> dtos = new ArrayList<>(content.size());
            for (Activity activity : content) {
                ActivityDTO dto =
                        toDTO(activity, gamesByName, reviewsById, statsByUser, lastSeenAt);
                // una recensione cancellata nel frattempo (moderazione) non deve piu' comparire
                if (dto != null) {
                    dtos.add(dto);
                }
            }

            return new PageImpl<>(dtos, pageable, activities.getTotalElements());
        } catch (Exception e) {
            log.error("Errore in getFriendsActivity", e);
            return Page.empty(pageable);
        }
    }

    private Map<String, Game> gamesByName(Collection<String> names) {
        List<String> distinct = names.stream().filter(Objects::nonNull).distinct().toList();
        if (distinct.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, Game> byName = new HashMap<>();
        for (Game game : gameRepository.findByNameIn(distinct)) {
            byName.putIfAbsent(game.getName(), game);
        }
        return byName;
    }

    private Map<String, Review> reviewsById(List<Activity> activities) {
        List<String> ids =
                activities.stream()
                        .filter(
                                a ->
                                        a.getType() == ActivityType.REVIEW
                                                || a.getType() == ActivityType.LIKE_REVIEW)
                        .map(Activity::getReviewId)
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList();
        if (ids.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, Review> byId = new HashMap<>();
        for (Review review : reviewRepository.findAllById(ids)) {
            byId.put(review.getId(), review);
        }
        return byId;
    }

    private Map<String, UserStatsDTO> statsForFollowTargets(List<Activity> activities) {
        List<String> targets =
                activities.stream()
                        .filter(a -> a.getType() == ActivityType.FOLLOW)
                        .map(Activity::getTargetUsername)
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList();
        if (targets.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, UserStatsDTO> byUser = new HashMap<>();
        for (UserStatsDTO stats : userNeo4jRepository.findUserStats(targets)) {
            byUser.put(stats.getUsername(), stats);
        }
        return byUser;
    }

    // null = prima visita: senza un segnalibro non si sa cosa l'utente abbia gia' visto, e
    // marcare "nuovo" l'intero storico (fino a 30 giorni) sarebbe rumore, non informazione.
    private Instant readLastSeenAt(String username) {
        FeedState state = mongoTemplate.findById(username, FeedState.class);
        return state == null ? null : state.getLastSeenAt();
    }

    private ActivityDTO toDTO(
            Activity activity,
            Map<String, Game> gamesByName,
            Map<String, Review> reviewsById,
            Map<String, UserStatsDTO> statsByUser,
            Instant lastSeenAt) {
        ActivityDTO dto = new ActivityDTO();
        dto.setId(activity.getId());
        dto.setUsername(activity.getUsername());
        dto.setType(activity.getType());
        dto.setGameName(activity.getGameName());
        dto.setScore(activity.getScore());
        dto.setCreatedAt(activity.getCreatedAt());
        dto.setUnseen(
                lastSeenAt != null
                        && activity.getCreatedAt() != null
                        && activity.getCreatedAt().isAfter(lastSeenAt));

        Game game = gamesByName.get(activity.getGameName());
        if (game != null) {
            GameSnippetDTO snippet = toSnippet(game);
            dto.setGame(snippet);
            dto.setGameHeaderImage(snippet.getHeaderImage());
        }

        if (activity.getType() == ActivityType.REVIEW
                || activity.getType() == ActivityType.LIKE_REVIEW) {
            Review review = reviewsById.get(activity.getReviewId());
            if (review == null) {
                return null;
            }
            dto.setReview(toSnippet(review));
        }

        if (activity.getType() == ActivityType.FOLLOW) {
            dto.setTargetUsername(activity.getTargetUsername());
            UserStatsDTO stats = statsByUser.get(activity.getTargetUsername());
            if (stats != null) {
                dto.setTargetWishlistCount(stats.getWishlistCount());
                dto.setTargetFollowers(stats.getFollowers());
            }
        }
        return dto;
    }

    private GameSnippetDTO toSnippet(Game game) {
        return new GameSnippetDTO(
                game.getId(),
                game.getName(),
                game.getURL() != null ? game.getURL().getHeaderImage() : null,
                game.getGenres(),
                game.getAvgScore(),
                game.getPrice());
    }

    private ReviewSnippetDTO toSnippet(Review review) {
        return new ReviewSnippetDTO(
                review.getId(),
                review.getTitle(),
                review.getUserScore(),
                review.getComment(),
                review.getUsername(),
                review.getLikeCount());
    }

    @Override
    public boolean markFeedSeen(String username, Instant upTo) {
        try {
            // $max rende l'avanzamento atomico e monotono anche con due schede aperte insieme;
            // il tetto a "adesso" impedisce a un client sbagliato di segnare come viste attivita'
            // che non esistono ancora.
            Instant now = Instant.now();
            Instant bounded = upTo.isAfter(now) ? now : upTo;
            mongoTemplate.upsert(
                    Query.query(Criteria.where("_id").is(username)),
                    new Update().max("lastSeenAt", bounded),
                    FeedState.class);
            return true;
        } catch (Exception e) {
            log.error("Errore in markFeedSeen", e);
            return false;
        }
    }

    @Override
    public CommunityHighlightsDTO getCommunityHighlights() {
        try {
            return new CommunityHighlightsDTO(trendingReviews(), hotGames());
        } catch (Exception e) {
            log.error("Errore in getCommunityHighlights", e);
            return new CommunityHighlightsDTO(List.of(), List.of());
        }
    }

    private List<TrendingReviewDTO> trendingReviews() {
        for (int windowHours : TRENDING_WINDOWS_HOURS) {
            List<Document> rows =
                    countByField(
                            ActivityType.LIKE_REVIEW,
                            "reviewId",
                            Instant.now().minus(Duration.ofHours(windowHours)),
                            TRENDING_MIN_LIKES,
                            TRENDING_LIMIT);
            if (rows.isEmpty()) {
                continue;
            }

            List<String> ids = rows.stream().map(r -> r.getString("_id")).toList();
            Map<String, Review> reviews = new HashMap<>();
            for (Review review : reviewRepository.findAllById(ids)) {
                reviews.put(review.getId(), review);
            }
            Map<String, Game> games =
                    gamesByName(reviews.values().stream().map(Review::getTitle).toList());

            List<TrendingReviewDTO> result = new ArrayList<>();
            for (Document row : rows) {
                Review review = reviews.get(row.getString("_id"));
                if (review == null) {
                    continue;
                }
                Game game = games.get(review.getTitle());
                result.add(
                        new TrendingReviewDTO(
                                toSnippet(review),
                                game != null ? toSnippet(game).getHeaderImage() : null,
                                ((Number) row.get("count")).intValue(),
                                windowHours));
            }
            if (!result.isEmpty()) {
                return result;
            }
        }
        return List.of();
    }

    private List<HotGameDTO> hotGames() {
        List<Document> rows =
                countByField(
                        ActivityType.WISHLIST_ADD,
                        "gameName",
                        Instant.now().minus(HOT_GAMES_WINDOW),
                        1,
                        HOT_GAMES_LIMIT);
        if (rows.isEmpty()) {
            return List.of();
        }

        Map<String, Game> games = gamesByName(rows.stream().map(r -> r.getString("_id")).toList());
        List<HotGameDTO> result = new ArrayList<>();
        for (Document row : rows) {
            Game game = games.get(row.getString("_id"));
            if (game != null) {
                result.add(new HotGameDTO(toSnippet(game), ((Number) row.get("count")).intValue()));
            }
        }
        return result;
    }

    // conta le attivita' di un tipo dopo "since", raggruppate per campo, e tiene le piu' frequenti
    private List<Document> countByField(
            ActivityType type, String field, Instant since, int minCount, int limit) {
        Aggregation aggregation =
                Aggregation.newAggregation(
                        Aggregation.match(
                                Criteria.where("type")
                                        .is(type.name())
                                        .and("createdAt")
                                        .gte(Date.from(since))),
                        Aggregation.group(field).count().as("count"),
                        Aggregation.match(Criteria.where("count").gte(minCount)),
                        Aggregation.sort(Sort.Direction.DESC, "count"),
                        Aggregation.limit(limit));
        return mongoTemplate
                .aggregate(aggregation, "activities", Document.class)
                .getMappedResults();
    }
}
