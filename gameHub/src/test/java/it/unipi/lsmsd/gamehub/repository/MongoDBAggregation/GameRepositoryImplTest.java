package it.unipi.lsmsd.gamehub.repository.MongoDBAggregation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import it.unipi.lsmsd.gamehub.model.Game;
import java.util.Arrays;
import java.util.List;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Query;

@ExtendWith(MockitoExtension.class)
class GameRepositoryImplTest {

    @Mock private MongoTemplate mongoTemplate;
    @InjectMocks private GameRepositoryImpl gameRepositoryImpl;

    @Captor private ArgumentCaptor<Query> queryCaptor;

    @Test
    void searchGames_noFilters_buildsEmptyQuery() {
        when(mongoTemplate.count(queryCaptor.capture(), eq(Game.class))).thenReturn(0L);
        when(mongoTemplate.find(any(Query.class), eq(Game.class))).thenReturn(List.of());

        gameRepositoryImpl.searchGames(null, null, null, PageRequest.of(0, 24));

        assertThat(queryCaptor.getValue().getQueryObject()).isEmpty();
    }

    @Test
    void searchGames_nameFilter_addsCaseInsensitiveRegexOnName() {
        when(mongoTemplate.count(queryCaptor.capture(), eq(Game.class))).thenReturn(0L);
        when(mongoTemplate.find(any(Query.class), eq(Game.class))).thenReturn(List.of());

        gameRepositoryImpl.searchGames("barrier x", null, null, PageRequest.of(0, 24));

        String json = queryCaptor.getValue().getQueryObject().toJson();
        assertThat(json)
                .contains("name")
                .contains("barrier x")
                .contains("$regularExpression")
                .contains("\"options\": \"i\"");
    }

    @Test
    void searchGames_singleGenre_matchesWholeCommaDelimitedToken() {
        when(mongoTemplate.count(queryCaptor.capture(), eq(Game.class))).thenReturn(0L);
        when(mongoTemplate.find(any(Query.class), eq(Game.class))).thenReturn(List.of());

        gameRepositoryImpl.searchGames(null, List.of("RPG"), null, PageRequest.of(0, 24));

        String json = queryCaptor.getValue().getQueryObject().toJson();
        // whole-token regex: must not be satisfiable by a loose "genres contains RPG" substring
        // match alone (e.g. must not match "RPGaction" as a token) -- assert the anchor/boundary
        // groups from GameRepositoryImpl.searchGames are present, not just the genre name.
        assertThat(json).contains("(^|,)").contains("RPG").contains("(,|$)");
    }

    @Test
    void searchGames_multipleGenres_addsOneRegexCriterionPerGenre() {
        when(mongoTemplate.count(queryCaptor.capture(), eq(Game.class))).thenReturn(0L);
        when(mongoTemplate.find(any(Query.class), eq(Game.class))).thenReturn(List.of());

        gameRepositoryImpl.searchGames(
                null, Arrays.asList("RPG", "Strategy"), null, PageRequest.of(0, 24));

        String json = queryCaptor.getValue().getQueryObject().toJson();
        assertThat(json).contains("RPG").contains("Strategy").contains("$and");
    }

    @Test
    void searchGames_blankGenreInList_isSkipped() {
        when(mongoTemplate.count(queryCaptor.capture(), eq(Game.class))).thenReturn(0L);
        when(mongoTemplate.find(any(Query.class), eq(Game.class))).thenReturn(List.of());

        gameRepositoryImpl.searchGames(
                null, Arrays.asList("RPG", "  ", null), null, PageRequest.of(0, 24));

        Document query = queryCaptor.getValue().getQueryObject();
        List<?> andClauses = query.getList("$and", Document.class);
        // the blank string and the null entry must not each contribute their own criterion
        assertThat(andClauses).hasSize(1);
        assertThat(query.toJson()).contains("RPG");
    }

    @Test
    void searchGames_avgScoreFilter_addsGreaterThanOrEqualCriterion() {
        when(mongoTemplate.count(queryCaptor.capture(), eq(Game.class))).thenReturn(0L);
        when(mongoTemplate.find(any(Query.class), eq(Game.class))).thenReturn(List.of());

        gameRepositoryImpl.searchGames(null, null, 4, PageRequest.of(0, 24));

        String json = queryCaptor.getValue().getQueryObject().toJson();
        assertThat(json).contains("avgScore").contains("$gte");
    }

    @Test
    void searchGames_returnsPageWithTotalFromCountAndContentFromFind() {
        Game game = new Game();
        game.setId("g1");
        // PageImpl silently recomputes total as offset+content.size() whenever offset+pageSize
        // exceeds the given total, so pageSize must stay <= total here or the assertion below
        // would be checking Spring's correction instead of GameRepositoryImpl's own total.
        Pageable pageable = PageRequest.of(0, 5);
        when(mongoTemplate.count(any(Query.class), eq(Game.class))).thenReturn(7L);
        when(mongoTemplate.find(any(Query.class), eq(Game.class))).thenReturn(List.of(game));

        org.springframework.data.domain.Page<Game> page =
                gameRepositoryImpl.searchGames("BARRIER X", null, null, pageable);

        assertThat(page.getTotalElements()).isEqualTo(7L);
        assertThat(page.getContent()).containsExactly(game);
    }

    @Test
    void findDistinctGenres_flattensGroupedIdsIntoStringList() {
        List<Document> mapped =
                List.of(new Document("_id", "RPG"), new Document("_id", "Strategy"));
        when(mongoTemplate.aggregate(any(Aggregation.class), eq("games"), eq(Document.class)))
                .thenReturn(new AggregationResults<>(mapped, new Document()));

        List<String> result = gameRepositoryImpl.findDistinctGenres();

        assertThat(result).containsExactly("RPG", "Strategy");
    }
}
