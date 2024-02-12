package it.unipi.lsmsd.gamehub.controller;

import it.unipi.lsmsd.gamehub.DTO.GameDTO;
import it.unipi.lsmsd.gamehub.DTO.GameDTOAggregation;
import it.unipi.lsmsd.gamehub.DTO.GameDTOAggregation2;
import it.unipi.lsmsd.gamehub.DTO.ReviewDTO;
import it.unipi.lsmsd.gamehub.model.Game;
import it.unipi.lsmsd.gamehub.model.GameNeo4j;
import it.unipi.lsmsd.gamehub.model.Review;
import it.unipi.lsmsd.gamehub.service.IGameNeo4jService;
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
    private GameNeo4jService gameNeo4jService;


    /*Postman parameters
    {
        "name": "BARRIER X",
        "genres":"Strategy",
        "avgScore":5
    }*/
    @GetMapping("/searchFilter")
    public ResponseEntity<Object> retrieveGamesByParameters(@RequestBody GameDTO gameDTO) {
        List<Game> gameList = gameService.retrieveGamesByParameters(gameDTO);
        if (gameList!=null && !gameList.isEmpty()) {
            return ResponseEntity.ok(gameList);
        }else if(gameList!=null && gameList.isEmpty()) {
            return ResponseEntity.ok("gameList empty");

        }

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }


    //  average score for each genre - descending order
    @GetMapping("/gameAggr1")
    public ResponseEntity<Object> retrieveAggregateGamesByGenresAndSortByScore() {
        List<GameDTOAggregation> gameList = gameService.retrieveAggregateGamesByGenresAndSortByScore();

        if (gameList!=null && !gameList.isEmpty()) {
            return ResponseEntity.ok(gameList);
        }else if(gameList!=null && gameList.isEmpty()) {
            return ResponseEntity.ok("gameList empty");
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }


    // average score for each year - descending order
    @GetMapping("/gameAggr2")
    public ResponseEntity<Object> findAggregation() {
        List<GameDTOAggregation2> gameList = gameService.findAggregation4();

        if (gameList!=null && !gameList.isEmpty()) {
            return ResponseEntity.ok(gameList);
        }else if(gameList!=null && gameList.isEmpty()) {
            return ResponseEntity.ok("gameList empty");
        }

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }


    @GetMapping("/getAll")
    public ResponseEntity<Page<Game>> showGames(@PageableDefault(sort = { "id" }, size = 50) Pageable pageable) {
        Page<Game> gameDTOPage = gameService.getAll(pageable);
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


    //admin function
    /*Postman parameters
    {
        "name": "Prova",
            "releaseDate": "Domani",
            "price": 40,
            "aboutTheGame": "Bellissimo",
            "supportedLanguages": "Italian, Sicilian",
            "developers": "Tonivico",
            "publishers": "Tonivico",
            "categories": "Tutte",
            "genres": "Tutti"
    }*/
    @PostMapping("/create/{userId}")
    public ResponseEntity<String> createGame(@PathVariable String userId,@RequestBody GameDTO gameDTO) {
        // check if the admin perform the operation
        ResponseEntity<String> responseEntity= iLoginService.roleUser(userId);
        if(responseEntity.getStatusCode() != HttpStatus.OK) {
            return responseEntity;
        }
        // add game in mongo
        responseEntity = gameService.createGame(gameDTO);
        if(responseEntity.getStatusCode() != HttpStatus.CREATED)
            return responseEntity;
        // add game in neo4j
        ResponseEntity<String> response = gameNeo4jService.addGame(responseEntity.getBody(), gameDTO.getName());
        if(response.getStatusCode() == HttpStatus.CREATED)
            return response;
        // delete game in mongo if is not created in neo4j
        return gameService.deleteGame(responseEntity.getBody());
    }

    //admin function
    @DeleteMapping("gameSelected/delete/{userId}")
    public ResponseEntity<String> deleteGame(@PathVariable String userId, @RequestParam String gameId) {
        // check if the admin perform the operation
        ResponseEntity<String> responseEntity= iLoginService.roleUser(userId);
        if(responseEntity.getStatusCode() != HttpStatus.OK) {
            return responseEntity;
        }
        // delete in mongo
        responseEntity = gameService.deleteGame(gameId);
        if(responseEntity.getStatusCode() != HttpStatus.OK) {
            return responseEntity;
        }
        // delete in neo4j
        return gameNeo4jService.removeGame(gameId);
    }


    //admin function
    @GetMapping("/countGame/{userId}")
    public ResponseEntity<Object> countGame(@PathVariable String userId){
        //check if the admin perform the operation
        ResponseEntity<String> responseEntity= iLoginService.roleUser(userId);
        if(responseEntity.getStatusCode() != HttpStatus.OK) {
            return ResponseEntity.status(responseEntity.getStatusCode()).body(responseEntity.getBody());
        }

        long count= gameService.countGameDocument();
        return ResponseEntity.ok(count);
    }

    //admin function
    @GetMapping("gameSelected/getGamesIngoingLinks/{userId}")
    public ResponseEntity<Object> getGamesIngoingLinks(@PathVariable String userId, @RequestParam String name) {
        // check if the admin perform the operation
        ResponseEntity<String> responseEntity= iLoginService.roleUser(userId);
        if(responseEntity.getStatusCode() != HttpStatus.OK) {
            //return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            return ResponseEntity.status(responseEntity.getStatusCode()).body(responseEntity.getBody());
        }
        Integer countLinks= gameNeo4jService.getGamesIngoingLinks(name);
        if (countLinks!=null) {
            return ResponseEntity.ok(countLinks);
        }


        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }


    @GetMapping("/suggestGames/{username}")
    public ResponseEntity<List<GameNeo4j>> suggestGames(@PathVariable String username) {
        return gameNeo4jService.getSuggestGames(username);
    }
}

