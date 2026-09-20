package it.unipi.lsmsd.gamehub.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import it.unipi.lsmsd.gamehub.DTO.GameRailsDTO;
import it.unipi.lsmsd.gamehub.model.Game;
import it.unipi.lsmsd.gamehub.model.GameNeo4j;
import it.unipi.lsmsd.gamehub.model.URL;
import it.unipi.lsmsd.gamehub.repository.GameNeo4jRepository;
import it.unipi.lsmsd.gamehub.repository.GameRepository;
import java.util.ArrayList;
import java.util.List;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;

@ExtendWith(MockitoExtension.class)
class GameRailServiceTest {

    @Mock private GameRepository gameRepository;
    @Mock private GameNeo4jRepository gameNeo4jRepository;
    @Mock private MongoTemplate mongoTemplate;

    @InjectMocks private GameRailService railService;

    private Game game(String id, int avgScore) {
        Game game = new Game();
        game.setId(id);
        game.setName("Game " + id);
        game.setAvgScore(avgScore);
        game.setAboutTheGame("a very long description");
        game.setReviews(new ArrayList<>());
        URL url = new URL();
        url.setHeaderImage("https://img/" + id + ".jpg");
        url.setScreenshots("https://img/shot.jpg");
        game.setURL(url);
        return game;
    }

    private Game gameWithoutCover(String id, int avgScore) {
        Game game = game(id, avgScore);
        game.setURL(new URL());
        return game;
    }

    @SuppressWarnings("unchecked")
    private void noWeeklyActivity() {
        AggregationResults<Document> results = org.mockito.Mockito.mock(AggregationResults.class);
        when(results.getMappedResults()).thenReturn(List.of());
        when(mongoTemplate.aggregate(any(Aggregation.class), anyString(), any(Class.class)))
                .thenReturn(results);
    }

    private void mostWished(List<String> ids, List<Game> games) {
        when(gameNeo4jRepository.findMostWishlistedGames(anyInt()))
                .thenReturn(ids.stream().map(id -> new GameNeo4j(id, "Game " + id)).toList());
        when(gameRepository.findAllById(anyIterable())).thenReturn(games);
    }

    @Test
    void getRails_withoutWeeklyActivity_completesTheWeeklyRailWithTheMostWishedGames() {
        noWeeklyActivity();
        List<Game> wished = new ArrayList<>();
        for (int i = 1; i <= 30; i++) {
            wished.add(game("g" + i, 5));
        }
        mostWished(wished.stream().map(Game::getId).toList(), wished);
        when(gameRepository.findLatestReleasedGameIds(anyInt())).thenReturn(List.of());
        when(gameRepository.findGamesWithReviews(any(Pageable.class))).thenReturn(List.of());

        GameRailsDTO rails = railService.getRails();

        assertThat(rails.getWeekly()).hasSize(GameRailService.RAIL_SIZE);
        assertThat(rails.getWeekly().get(0).getId()).isEqualTo("g1");
    }

    @Test
    void getRails_cardsAreTrimmedToWhatTheCardNeeds() {
        noWeeklyActivity();
        List<Game> wished = List.of(game("g1", 9));
        mostWished(List.of("g1"), wished);
        when(gameRepository.findLatestReleasedGameIds(anyInt())).thenReturn(List.of());
        when(gameRepository.findGamesWithReviews(any(Pageable.class))).thenReturn(List.of());

        Game card = railService.getRails().getWeekly().get(0);

        assertThat(card.getAboutTheGame()).isNull();
        assertThat(card.getReviews()).isNull();
        assertThat(card.getURL().getHeaderImage()).isEqualTo("https://img/g1.jpg");
        assertThat(card.getURL().getScreenshots()).isNull();
    }

    @Test
    void getRails_favoritesAreHighlyRatedAndNeverRepeatTheWeeklyRail() {
        noWeeklyActivity();
        List<Game> wished = new ArrayList<>();
        // i primi 14 riempiono lo scaffale settimanale; poi ce ne sono di amati e di meno amati
        for (int i = 1; i <= 14; i++) {
            wished.add(game("w" + i, 9));
        }
        wished.add(game("low", 3));
        wished.add(game("good", 8));
        wished.add(game("best", 10));
        mostWished(wished.stream().map(Game::getId).toList(), wished);
        when(gameRepository.findLatestReleasedGameIds(anyInt())).thenReturn(List.of());
        when(gameRepository.findGamesWithReviews(any(Pageable.class))).thenReturn(List.of());

        GameRailsDTO rails = railService.getRails();

        // i w1..w14 hanno voto alto ma sono gia' nello scaffale settimanale
        assertThat(rails.getFavorites()).extracting(Game::getId).containsExactly("best", "good");
    }

