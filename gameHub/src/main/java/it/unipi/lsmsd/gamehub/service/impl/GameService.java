package it.unipi.lsmsd.gamehub.service.impl;

import lombok.extern.slf4j.Slf4j;

import it.unipi.lsmsd.gamehub.DTO.GameDTO;
import it.unipi.lsmsd.gamehub.DTO.GameDTOAggregation;
import it.unipi.lsmsd.gamehub.DTO.GameDTOAggregation2;
import it.unipi.lsmsd.gamehub.model.Game;
import it.unipi.lsmsd.gamehub.model.Review;
import it.unipi.lsmsd.gamehub.repository.GameRepository;
import it.unipi.lsmsd.gamehub.repository.ReviewRepository;
import it.unipi.lsmsd.gamehub.service.IGameService;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@Slf4j
public class GameService implements IGameService {

    @Autowired
    private GameRepository gameRepository;

    @Autowired
    private ReviewRepository reviewRepository;


    @Override
    public Page<Game> retrieveGamesByParameters(String name, List<String> genres, Integer avgScore, Pageable pageable) {
        try {
            if ((name == null || name.isBlank()) && (genres == null || genres.isEmpty()) && avgScore == null) {
                return new PageImpl<>(Collections.emptyList(), pageable, 0);
            }
            return gameRepository.searchGames(name, genres, avgScore, pageable);
        } catch (Exception e) {
            log.error("Errore in retrieveGamesByParameters", e);
            return new PageImpl<>(Collections.emptyList(), pageable, 0);
        }
    }

    @Override
    public List<String> findDistinctGenres() {
        try {
            return gameRepository.findDistinctGenres();
        } catch (Exception e) {
            log.error("Errore in findDistinctGenres", e);
            return null;
        }
    }

    @Override
    public List<GameDTOAggregation> retrieveAggregateGamesByGenresAndSortByScore() {
        try {
            List<GameDTOAggregation> gameList = gameRepository.findAggregation();
            return gameList;
        } catch (Exception e) {
            log.error("Errore in retrieveAggregateGamesByGenresAndSortByScore", e);
            return null;
        }
    }

    @Override
    public List<GameDTOAggregation2> findAggregation4() {
        try {
            List<GameDTOAggregation2> gameList = gameRepository.findAggregation4();
            return gameList;
        } catch (Exception e) {
            log.error("Errore in findAggregation4", e);
            return null;
        }
    }

    @Override
    public List<Game> getGamesWithReviews(int limit) {
        try {
            return gameRepository.findGamesWithReviews(PageRequest.of(0, limit));
        } catch (Exception e) {
            log.error("Errore in getGamesWithReviews", e);
            return Collections.emptyList();
        }
    }

    @Override
    public Page<Game> getAll(Pageable pageable) {
        try {
            Page<Game> games = gameRepository.findAll(pageable);
//            ModelMapper modelMapper = new ModelMapper();
//            return games.map(game -> modelMapper.map(game, GameDTO.class));
            return games;
        } catch (Exception e) {
            log.error("Errore durante il recupero dei giochi", e);
            return new PageImpl<>(Collections.emptyList(), pageable, 0);
        }
    }

    @Override
    public List<Review> updateGameReviewFromScratch(Game game, int limit) {
        try {


            Pageable pageable = PageRequest.of(0, limit);

            List<Review> top20Reviews = reviewRepository.findByTitleOrderByLikeCountDesc(game.getName(), pageable);
            List<Review> existingReviews = game.getReviews();
            if (existingReviews == null) {
                existingReviews = new ArrayList<>();
            } else {
                existingReviews.clear();  // Clear existing reviews if any
            }

            // Add the new top 20 reviews to the existing reviews
            existingReviews.addAll(top20Reviews);

            game.setReviews(existingReviews);

            // Save the updated game document
            gameRepository.save(game);
            return existingReviews;


        } catch (Exception e) {
            log.error("Errore in updateGameReviewFromScratch", e);
            return null;
        }
    }

    @Override
    public List<Review> updateGameEmbeddedReview(Game game){
        List<Review> reviews=game.getReviews();
        // Sort the list of reviews based on the likeCount field in descending order
        Collections.sort(reviews, Comparator.comparingInt(Review::getLikeCount).reversed());
        game.setReviews(reviews);
        gameRepository.save(game);
        return reviews;
    }

    @Override
    public ResponseEntity<String> createGame(GameDTO gameDTO) {
        // converto il dto in model entity
        ModelMapper modelMapper = new ModelMapper();
        Game game = modelMapper.map(gameDTO, Game.class);
        // inserisco il model nel db
        try {
            // controllo se esiste un gioco con lo stesso nome
            List<Game> existGame = gameRepository.findByName(game.getName());
            if(!existGame.isEmpty()) {
                return new ResponseEntity<>("there is already a game with the same name", HttpStatus.CONFLICT);
            }
            Game saved = gameRepository.save(game);
            // mappare model in dto
            GameDTO gameInserted = modelMapper.map(saved, GameDTO.class);
            return new ResponseEntity<>(gameInserted.getId(), HttpStatus.CREATED);
        } catch (Exception e) {
            log.error("Error in game creation", e);
            return new ResponseEntity<>("Error in game creation: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }



    @Override
    public long countGameDocument() {
        try {
            return gameRepository.count();
        } catch (Exception e) {
            log.error("Errore in countGameDocument", e);
            return -1;
        }
    }


    @Override
    public ResponseEntity<String> deleteGame(String id) {
        try {
            Optional<Game> game= gameRepository.findById(id);
            if(!game.isPresent()){
                return new ResponseEntity<>("game not deleted", HttpStatus.NOT_FOUND);
            }
            gameRepository.deleteById(id);
            return new ResponseEntity<>("game deleted", HttpStatus.OK);
        } catch (Exception e) {
            log.error("Errore in deleteGame", e);
            return new ResponseEntity<>("deletion error: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);

        }
    }
}

