package it.unipi.lsmsd.gamehub.service.impl;


import it.unipi.lsmsd.gamehub.DTO.GameDTO;
import it.unipi.lsmsd.gamehub.DTO.ReviewDTO;
import it.unipi.lsmsd.gamehub.DTO.SuggestedUserDTO;
import it.unipi.lsmsd.gamehub.model.*;


import it.unipi.lsmsd.gamehub.repository.*;
import it.unipi.lsmsd.gamehub.service.IGameService;
import it.unipi.lsmsd.gamehub.service.IUserNeo4jService;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

import static java.util.Arrays.stream;

@Service
public class UserNeo4jService implements IUserNeo4jService {
    @Autowired
    private UserNeo4jRepository userNeo4jRepository;
    @Autowired
    private LoginRepository loginRepository;
    @Autowired
    private GameRepository gameRepository;
    @Autowired
    private GameNeo4jRepository gameNeo4jRepository;

    @Autowired
    private ReviewRepository reviewRepository;

    // numero di suggerimenti mostrati nella sidebar della Home
    private static final int SUGGESTIONS_LIMIT = 10;
    private static final int POPULAR_POOL_SIZE = 100;
    private static final long POPULAR_CACHE_TTL_MS = 10 * 60 * 1000L;

    private volatile List<SuggestedUserDTO> popularUsersCache;
    private volatile long popularUsersCachedAt;

    @Autowired
    private IGameService gameService;

    @Override
    public void SyncUser() {
        List<User> usersMongo = loginRepository.findAll();
        ModelMapper modelMapper = new ModelMapper();
        List<UserNeo4j> userNeo4js = usersMongo.stream().map(User -> modelMapper.map(User, UserNeo4j.class)).toList();
        userNeo4jRepository.saveAll(userNeo4js);
    }

    public void loadGames() {
        List<Game> games = gameRepository.findAll();
        ModelMapper modelMapper = new ModelMapper();
        List<GameNeo4j> graphGames = games.stream().map(Game -> modelMapper.map(Game, GameNeo4j.class)).toList();
        gameNeo4jRepository.saveAll(graphGames);
    }



    @Override
    public List<Game> getUserWishlist(String username,String friendUsername) {
        try {
            // friendUsername assente = "voglio la mia wishlist". La pagina Wishlist non manda il
            // parametro, quindi arrivava null: il confronto falliva, si finiva nel ramo "wishlist
            // di un amico" e l'utente vedeva sempre una lista vuota della propria wishlist.
            //
            // La wishlist di un altro utente non e' piu' filtrata sui soli utenti seguiti. I
            // suggerimenti della Home mostrano di proposito persone NON ancora seguite ("10 giochi
            // in comune"), quindi il filtro rendeva sistematicamente vuota la wishlist di ogni
            // profilo raggiunto da li: la card prometteva giochi in comune e il profilo diceva
            // "nessun gioco". La sezione del profilo si chiama "Wishlist pubblica", quindi la
            // lettura pubblica e' il comportamento coerente.
            String target = (friendUsername == null || friendUsername.isBlank())
                    ? username
                    : friendUsername;
            return enrichFromMongo(userNeo4jRepository.findByUsername(target));
        }catch (Exception e){
            System.out.println(e.getMessage());
            return null;
        }
    }

