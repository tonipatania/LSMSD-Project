package it.unipi.lsmsd.gamehub.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.unipi.lsmsd.gamehub.DTO.GameDTO;
import it.unipi.lsmsd.gamehub.model.Game;
import it.unipi.lsmsd.gamehub.security.JwtService;
import it.unipi.lsmsd.gamehub.service.IGameService;
import it.unipi.lsmsd.gamehub.service.ILoginService;
import it.unipi.lsmsd.gamehub.service.impl.GameNeo4jService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;

@ExtendWith(SpringExtension.class)
@WebMvcTest(GameController.class)
@AutoConfigureMockMvc(addFilters = false)
class GameControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private IGameService gameService;
    @MockBean private ILoginService iLoginService;
    @MockBean private GameNeo4jService gameNeo4jService;

    // @WebMvcTest still wires SecurityConfig -> JwtAuthenticationFilter, whose constructor needs a
    // JwtService bean, even though @AutoConfigureMockMvc(addFilters = false) means it never runs:
    // without this @MockBean, context startup fails with a NoSuchBeanDefinitionException.
    @MockBean private JwtService jwtService;

    private Game game(String id, String name) {
        Game game = new Game();
        game.setId(id);
        game.setName(name);
        return game;
    }

    @Test
    void getAllGenres_serviceSucceeds_returnsOkWithList() throws Exception {
        when(gameService.findDistinctGenres()).thenReturn(List.of("RPG", "Strategy"));

        mockMvc.perform(get("/game/genres"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value("RPG"));
    }

    @Test
    void getAllGenres_serviceReturnsNull_returnsInternalServerError() throws Exception {
        when(gameService.findDistinctGenres()).thenReturn(null);

        mockMvc.perform(get("/game/genres")).andExpect(status().isInternalServerError());
    }

    @Test
    void showGames_pageBeyondAvailable_returnsNotFound() throws Exception {
        // PageImpl recomputes total as offset+content.size() whenever offset+pageSize > total for
        // non-empty content, so total must be >= pageSize here or the "1 total page" this test
        // relies on would silently become something else.
        Page<Game> onePageTotal =
                new PageImpl<>(List.of(game("g1", "A")), PageRequest.of(0, 50), 50);
        when(gameService.getAll(any())).thenReturn(onePageTotal);

        mockMvc.perform(get("/game/getAll").param("page", "5").param("size", "50"))
                .andExpect(status().isNotFound());
    }

    @Test
    void showGames_emptyPageWithinRange_returnsNoContent() throws Exception {
        // A genuinely empty result set (total=0) always trips the "page beyond available" 404
        // check first, since totalPages is then 0 and any page number is >= 0: to reach the
        // isEmpty()->204 branch the mock has to describe a result set with more pages (total=100)
        // whose *current* page happens to come back empty.
        Page<Game> emptyPageWithMorePagesAvailable =
                new PageImpl<>(List.of(), PageRequest.of(0, 50), 100);
        when(gameService.getAll(any())).thenReturn(emptyPageWithMorePagesAvailable);

        mockMvc.perform(get("/game/getAll")).andExpect(status().isNoContent());
    }

    @Test
    void showGames_nonEmptyPageWithinRange_returnsOkWithContent() throws Exception {
        Page<Game> page =
                new PageImpl<>(List.of(game("g1", "BARRIER X")), PageRequest.of(0, 50), 1);
        when(gameService.getAll(any())).thenReturn(page);

        mockMvc.perform(get("/game/getAll"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value("g1"));
    }

    private GameDTO gameDto(String name) {
        return new GameDTO(
                null,
                name,
                "Oct 21, 2008",
                10.0,
                "about",
                "English",
                "dev",
                "pub",
                "Action",
                "Strategy",
                0);
    }

    @Test
    void createGame_callerNotAdmin_returnsRoleCheckResponseWithoutCreating() throws Exception {
        when(iLoginService.roleUser("u1"))
                .thenReturn(
                        new ResponseEntity<>(
                                "you do not have permissions for this operation",
                                HttpStatus.UNAUTHORIZED));

        mockMvc.perform(
                        post("/game/create/u1")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(gameDto("BARRIER X"))))
                .andExpect(status().isUnauthorized());

        verify(gameService, never()).createGame(any());
    }

    @Test
    void createGame_mongoAndNeo4jSucceed_returnsCreated() throws Exception {
        when(iLoginService.roleUser("u1")).thenReturn(new ResponseEntity<>("ADMIN", HttpStatus.OK));
        when(gameService.createGame(any(GameDTO.class)))
                .thenReturn(new ResponseEntity<>("g1", HttpStatus.CREATED));
        when(gameNeo4jService.addGame(eq("g1"), eq("BARRIER X")))
                .thenReturn(new ResponseEntity<>("game inserted correctly", HttpStatus.CREATED));

        mockMvc.perform(
                        post("/game/create/u1")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(gameDto("BARRIER X"))))
                .andExpect(status().isCreated());
    }

    @Test
    void createGame_neo4jFails_rollsBackByDeletingMongoGame() throws Exception {
        when(iLoginService.roleUser("u1")).thenReturn(new ResponseEntity<>("ADMIN", HttpStatus.OK));
        when(gameService.createGame(any(GameDTO.class)))
                .thenReturn(new ResponseEntity<>("g1", HttpStatus.CREATED));
        when(gameNeo4jService.addGame(eq("g1"), eq("BARRIER X")))
                .thenReturn(new ResponseEntity<>("error", HttpStatus.INTERNAL_SERVER_ERROR));
        when(gameService.deleteGame("g1"))
                .thenReturn(new ResponseEntity<>("game deleted", HttpStatus.OK));

        mockMvc.perform(
                        post("/game/create/u1")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(gameDto("BARRIER X"))))
                .andExpect(status().isOk());

        verify(gameService).deleteGame("g1");
    }

    @Test
    void deleteGame_callerNotAdmin_doesNotTouchGame() throws Exception {
        when(iLoginService.roleUser("u1"))
                .thenReturn(new ResponseEntity<>("forbidden", HttpStatus.UNAUTHORIZED));

        mockMvc.perform(delete("/game/gameSelected/delete/u1").param("gameId", "g1"))
                .andExpect(status().isUnauthorized());

        verify(gameService, never()).deleteGame(anyString());
    }

    @Test
    void deleteGame_adminAndMongoDeleteSucceeds_alsoDeletesFromNeo4j() throws Exception {
        when(iLoginService.roleUser("u1")).thenReturn(new ResponseEntity<>("ADMIN", HttpStatus.OK));
        when(gameService.deleteGame("g1"))
                .thenReturn(new ResponseEntity<>("game deleted", HttpStatus.OK));
        when(gameNeo4jService.removeGame("g1"))
                .thenReturn(new ResponseEntity<>("game deleted", HttpStatus.OK));

        mockMvc.perform(delete("/game/gameSelected/delete/u1").param("gameId", "g1"))
                .andExpect(status().isOk());

        verify(gameNeo4jService).removeGame("g1");
    }

    @Test
    void deleteGame_mongoDeleteFails_doesNotTouchNeo4j() throws Exception {
        when(iLoginService.roleUser("u1")).thenReturn(new ResponseEntity<>("ADMIN", HttpStatus.OK));
        when(gameService.deleteGame("g1"))
                .thenReturn(new ResponseEntity<>("game not deleted", HttpStatus.NOT_FOUND));

        mockMvc.perform(delete("/game/gameSelected/delete/u1").param("gameId", "g1"))
                .andExpect(status().isNotFound());

        verify(gameNeo4jService, never()).removeGame(anyString());
    }

    @Test
    void countGame_callerNotAdmin_forwardsRoleCheckStatus() throws Exception {
        when(iLoginService.roleUser("u1"))
                .thenReturn(new ResponseEntity<>("forbidden", HttpStatus.UNAUTHORIZED));

        mockMvc.perform(get("/game/countGame/u1")).andExpect(status().isUnauthorized());
    }

    @Test
    void countGame_callerIsAdmin_returnsCount() throws Exception {
        when(iLoginService.roleUser("u1")).thenReturn(new ResponseEntity<>("ADMIN", HttpStatus.OK));
        when(gameService.countGameDocument()).thenReturn(42L);

        mockMvc.perform(get("/game/countGame/u1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value(42));
    }

    @Test
    void getGamesWithReviews_returnsOkWithGames() throws Exception {
        when(gameService.getGamesWithReviews(20)).thenReturn(List.of(game("g1", "BARRIER X")));

        mockMvc.perform(get("/game/withReviews"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("g1"));
    }
}
