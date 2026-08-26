package it.unipi.lsmsd.gamehub.e2e;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItems;

import io.restassured.http.ContentType;
import it.unipi.lsmsd.gamehub.DTO.RegistrationDTO;
import it.unipi.lsmsd.gamehub.model.Game;
import it.unipi.lsmsd.gamehub.support.E2ETestSupport;
import org.junit.jupiter.api.Test;

// GET /game/searchFilter and /game/genres over real HTTP, with a real authenticated user - see
// GameRepositoryImplIT for the lower-level, repository-only coverage of the genre-matching and
// pagination-total quirks this endpoint relies on.
class GameSearchJourneyE2EIT extends E2ETestSupport {

    private Game game(String name, String genres, int avgScore) {
        Game game = new Game();
        game.setName(name);
        game.setGenres(genres);
        game.setReleaseDate("Oct 21, 2008");
        game.setAvgScore(avgScore);
        return game;
    }

    @Test
    void searchFilter_requiresAuthentication_thenFiltersByGenreAndScoreWithPagination() {
        mongoTemplate.save(game("Whole Token Match", "RPG,Action", 80));
        mongoTemplate.save(game("Substring Only", "RPGaction", 80));
        mongoTemplate.save(game("Low Score RPG", "RPG", 40));

        anonymous().queryParam("genres", "RPG").get("/game/searchFilter").then().statusCode(401);

        anonymous()
                .contentType(ContentType.JSON)
                .body(new RegistrationDTO("Kai", "Stlin", "Kaistlin", "Passw0rd!", "kai@test.it"))
                .post("/signup")
                .then()
                .statusCode(201);

        authenticatedAs("Kaistlin", "USER")
                .queryParam("genres", "RPG")
                .queryParam("avgScore", 70)
                .queryParam("page", 0)
                .queryParam("size", 10)
                .get("/game/searchFilter")
                .then()
                .statusCode(200)
                .body("totalElements", equalTo(1))
                .body("content[0].name", equalTo("Whole Token Match"));
    }

    @Test
    void genres_returnsDistinctTrimmedGenresAcrossGames() {
        mongoTemplate.save(game("Game A", "RPG, Action", 80));
        mongoTemplate.save(game("Game B", "Action,Indie", 70));

        anonymous()
                .contentType(ContentType.JSON)
                .body(new RegistrationDTO("Kai", "Stlin", "Kaistlin", "Passw0rd!", "kai@test.it"))
                .post("/signup")
                .then()
                .statusCode(201);

        authenticatedAs("Kaistlin", "USER")
                .get("/game/genres")
                .then()
                .statusCode(200)
                .body("$", hasItems("RPG", "Action", "Indie"));
    }
}