    // Il grafo tiene solo id+nome: la wishlist e' quindi renderizzabile solo come lista di nomi.
    // Qui si recuperano i documenti Mongo (copertina, generi, voto, prezzo) con una sola query,
    // sfruttando il fatto che i due store condividono gli id.
    private List<Game> enrichFromMongo(List<GameNeo4j> graphGames) {
        if (graphGames == null || graphGames.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> ids = graphGames.stream()
                .map(GameNeo4j::getId)
                .filter(Objects::nonNull)
                .toList();

        Map<String, Game> byId = new HashMap<>();
        for (Game game : gameRepository.findAllById(ids)) {
            // le recensioni embedded non servono a una card di wishlist e pesano molto sul payload
            game.setReviews(null);
            byId.put(game.getId(), game);
        }

        List<Game> enriched = new ArrayList<>(graphGames.size());
        for (GameNeo4j graphGame : graphGames) {
            Game full = graphGame.getId() == null ? null : byId.get(graphGame.getId());
            if (full != null) {
                enriched.add(full);
            } else {
                // documento Mongo mancante: si mostra comunque id+nome, perche' un gioco aggiunto
                // dall'utente non deve sparire silenziosamente dalla sua wishlist
                Game placeholder = new Game();
                placeholder.setId(graphGame.getId());
                placeholder.setName(graphGame.getName());
                enriched.add(placeholder);
            }
        }
        return enriched;
    }


    @Override
    public Boolean addGameToWishlist(String username, String name) {
        try {
            //check both the game and the user exist in the graph before linking them
            GameNeo4j gameNeo4j=gameNeo4jRepository.findGameByName(name);
            UserNeo4j userNeo4j=userNeo4jRepository.getUser(username);
            if(gameNeo4j!=null && userNeo4j!=null){
                userNeo4jRepository.addGameToUser(username, name);
                return true;
            }

            return false;

        }catch (Exception e){
            System.out.println(e.getMessage());
            return null;
        }
    }

    @Override
    public Boolean deleteGameToWishlist(String username, String name) {
        try {
            //check if the game is present in the list of the added game
            List<GameNeo4j> gameNeo4jList= userNeo4jRepository.findByUsername(username);
            for (GameNeo4j game : gameNeo4jList) {
                if (game.getName().equals(name)) {
                    // The game with the provided name is present in the list
                    userNeo4jRepository.deleteGameFromUser(username, name);
                    return true;
                }
            }

            return false;
        }catch (Exception e){
            System.out.println(e.getMessage());
            return null;
        }
    }


    @Override
    public List<UserNeo4j> getFollowedUser(String username) {
        try {
            return userNeo4jRepository.findFollowedUsers(username);
        }catch (Exception e){
            System.out.println(e.getMessage());
            return null;
        }
    }

    @Override
    public Page<UserNeo4j> getFollowedUserPage(String username, Pageable pageable) {
        try {
            List<UserNeo4j> content = userNeo4jRepository.findFollowedUsersPage(
                    username, pageable.getOffset(), pageable.getPageSize());
            long total = userNeo4jRepository.countFollowedUsers(username);
            return new PageImpl<>(content, pageable, total);
        }catch (Exception e){
            System.out.println(e.getMessage());
            return Page.empty(pageable);
        }
    }

    @Override
    public List<UserNeo4j> getFriendsOfFriends(String username) {
        try {
            return userNeo4jRepository.findFriendsOfFriends(username);
        }catch (Exception e){
            System.out.println(e.getMessage());
            return null;
        }
    }

    private UserNeo4j convertUser(User user) {
        UserNeo4j userNeo4j = new UserNeo4j();
        userNeo4j.setId(user.getId());
        userNeo4j.setUsername(user.getUsername());
        return userNeo4j;
    }


    @Override
    public List<SuggestedUserDTO> getSuggestedFriends(String username) {
        try {
            // Cascata: si scende di livello solo se il precedente non produce risultati, cosi la
            // sezione "Persone da seguire" resta personalizzata quando possibile e non e mai vuota.
            List<SuggestedUserDTO> suggestions =
                    userNeo4jRepository.findSuggestedFriends(username, SUGGESTIONS_LIMIT);
            if (!suggestions.isEmpty()) {
                return suggestions;
            }

            suggestions = userNeo4jRepository.findUsersWithSimilarTastes(username, SUGGESTIONS_LIMIT);
            if (!suggestions.isEmpty()) {
                return suggestions;
            }

            return mostFollowedUsersFor(username);
        }catch (Exception e){
            System.out.println(e.getMessage());
            return null;
        }
    }

    // La classifica globale costa una scansione di tutte le relazioni FOLLOW (~1,5s sul dataset
    // completo) ed e' identica per tutti: la si calcola una volta ogni POPULAR_CACHE_TTL_MS e si
    // personalizza il risultato scartando l'utente stesso e chi gia' segue.
    private List<SuggestedUserDTO> mostFollowedUsersFor(String username) {
        Set<String> excluded = userNeo4jRepository.findFollowedUsers(username).stream()
                .map(UserNeo4j::getUsername)
                .collect(Collectors.toCollection(HashSet::new));
        excluded.add(username);

        return cachedMostFollowedUsers().stream()
                .filter(u -> !excluded.contains(u.getUsername()))
                .limit(SUGGESTIONS_LIMIT)
                .toList();
    }

    private List<SuggestedUserDTO> cachedMostFollowedUsers() {
        List<SuggestedUserDTO> cached = popularUsersCache;
        if (cached != null && System.currentTimeMillis() - popularUsersCachedAt < POPULAR_CACHE_TTL_MS) {
            return cached;
        }
        // il pool e' piu ampio del numero di suggerimenti mostrati, cosi resta abbastanza margine
        // dopo aver scartato gli utenti gia' seguiti
        List<SuggestedUserDTO> fresh = userNeo4jRepository.findMostFollowedUsers(POPULAR_POOL_SIZE);
        popularUsersCache = fresh;
        popularUsersCachedAt = System.currentTimeMillis();
        return fresh;
    }

    @Override
    public List<UserNeo4j> searchUsers(String query, String currentUsername) {
        try {
            return userNeo4jRepository.searchUsers(query, currentUsername);
        } catch (Exception e) {
            System.out.println(e.getMessage());
            return null;
        }
    }




    @Override
    public Boolean addLikeToReview(String username, String id) {
        try {
            Boolean likePresent=userNeo4jRepository.addLikeToReview(username,id);
            //System.out.println(likePresent);
            if(likePresent != null && !likePresent.booleanValue()){
                //se il like non è presente si aggiunge anche su mongoDB
                Optional<Review> optionalReview=reviewRepository.findById(id);
                if(optionalReview.isPresent()){
                    Review review = optionalReview.get();
                    int modifiedLikeCount=review.getLikeCount();
                    modifiedLikeCount+=1;
                    review.setLikeCount(modifiedLikeCount);
                    reviewRepository.save(review);

                    //check if in the embedded review list of the game the likeCount of this review
                    //is greater of the likeCount of the embedded review with minor likeCount, if yes
                    //we check if the review is not inside the embedded list,if yes we update the review from scratch
                    // otherwise if the review is inside the embedded review
                    //list we update the embedded review list with another function that operate only with the embedded
                    //reviews wich we have already retrieved in one read when we retrieve the game

                    //now do it step by step
                    //1)retrieve the game to retrieve the list of embedded review
                    List<Game> game=gameRepository.findByName(review.getTitle());
                    List<Review> embeddedReviews=game.get(0).getReviews();

                    //2)retrieve the embedded review with least likeCount between the embedded reviews
                    Review reviewWithLeastLikes = null;

                    // Iterate through the embedded reviews
                    for (Review compareReview : embeddedReviews) {
                        // Check if reviewWithLeastLikes is null or if the current review has fewer likes
                        if (reviewWithLeastLikes == null || compareReview.getLikeCount() < reviewWithLeastLikes.getLikeCount()) {
                            // Update reviewWithLeastLikes to the current review
                            reviewWithLeastLikes = compareReview;
                        }
                    }

                    //3)check if the review.likeCount i considered is > reviewWithLeastLikes.likecCount()
                    if(review.getLikeCount()>reviewWithLeastLikes.getLikeCount()){
                        boolean fuondEqualReview=false;
                        //4) now check if the review is already inside the embeddedReview
                        for(Review compareReview : embeddedReviews){
                            if(review.getId().equals(compareReview.getId())){
                                //if the review is already embedded we modify the also the likeCount in the embedded review
                                int embeddedLikeCount=compareReview.getLikeCount();
                                compareReview.setLikeCount(embeddedLikeCount+1);
                                gameRepository.save(game.get(0));
                                fuondEqualReview=true;
                            }
                        }

                        if(fuondEqualReview){
                            //5) if yes, run the function that act only inside the embedded review(DEFINE THIS FUNCTION)
                            gameService.updateGameEmbeddedReview(game.get(0));
                            System.out.println("update embedded reviews");

                        }else if(!fuondEqualReview){
                            //6) if not we update from scratch considering all the reviews of that game

                            gameService.updateGameReviewFromScratch(game.get(0),20);
                            System.out.println("update reviews from scratch");
                        }
                    }





                    //return true if the review it is created
                    return true;
                }else{
                    //if there are some problems we delete the link
                    userNeo4jRepository.deleteLikeFromReview(username,id);
                    return false;
                }


            }
            return false;
        }catch (Exception e){
            System.out.println(e.getMessage());
            return null;

        }


    }



    @Override
    public long countUserDocument(){
        try {
            return loginRepository.count();
        }catch (Exception e){
            System.out.println(e.getMessage());
            return -1;
        }
    }

    @Override
    public Boolean followUser(String followerUsername, String followedUsername) {

        try {

            //check both sides of the relationship exist in the graph before linking them
            if(userNeo4jRepository.getUser(followerUsername)!=null && userNeo4jRepository.getUser(followedUsername)!=null){
                userNeo4jRepository.followUser(followerUsername, followedUsername);
                return true;
            }
            return false;

        }catch (Exception e){
            System.out.println(e.getMessage());
            return null;
        }
    }
    @Override
    public Boolean unfollowUser(String followerUsername, String followedUsername) {
        try {
            List<UserNeo4j> userNeo4jList=userNeo4jRepository.findFollowedUsers(followerUsername);
            for(UserNeo4j userNeo4j:userNeo4jList){
                if(userNeo4j.getUsername().equals(followedUsername)){
                    userNeo4jRepository.unfollowUser(followerUsername, followedUsername);
                    return true;
                }
            }
            return false;

        }catch (Exception e){
            System.out.println(e.getMessage());
            return null;
        }
    }

    public void removeUser(String username) {
        userNeo4jRepository.removeUser(username);
    }
    public ResponseEntity<String> addUser(String id, String username){
        try {
            userNeo4jRepository.addUser(id, username);
            return new ResponseEntity<>("successfully registered user", HttpStatus.CREATED);
        }
        catch (Exception e) {
            return new ResponseEntity<>("Error in interaction with Neo4j" + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }

    }
    public UserNeo4j getUser(String username){

        try{
            UserNeo4j userNeo4j= userNeo4jRepository.getUser(username);
            if(userNeo4j!=null){
                return userNeo4j;
            }else {
                UserNeo4j userNeo4j1=new UserNeo4j();
                userNeo4j1.setId("null");
                return userNeo4j1;
            }
        }catch (Exception e){
            System.out.println(e.getMessage());
            return null;
        }
    }
    public ResponseEntity<String> updateUser(String username, String newUsername){
        try {
            userNeo4jRepository.updateUser(username, newUsername);
            return new ResponseEntity<>("username correctly updated", HttpStatus.OK);
        }
        catch (Exception e) {
            return new ResponseEntity<>("error in updating username in neo4j: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

}
