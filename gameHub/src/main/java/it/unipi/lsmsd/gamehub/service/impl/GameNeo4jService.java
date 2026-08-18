package it.unipi.lsmsd.gamehub.service.impl;

import it.unipi.lsmsd.gamehub.model.Game;
import it.unipi.lsmsd.gamehub.model.GameNeo4j;
import it.unipi.lsmsd.gamehub.repository.GameNeo4jRepository;
import it.unipi.lsmsd.gamehub.repository.GameRepository;
import it.unipi.lsmsd.gamehub.service.IGameNeo4jService;
import java.time.Duration;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class GameNeo4jService implements IGameNeo4jService {
    @Autowired private GameNeo4jRepository gameNeo4jRepository;
    @Autowired private GameRepository gameRepository;
    @Autowired private RedisTemplate<String, Object> redisTemplate;

    @Override
    public Integer getGamesIngoingLinks(String name) {
        try {
            return gameNeo4jRepository.findGameIngoingLinks(name);
        } catch (Exception e) {
            log.error("Errore in getGamesIngoingLinks", e);
            return null;
        }
    }

    // numero di giochi consigliati mostrati nella sidebar della Home
    private static final int SUGGESTIONS_LIMIT = 10;

    private static final String GAMES_CACHE_KEY_PREFIX = "gamehub:suggestions:games:";
    // stesso TTL dei suggerimenti amici (UserNeo4jService): personalizzata per utente, quindi una
    // staleness lunga sarebbe visibile (es. un gioco appena aggiunto alla wishlist che dovrebbe
    // cambiare i consigli).
    private static final Duration GAMES_CACHE_TTL = Duration.ofMinutes(2);

    @Override
    public ResponseEntity<List<Game>> getSuggestGames(String username) {
        try {
            String cacheKey = GAMES_CACHE_KEY_PREFIX + username;
            List<Game> cached = readGamesCache(cacheKey);
            if (cached != null) {
                return new ResponseEntity<>(cached, HttpStatus.OK);
            }

            // Consiglio basato su tutta la wishlist. Se la wishlist e' vuota si ripiega sui giochi
            // piu desiderati: la vecchia versione partiva dal gioco con la media recensioni piu
            // alta, che nel dataset e' quasi sempre un titolo di nicchia con zero wishlist, quindi
            // il suggerimento risultava sistematicamente vuoto.
            List<GameNeo4j> games =
                    gameNeo4jRepository.findSuggestGames(username, SUGGESTIONS_LIMIT);
            if (games.isEmpty()) {
                games = gameNeo4jRepository.findMostWishlistedGames(SUGGESTIONS_LIMIT);
            }

            // GameNeo4j only carries id+name; fetch the full Mongo documents (image, genres,
            // score) for the card UI, since the ids are shared between the two stores
            List<String> ids = games.stream().map(GameNeo4j::getId).toList();
            List<Game> enrichedGames = gameRepository.findAllById(ids);

            writeGamesCache(cacheKey, enrichedGames);
            return new ResponseEntity<>(enrichedGames, HttpStatus.OK);
        } catch (Exception e) {
            log.error("Errore durante l accesso al database", e);
            return new ResponseEntity<>(null, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    // La cache su Redis e' un'ottimizzazione, non la fonte di verita' (che restano Neo4j/Mongo): se
    // Redis non e' raggiungibile i suggerimenti devono comunque funzionare, solo un po' piu lenti.
    @SuppressWarnings("unchecked")
    private List<Game> readGamesCache(String key) {
        try {
            Object cached = redisTemplate.opsForValue().get(key);
            return cached == null ? null : (List<Game>) cached;
        } catch (Exception e) {
            log.warn("Redis non raggiungibile in lettura per la chiave {}", key, e);
            return null;
        }
    }

    private void writeGamesCache(String key, List<Game> value) {
        try {
            redisTemplate.opsForValue().set(key, value, GAMES_CACHE_TTL);
        } catch (Exception e) {
            log.warn("Redis non raggiungibile in scrittura per la chiave {}", key, e);
        }
    }

    public ResponseEntity<String> removeGame(String gameId) {
        try {
            gameNeo4jRepository.removeGame(gameId);
            return new ResponseEntity<>("game deleted", HttpStatus.OK);
        } catch (Exception e) {
            log.error("Errore in removeGame", e);
            return new ResponseEntity<>(
                    "deletion error: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    public ResponseEntity<String> addGame(String id, String name) {
        try {
            gameNeo4jRepository.addGame(id, name);
            return new ResponseEntity<>("game inserted correctly", HttpStatus.CREATED);
        } catch (Exception e) {
            log.error("Errore in addGame", e);
            return new ResponseEntity<>(
                    "error inserted game in neo4j: " + e.getMessage(),
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}
