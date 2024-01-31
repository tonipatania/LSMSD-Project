package it.unipi.lsmsd.gamehub.controller;

import it.unipi.lsmsd.gamehub.DTO.GameDTO;
import it.unipi.lsmsd.gamehub.DTO.GameDTOAggregation;
import it.unipi.lsmsd.gamehub.DTO.GameDTOAggregation2;
import it.unipi.lsmsd.gamehub.DTO.ReviewDTO;
import it.unipi.lsmsd.gamehub.model.Game;
import it.unipi.lsmsd.gamehub.model.Review;
import it.unipi.lsmsd.gamehub.service.IGameService;
import it.unipi.lsmsd.gamehub.service.ILoginService;
import it.unipi.lsmsd.gamehub.service.impl.GameNeo4jService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RequestMapping("game")
@RestController
public class GameController {
    @Autowired
    private IGameService gameService;

    @Autowired
    private ILoginService iLoginService;

    @Autowired
    GameNeo4jService gameNeo4jService;
    @GetMapping("/searchFilter")
    public ResponseEntity<List<Game>> retrieveGamesByParameters(@RequestBody GameDTO gameDTO) {
        List<Game> gameList = gameService.retrieveGamesByParameters(gameDTO);
        if (!gameList.isEmpty()) {
            return ResponseEntity.ok(gameList);
        }
        System.out.println("gamelist empty");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }

    @GetMapping("/aggr1")
    public ResponseEntity<List<GameDTOAggregation>> retrieveAggregateGamesByGenresAndSortByScore() {
        List<GameDTOAggregation> gameList = gameService.retrieveAggregateGamesByGenresAndSortByScore();

        if (!gameList.isEmpty()) {
            return ResponseEntity.ok(gameList);
        }
        System.out.println("gamelist empty");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }

    @GetMapping("/aggr2")
    public ResponseEntity<List<GameDTOAggregation2>> findAggregation() {
        List<GameDTOAggregation2> gameList = gameService.findAggregation4();

        if (!gameList.isEmpty()) {
            return ResponseEntity.ok(gameList);
        }
        System.out.println("gamelist empty");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }

    @GetMapping("/getAll")
    public ResponseEntity<Page<GameDTO>> showGames(@PageableDefault(sort = { "id" }, size = 50) Pageable pageable) {
        Page<GameDTO> gameDTOPage = gameService.getAll(pageable);
        if (pageable.getPageNumber() >= gameDTOPage.getTotalPages()) {
            // La pagina richiesta supera il numero massimo di pagine disponibili
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
        }
        if (gameDTOPage.isEmpty()) {
            // La pagina è vuota
            return ResponseEntity.status(HttpStatus.NO_CONTENT).body(null);
        }
        return ResponseEntity.ok(gameDTOPage);
    }

    @PatchMapping("/updateGameReview")
    public ResponseEntity<List<Review>> updateGameReview(@RequestBody ReviewDTO reviewDTO) {
        List<Review> reviewList = gameService.updateGameReview(reviewDTO,20);
        if (!reviewList.isEmpty()) {
            return ResponseEntity.ok(reviewList);
        }
        System.out.println("gamelist empty");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }

    @PostMapping("/create/{userId}")
    public ResponseEntity<String> createGame(@PathVariable String userId,@RequestBody GameDTO gameDTO) {
        // controllo se si tratta di admin
        ResponseEntity<String> responseEntity= iLoginService.roleUser(userId);
        if(responseEntity.getStatusCode() != HttpStatus.OK) {
            return responseEntity;
        }
        // aggiungo il gioco in mongo
        responseEntity = gameService.createGame(gameDTO);
        if(responseEntity.getStatusCode() != HttpStatus.CREATED)
            return responseEntity;
        // aggiungo il gioco su neo4j
        ResponseEntity<String> response = gameNeo4jService.addGame(responseEntity.getBody(), gameDTO.getName());
        if(response.getStatusCode() == HttpStatus.CREATED)
            return response;
        // eliminare gioco in mongo
        return gameService.deleteGame(responseEntity.getBody());
    }
    @DeleteMapping("/delete/{userId}")
    public ResponseEntity<String> deleteGame(@PathVariable String userId, @RequestParam String gameId) {
        // controllo se si tratta di admin
        ResponseEntity<String> responseEntity= iLoginService.roleUser(userId);
        if(responseEntity.getStatusCode() != HttpStatus.OK) {
            return responseEntity;
        }
        // cancello in mongo
        responseEntity = gameService.deleteGame(gameId);
        if(responseEntity.getStatusCode() != HttpStatus.OK) {
            return responseEntity;
        }
        // cancello in neo4j
        return gameNeo4jService.removeGame(gameId);
    }

    @GetMapping("/getGamesIngoingLinks")
    public ResponseEntity<Integer> getGamesIngoingLinks(@RequestParam String name) {
        Integer countLinks= gameNeo4jService.getGamesIngoingLinks(name);
        if (countLinks!=null) {
            return ResponseEntity.ok(countLinks);
        }
        System.out.println("gamelist empty");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }
    @GetMapping("/suggestGames/{userId}")
    public ResponseEntity<List<GameDTO>> suggestGames(@PathVariable String userId) {
        //return gameNeo4jService.getSuggestGames(userId);
        return null;
    }

}