    @Test
    void getRails_latestSkipsGamesAlreadyShownAndKeepsTheReleaseOrder() {
        noWeeklyActivity();
        List<Game> wished = List.of(game("g1", 9));
        mostWished(List.of("g1"), wished);
        // "g1" e' gia' nello scaffale settimanale: l'ordine delle uscite va rispettato
        when(gameRepository.findLatestReleasedGameIds(anyInt()))
                .thenReturn(List.of("n2", "g1", "n1"));
        when(gameRepository.findAllById(List.of("n2", "g1", "n1")))
                .thenReturn(List.of(game("n1", 0), game("g1", 9), game("n2", 0)));
        when(gameRepository.findGamesWithReviews(any(Pageable.class))).thenReturn(List.of());

        GameRailsDTO rails = railService.getRails();

        assertThat(rails.getLatest()).extracting(Game::getId).containsExactly("n2", "n1");
    }

    @Test
    void getRails_gamesWithoutCoverNeverAppear() {
        noWeeklyActivity();
        List<Game> wished = List.of(gameWithoutCover("nocover", 9), game("g2", 9));
        mostWished(List.of("nocover", "g2"), wished);
        when(gameRepository.findLatestReleasedGameIds(anyInt())).thenReturn(List.of());
        when(gameRepository.findGamesWithReviews(any(Pageable.class))).thenReturn(List.of());

        GameRailsDTO rails = railService.getRails();

        assertThat(rails.getWeekly()).extracting(Game::getId).containsExactly("g2");
    }

    @Test
    void getRails_secondCallWithinTheTtlIsServedFromMemory() {
        noWeeklyActivity();
        List<Game> wished = List.of(game("g1", 9));
        mostWished(List.of("g1"), wished);
        when(gameRepository.findLatestReleasedGameIds(anyInt())).thenReturn(List.of());
        when(gameRepository.findGamesWithReviews(any(Pageable.class))).thenReturn(List.of());

        railService.getRails();
        railService.getRails();

        verify(gameNeo4jRepository, times(1)).findMostWishlistedGames(anyInt());
    }

    @Test
    void getRails_expiredCache_servesTheOldRailsAtOnceAndRefreshesInTheBackground() {
        noWeeklyActivity();
        List<Game> wished = List.of(game("g1", 9));
        mostWished(List.of("g1"), wished);
        when(gameRepository.findLatestReleasedGameIds(anyInt())).thenReturn(List.of());
        when(gameRepository.findGamesWithReviews(any(Pageable.class))).thenReturn(List.of());
        railService.cacheTtl = java.time.Duration.ZERO;

        GameRailsDTO first = railService.getRails();
        GameRailsDTO second = railService.getRails();

        // la seconda risposta e' la copia scaduta, senza aspettare il ricalcolo...
        assertThat(second).isSameAs(first);
        // ...che parte comunque in background
        verify(gameNeo4jRepository, timeout(2000).times(2)).findMostWishlistedGames(anyInt());
    }

    @Test
    void getRails_oneRailFailing_doesNotTakeTheOthersDown() {
        noWeeklyActivity();
        when(gameNeo4jRepository.findMostWishlistedGames(anyInt()))
                .thenThrow(new RuntimeException("neo4j down"));
        Game brandNew = game("n1", 0);
        when(gameRepository.findLatestReleasedGameIds(anyInt())).thenReturn(List.of("n1"));
        when(gameRepository.findAllById(anyList())).thenReturn(List.of(brandNew));
        when(gameRepository.findGamesWithReviews(any(Pageable.class))).thenReturn(List.of());

        GameRailsDTO rails = railService.getRails();

        assertThat(rails.getWeekly()).isEmpty();
        assertThat(rails.getLatest()).extracting(Game::getId).containsExactly("n1");
    }

    @Test
    void getRails_everythingFailing_returnsEmptyRailsAndRetriesNextTime() {
        when(gameNeo4jRepository.findMostWishlistedGames(anyInt()))
                .thenThrow(new RuntimeException("neo4j down"));
        when(mongoTemplate.aggregate(any(Aggregation.class), anyString(), any(Class.class)))
                .thenThrow(new RuntimeException("mongo down"));
        when(gameRepository.findLatestReleasedGameIds(anyInt()))
                .thenThrow(new RuntimeException("mongo down"));
        when(gameRepository.findGamesWithReviews(any(Pageable.class)))
                .thenThrow(new RuntimeException("mongo down"));

        GameRailsDTO first = railService.getRails();
        railService.getRails();

        assertThat(first.getWeekly()).isEmpty();
        assertThat(first.getFavorites()).isEmpty();
        assertThat(first.getLatest()).isEmpty();
        // il vuoto non si memorizza: la seconda richiesta ci riprova
        verify(gameNeo4jRepository, times(2)).findMostWishlistedGames(anyInt());
    }
}
