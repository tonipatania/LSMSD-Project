package it.unipi.lsmsd.gamehub.repository.MongoDBAggregation;

import static org.assertj.core.api.Assertions.assertThat;

import it.unipi.lsmsd.gamehub.model.Game;
import it.unipi.lsmsd.gamehub.support.IntegrationTestSupport;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

class GameRepositoryImplIT extends IntegrationTestSupport {

    @Autowired private GameRepositoryImpl gameRepositoryImpl;

    private Game game(String name, String genres, int avgScore) {
        Game game = new Game();
        game.setName(name);
        game.setGenres(genres);
        game.setReleaseDate("Oct 21, 2008");
        game.setAvgScore(avgScore);
        return game;
    }

    @Test
    void searchGames_genreFilter_matchesWholeCommaTokenNotLooseSubstring() {
        // "RPG" must match a comma-delimited "RPG" token but not a "RPGaction" token that merely
        // contains the substring "RPG" - see the genre-quirk note in the backend-tests skill.
        mongoTemplate.save(game("Whole Token Match", "RPG,Action", 80));
        mongoTemplate.save(game("Substring Only", "RPGaction", 80));

        Page<Game> result =
                gameRepositoryImpl.searchGames(null, List.of("RPG"), null, PageRequest.of(0, 10));

        assertThat(result.getContent())
                .extracting(Game::getName)
                .containsExactly("Whole Token Match");
    }

    @Test
    void searchGames_avgScoreFilter_isInclusiveGreaterThanOrEqual() {
        mongoTemplate.save(game("Low Score", "Action", 50));
        mongoTemplate.save(game("Exact Threshold", "Action", 70));
        mongoTemplate.save(game("High Score", "Action", 90));

        Page<Game> result = gameRepositoryImpl.searchGames(null, null, 70, PageRequest.of(0, 10));

        assertThat(result.getContent())
                .extracting(Game::getName)
                .containsExactlyInAnyOrder("Exact Threshold", "High Score");
    }

    @Test
    void searchGames_pagination_reportsRealTotalAcrossPages() {
        for (int i = 0; i < 3; i++) {
            mongoTemplate.save(game("Game " + i, "Action", 80));
        }

        Page<Game> firstPage =
                gameRepositoryImpl.searchGames(null, null, null, PageRequest.of(0, 2));

        assertThat(firstPage.getContent()).hasSize(2);
        assertThat(firstPage.getTotalElements()).isEqualTo(3);
        assertThat(firstPage.getTotalPages()).isEqualTo(2);
    }

    @Test
    void findDistinctGenres_splitsTrimsAndDedupesCommaSeparatedGenres() {
        mongoTemplate.save(game("Game A", "RPG, Action", 80));
        mongoTemplate.save(game("Game B", "Action,Indie", 70));
        mongoTemplate.save(game("Game C", null, 60));

        List<String> genres = gameRepositoryImpl.findDistinctGenres();

        assertThat(genres).containsExactlyInAnyOrder("RPG", "Action", "Indie");
    }
}
