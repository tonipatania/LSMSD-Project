package it.unipi.lsmsd.gamehub.service.impl;

import it.unipi.lsmsd.gamehub.DTO.GameRailsDTO;
import it.unipi.lsmsd.gamehub.model.ActivityType;
import it.unipi.lsmsd.gamehub.model.Game;
import it.unipi.lsmsd.gamehub.model.GameNeo4j;
import it.unipi.lsmsd.gamehub.model.URL;
import it.unipi.lsmsd.gamehub.repository.GameNeo4jRepository;
import it.unipi.lsmsd.gamehub.repository.GameRepository;
import it.unipi.lsmsd.gamehub.service.IGameRailService;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class GameRailService implements IGameRailService {
    static final int RAIL_SIZE = 14;
    // quanti giochi "piu' desiderati" si leggono dal grafo: e' il serbatoio da cui si riempie lo
    // scaffale settimanale e si scelgono i preferiti, quindi deve essere molto piu' grande di
    // RAIL_SIZE
    static final int WISHED_POOL_SIZE = 200;
    static final int FAVORITE_MIN_SCORE = 8;
    static final Duration WEEK = Duration.ofDays(7);
    // una recensione dice piu' di un'aggiunta alla wishlist: costa piu' fatica e porta contenuto
    static final int REVIEW_WEIGHT = 2;

    // Gli scaffali sono uguali per tutti e costano una scansione dei giochi (~0,6 s per le date)
    // piu' una delle relazioni ADD del grafo (~1 s): si ricalcolano al massimo ogni CACHE_TTL.
    // Cache
    // in memoria e non su Redis di proposito: il payload e' minuscolo, non dipende dall'utente, e
    // cosi funziona anche senza Redis.
    static final Duration CACHE_TTL = Duration.ofMinutes(5);

    @Autowired private GameRepository gameRepository;
    @Autowired private GameNeo4jRepository gameNeo4jRepository;
    @Autowired private MongoTemplate mongoTemplate;

    private record Cached(GameRailsDTO rails, Instant builtAt) {}

    private volatile Cached cached;
    private final AtomicBoolean refreshing = new AtomicBoolean(false);

    // package-private per i test, che devono poter far scadere la cache senza aspettare 5 minuti
    Duration cacheTtl = CACHE_TTL;

    @Override
    public GameRailsDTO getRails() {
        Cached current = cached;
        if (current != null) {
            // Scaduta ma presente: si serve subito la copia vecchia e la si rinnova in
            // background. Il ricalcolo a freddo costa qualche secondo e non deve toccare a un
            // utente ogni CACHE_TTL: bloccano solo la primissima richiesta (e il warm-up).
            if (!isFresh(current)) {
                refreshInBackground();
            }
            return current.rails();
        }
        synchronized (this) {
            // un altro thread potrebbe aver costruito gli scaffali mentre si aspettava il lock
            current = cached;
            if (current != null) {
                return current.rails();
            }
            GameRailsDTO built = build();
            // database non raggiungibili: il vuoto non si memorizza, cosi la prossima richiesta
            // ci riprova
            if (!isEmpty(built)) {
                cached = new Cached(built, Instant.now());
            }
            return built;
        }
    }

    // calcola gli scaffali appena l'app e' pronta, cosi anche il primo utente li trova in cache
    @EventListener(ApplicationReadyEvent.class)
    public void warmUp() {
        refreshInBackground();
    }

    private void refreshInBackground() {
        // un solo ricalcolo alla volta: piu' richieste su una cache scaduta non li moltiplicano
        if (!refreshing.compareAndSet(false, true)) {
            return;
        }
        CompletableFuture.runAsync(
                () -> {
                    try {
                        GameRailsDTO built = build();
                        // se il ricalcolo fallisce si tengono i vecchi scaffali
                        if (!isEmpty(built)) {
                            cached = new Cached(built, Instant.now());
                        }
                    } catch (Exception e) {
                        log.error("Errore nel rinnovo degli scaffali", e);
                    } finally {
                        refreshing.set(false);
                    }
                });
    }

    private boolean isFresh(Cached entry) {
        return entry.builtAt().plus(cacheTtl).isAfter(Instant.now());
    }

    private boolean isEmpty(GameRailsDTO rails) {
        return rails.getWeekly().isEmpty()
                && rails.getFavorites().isEmpty()
                && rails.getLatest().isEmpty();
    }

    private GameRailsDTO build() {
        List<Game> wished = safely("wished", this::mostWishedGames);
        List<Game> weekly = safely("weekly", () -> weeklyRail(wished));
        Set<String> used = idsOf(weekly);
        List<Game> favorites = safely("favorites", () -> favoritesRail(wished, used));
        used.addAll(idsOf(favorites));
        List<Game> latest = safely("latest", () -> latestRail(used));
        return new GameRailsDTO(weekly, favorites, latest);
    }

    // un fallimento in uno scaffale (es. Neo4j giu') non deve portarsi via gli altri
    private <T> List<T> safely(String rail, Supplier<List<T>> supplier) {
        try {
            return supplier.get();
        } catch (Exception e) {
            log.error("Errore nel calcolo dello scaffale {}", rail, e);
            return new ArrayList<>();
        }
    }

    // Giochi piu' presenti nelle wishlist di sempre, dal piu' desiderato. Solo quelli con una
    // copertina: una card senza immagine in uno scaffale fa brutto.
    private List<Game> mostWishedGames() {
        List<String> ids =
                gameNeo4jRepository.findMostWishlistedGames(WISHED_POOL_SIZE).stream()
                        .map(GameNeo4j::getId)
                        .filter(Objects::nonNull)
                        .toList();
        return inOrder(ids, gameRepository.findAllById(ids));
    }

    // Settimana: wishlist e recensioni degli ultimi 7 giorni. Il registro delle attivita' nasce
    // con il feed, quindi in una community giovane (o dopo un periodo tranquillo) i giochi con
    // movimento reale sono pochi: lo scaffale si completa con i piu' desiderati di sempre, cosi
    // non resta mai mezzo vuoto.
    private List<Game> weeklyRail(List<Game> wished) {
        List<Game> rail = new ArrayList<>(weeklyMovers());
        Set<String> present = idsOf(rail);
        for (Game game : wished) {
            if (rail.size() >= RAIL_SIZE) {
                break;
            }
            if (present.add(game.getId())) {
                rail.add(game);
            }
        }
        return trimmed(rail.subList(0, Math.min(rail.size(), RAIL_SIZE)));
    }

    private List<Game> weeklyMovers() {
        Aggregation aggregation =
                Aggregation.newAggregation(
                        Aggregation.match(
                                Criteria.where("type")
                                        .in(
                                                ActivityType.WISHLIST_ADD.name(),
                                                ActivityType.REVIEW.name())
                                        .and("createdAt")
                                        .gte(Date.from(Instant.now().minus(WEEK)))
                                        .and("gameName")
                                        .ne(null)),
                        Aggregation.group("gameName", "type").count().as("count"));
        Map<String, Integer> scores = new HashMap<>();
        for (Document row :
                mongoTemplate
                        .aggregate(aggregation, "activities", Document.class)
                        .getMappedResults()) {
            Document key = row.get("_id", Document.class);
            if (key == null || key.getString("gameName") == null) {
                continue;
            }
            int weight =
                    ActivityType.REVIEW.name().equals(key.getString("type")) ? REVIEW_WEIGHT : 1;
            scores.merge(
                    key.getString("gameName"),
                    weight * ((Number) row.get("count")).intValue(),
                    Integer::sum);
        }
        if (scores.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> names =
                scores.entrySet().stream()
                        .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                        .map(Map.Entry::getKey)
                        .toList();
        // i nomi dei giochi non sono univoci nel dataset: si prende il primo con copertina
        Map<String, Game> byName = new LinkedHashMap<>();
        for (Game game : gameRepository.findByNameIn(names)) {
            if (hasCover(game)) {
                byName.putIfAbsent(game.getName(), game);
            }
        }
        List<Game> movers = new ArrayList<>();
        for (String name : names) {
            Game game = byName.get(name);
            if (game != null) {
                movers.add(game);
            }
        }
        return movers;
    }

    // Preferiti: tra i giochi che la community desidera di piu', quelli col voto piu' alto (a pari
    // voto resta l'ordine di popolarita'). Se non bastano, si completa con i giochi che hanno
    // recensioni vere, dal voto piu' alto.
    private List<Game> favoritesRail(List<Game> wished, Set<String> alreadyShown) {
        List<Game> rail =
                new ArrayList<>(
                        wished.stream()
                                .filter(g -> g.getAvgScore() >= FAVORITE_MIN_SCORE)
                                .filter(g -> !alreadyShown.contains(g.getId()))
                                .sorted(Comparator.comparingInt(Game::getAvgScore).reversed())
                                .limit(RAIL_SIZE)
                                .toList());

        if (rail.size() < RAIL_SIZE) {
            Set<String> present = idsOf(rail);
            for (Game game :
                    gameRepository.findGamesWithReviews(PageRequest.of(0, WISHED_POOL_SIZE))) {
                if (rail.size() >= RAIL_SIZE) {
                    break;
                }
                if (hasCover(game)
                        && game.getAvgScore() >= FAVORITE_MIN_SCORE
                        && !alreadyShown.contains(game.getId())
                        && present.add(game.getId())) {
                    rail.add(game);
                }
            }
        }
        return trimmed(rail);
    }

    private List<Game> latestRail(Set<String> alreadyShown) {
        // qualche margine sul limite, per poter scartare i giochi gia' mostrati piu' in alto
        List<String> ids =
                gameRepository.findLatestReleasedGameIds(RAIL_SIZE + alreadyShown.size());
        List<Game> rail =
                inOrder(ids, gameRepository.findAllById(ids)).stream()
                        .filter(g -> !alreadyShown.contains(g.getId()))
                        .limit(RAIL_SIZE)
                        .toList();
        return trimmed(rail);
    }

    // findAllById non garantisce l'ordine di ingresso: lo si ripristina, scartando i giochi
    // senza copertina
    private List<Game> inOrder(List<String> ids, Collection<Game> games) {
        Map<String, Game> byId = new HashMap<>();
        for (Game game : games) {
            byId.put(game.getId(), game);
        }
        List<Game> ordered = new ArrayList<>();
        for (String id : ids) {
            Game game = byId.get(id);
            if (game != null && hasCover(game)) {
                ordered.add(game);
            }
        }
        return ordered;
    }

    private boolean hasCover(Game game) {
        return game.getURL() != null
                && game.getURL().getHeaderImage() != null
                && !game.getURL().getHeaderImage().isBlank();
    }

    private Set<String> idsOf(Collection<Game> games) {
        Set<String> ids = new HashSet<>();
        for (Game game : games) {
            ids.add(game.getId());
        }
        return ids;
    }

    // Una card non ha bisogno di descrizione, recensioni embedded, lingue... che da sole
    // pesano piu' di tutto il resto. Si lavora su copie: i documenti letti non vengono mai
    // risalvati, ma non si vuole comunque toccare l'originale.
    private List<Game> trimmed(List<Game> games) {
        List<Game> light = new ArrayList<>(games.size());
        for (Game game : games) {
            Game copy = new Game();
            copy.setId(game.getId());
            copy.setName(game.getName());
            copy.setGenres(game.getGenres());
            copy.setReleaseDate(game.getReleaseDate());
            copy.setAvgScore(game.getAvgScore());
            copy.setPrice(game.getPrice());
            URL url = new URL();
            url.setHeaderImage(game.getURL().getHeaderImage());
            copy.setURL(url);
            light.add(copy);
        }
        return light;
    }
}
