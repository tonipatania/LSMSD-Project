package it.unipi.lsmsd.gamehub.controller;

import it.unipi.lsmsd.gamehub.DTO.GameDTO;
import it.unipi.lsmsd.gamehub.DTO.GameDTOAggregation;
import it.unipi.lsmsd.gamehub.DTO.GameDTOAggregation2;
import it.unipi.lsmsd.gamehub.DTO.ReviewDTO;
import it.unipi.lsmsd.gamehub.model.Game;
import it.unipi.lsmsd.gamehub.model.Review;
import it.unipi.lsmsd.gamehub.service.IGameService;
import it.unipi.lsmsd.gamehub.service.ILoginService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class GameController {
    @Autowired
    private IGameService gameService;

    @Autowired
    private ILoginService iLoginService;

    @GetMapping("/game")
    public ResponseEntity<List<Game>> retrieveGamesByParameters(@RequestBody GameDTO gameDTO) {
        List<Game> gameList = gameService.retrieveGamesByParameters(gameDTO);
        if (!gameList.isEmpty()) {
            return ResponseEntity.ok(gameList);
        }
        System.out.println("gamelist empty");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }

    @GetMapping("/gameAggr1")
    public ResponseEntity<List<GameDTOAggregation>> retrieveAggregateGamesByGenresAndSortByScore() {
        List<GameDTOAggregation> gameList = gameService.retrieveAggregateGamesByGenresAndSortByScore();

        if (!gameList.isEmpty()) {
            return ResponseEntity.ok(gameList);
        }
        System.out.println("gamelist empty");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }

    @GetMapping("/gameAggr2")
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

    @GetMapping("/updateGameReview")
    public ResponseEntity<List<Review>> updateGameReview(@RequestBody ReviewDTO reviewDTO) {
        List<Review> reviewList = gameService.updateGameReview(reviewDTO,20);
        if (!reviewList.isEmpty()) {
            return ResponseEntity.ok(reviewList);
        }
        System.out.println("gamelist empty");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }

    //FUNZIONE PER UPDATE DI TUTTI I GIOCHI
    /*@PostMapping("/updateAllGameReview")
    public ResponseEntity<Integer> updateAllGameReview(@PageableDefault(sort = { "id" }, size = 1000) Pageable pageable) {
        int successfulUpdates=0;
        Page<GameDTO> gameDTOPage = gameService.getAll(pageable);
        for (GameDTO gameDTO : gameDTOPage.getContent()) {
            String gameName = gameDTO.getName();
            ReviewDTO reviewDTO=new ReviewDTO();
            reviewDTO.setTitle(gameName);

            List<Review> reviewList = gameService.updateGameReview(reviewDTO,20);
            if (!reviewList.isEmpty()) {
                successfulUpdates++;
            }

        }
        if (successfulUpdates > 0) {
            return ResponseEntity.ok(successfulUpdates);
        } else {
            System.out.println("No successful updates");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }*/

    @PostMapping("/create/{userId}")
    public ResponseEntity<Object> createGame(@PathVariable String userId,@RequestBody GameDTO gameDTO) {
        // controllo se si tratta di admin
        ResponseEntity<Object> responseEntity= iLoginService.roleUser(userId);
        if(responseEntity.getStatusCode() == HttpStatus.UNAUTHORIZED) {
            return responseEntity;
        }
        else if (responseEntity.getStatusCode() == HttpStatus.INTERNAL_SERVER_ERROR) {
            return responseEntity;
        }

        GameDTO game = gameService.createGame(gameDTO);
        return new ResponseEntity<>(game, HttpStatus.CREATED);
    }
    @DeleteMapping("/delete/{userId}")
    public ResponseEntity<Object> deleteGame(@PathVariable String userId, @RequestParam String gameId) {
        // controllo se si tratta di admin
        ResponseEntity<Object> responseEntity= iLoginService.roleUser(userId);
        if(responseEntity.getStatusCode() == HttpStatus.UNAUTHORIZED) {
            return responseEntity;
        }
        else if (responseEntity.getStatusCode() == HttpStatus.INTERNAL_SERVER_ERROR) {
            return responseEntity;
        }

        gameService.deleteGame(gameId);
        return ResponseEntity.ok().build();
    }

    //DA AGGIUNGERE NEL MAIN-> CONTA IL NUMERO TOTALE DI GIOCHI E PUò FARLO SOLO L'ADMIN(AGGIUNGERE PARTI ANCHE DEL SERVICE)
    @GetMapping("/countGame/{userId}")
    public ResponseEntity<Object> countGame(@PathVariable String userId){
        ResponseEntity<Object> responseEntity= iLoginService.roleUser(userId);
        if(responseEntity.getStatusCode() == HttpStatus.UNAUTHORIZED) {
            return responseEntity;
        }
        else if (responseEntity.getStatusCode() == HttpStatus.INTERNAL_SERVER_ERROR) {
            return responseEntity;
        }

        long count= gameService.countGameDocument();
        return ResponseEntity.ok(count);
    }


}

