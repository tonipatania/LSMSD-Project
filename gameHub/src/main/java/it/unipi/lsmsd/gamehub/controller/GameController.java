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

    //tengo locale
    @GetMapping("/searchFilter/{userId}")
    public ResponseEntity<Object> retrieveGamesByParameters(@PathVariable String userId,@RequestBody GameDTO gameDTO) {
        List<Game> gameList = gameService.retrieveGamesByParameters(gameDTO);
        if (gameList!=null && !gameList.isEmpty()) {
            return ResponseEntity.ok(gameList);
        }else if(gameList!=null && gameList.isEmpty()) {
            return ResponseEntity.ok("gameList empty");

        }

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }


    //tengo locale
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


    //tengo locale
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


    @GetMapping("/getAll/{userId}")
    public ResponseEntity<Page<Game>> showGames(@PathVariable String userId, @PageableDefault(sort = { "id" }, size = 50) Pageable pageable) {
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


    // funzione admin
    //remember to put 'title' in the Postman body
    @PatchMapping("gameSelected/updateGameReview/{userId}")
    public ResponseEntity<Object> updateGameReview(@PathVariable String userId, @RequestBody ReviewDTO reviewDTO) {
        // controllo se si tratta di admin
        ResponseEntity<String> responseEntity= iLoginService.roleUser(userId);
        if(responseEntity.getStatusCode() != HttpStatus.OK) {
            return ResponseEntity.status(responseEntity.getStatusCode()).build();
        }
        List<Review> reviewList = gameService.updateGameReview(reviewDTO,20);
        if (reviewList!=null && !reviewList.isEmpty()) {
            return ResponseEntity.ok(reviewList);
        }else if(reviewList!=null && reviewList.isEmpty()) {
            return ResponseEntity.ok("reviewList empty");
        }

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }


    // funzione admin
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

    //funzione admin
    @DeleteMapping("gameSelected/delete/{userId}")
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


    //DA AGGIUNGERE NEL MAIN-> CONTA IL NUMERO TOTALE DI GIOCHI E PUò FARLO SOLO L'ADMIN(AGGIUNGERE PARTI ANCHE DEL SERVICE)
    //tengo locale
    @GetMapping("/countGame/{userId}")
    public ResponseEntity<Object> countGame(@PathVariable String userId){
        ResponseEntity<String> responseEntity= iLoginService.roleUser(userId);
        if(responseEntity.getStatusCode() != HttpStatus.OK) {
            return ResponseEntity.status(responseEntity.getStatusCode()).body(responseEntity.getBody());
        }
        /*ResponseEntity<Object> responseEntity= iLoginService.roleUser(userId);
        if(responseEntity.getStatusCode() == HttpStatus.UNAUTHORIZED) {
            return responseEntity;
        }
        else if (responseEntity.getStatusCode() == HttpStatus.INTERNAL_SERVER_ERROR) {
            return responseEntity;
        }*/

        long count= gameService.countGameDocument();
        return ResponseEntity.ok(count);
    }

    //tengo locale

    @GetMapping("/getGamesIngoingLinks")
    public ResponseEntity<Integer> getGamesIngoingLinks(@RequestParam String name) {
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

