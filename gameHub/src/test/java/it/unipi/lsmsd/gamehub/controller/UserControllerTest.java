package it.unipi.lsmsd.gamehub.controller;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import it.unipi.lsmsd.gamehub.model.Game;
import it.unipi.lsmsd.gamehub.model.UserNeo4j;
import it.unipi.lsmsd.gamehub.security.JwtService;
import it.unipi.lsmsd.gamehub.service.ILoginService;
import it.unipi.lsmsd.gamehub.service.IUserNeo4jService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;

@ExtendWith(SpringExtension.class)
@WebMvcTest(UserController.class)
@AutoConfigureMockMvc(addFilters = false)
class UserControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockBean private IUserNeo4jService userNeo4jService;
    @MockBean private ILoginService iLoginService;

    // see LoginControllerTest for why this is required even with addFilters = false
    @MockBean private JwtService jwtService;

    @Test
    void getUserWishlist_serviceReturnsNull_returnsInternalServerError() throws Exception {
        when(userNeo4jService.getUserWishlist("Lunark", null)).thenReturn(null);

        mockMvc.perform(get("/user/userSelected/wishlist").param("username", "Lunark"))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void getUserWishlist_serviceSucceeds_returnsOkWithList() throws Exception {
        Game game = new Game();
        game.setId("g1");
        when(userNeo4jService.getUserWishlist("Lunark", null)).thenReturn(List.of(game));

        mockMvc.perform(get("/user/userSelected/wishlist").param("username", "Lunark"))
                .andExpect(status().isOk());
    }

    @Test
    void addGameToWishlist_serviceReturnsNull_returnsInternalServerError() throws Exception {
        when(userNeo4jService.addGameToWishlist("Lunark", "BARRIER X")).thenReturn(null);

        mockMvc.perform(
                        post("/user/wishlist/addWishlistGame")
                                .param("username", "Lunark")
                                .param("name", "BARRIER X"))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void addGameToWishlist_serviceReturnsTrue_returnsGameAdded() throws Exception {
        when(userNeo4jService.addGameToWishlist("Lunark", "BARRIER X")).thenReturn(true);

        mockMvc.perform(
                        post("/user/wishlist/addWishlistGame")
                                .param("username", "Lunark")
                                .param("name", "BARRIER X"))
                .andExpect(status().isOk())
                .andExpect(content().string("game added"));
    }

    @Test
    void addGameToWishlist_serviceReturnsFalse_returnsNoGameAdded() throws Exception {
        when(userNeo4jService.addGameToWishlist("Lunark", "BARRIER X")).thenReturn(false);

        mockMvc.perform(
                        post("/user/wishlist/addWishlistGame")
                                .param("username", "Lunark")
                                .param("name", "BARRIER X"))
                .andExpect(status().isOk())
                .andExpect(content().string("no game added"));
    }

    @Test
    void deleteGameToWishlist_serviceReturnsNull_returnsInternalServerError() throws Exception {
        when(userNeo4jService.deleteGameToWishlist("Lunark", "BARRIER X")).thenReturn(null);

        mockMvc.perform(
                        post("/user/wishlist/deleteWishlistGame")
                                .param("username", "Lunark")
                                .param("name", "BARRIER X"))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void addLikeToReview_serviceReturnsNull_returnsInternalServerError() throws Exception {
        when(userNeo4jService.addLikeToReview("Lunark", "r1")).thenReturn(null);

        mockMvc.perform(
                        post("/user/reviewSelected/addLikeReview")
                                .param("username", "Lunark")
                                .param("id", "r1"))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void addLikeToReview_serviceReturnsTrue_returnsAddedLike() throws Exception {
        when(userNeo4jService.addLikeToReview("Lunark", "r1")).thenReturn(true);

        mockMvc.perform(
                        post("/user/reviewSelected/addLikeReview")
                                .param("username", "Lunark")
                                .param("id", "r1"))
                .andExpect(status().isOk())
                .andExpect(content().string("added like"));
    }

    @Test
    void addLikeToReview_serviceReturnsFalse_returnsNoAddedLike() throws Exception {
        when(userNeo4jService.addLikeToReview("Lunark", "r1")).thenReturn(false);

        mockMvc.perform(
                        post("/user/reviewSelected/addLikeReview")
                                .param("username", "Lunark")
                                .param("id", "r1"))
                .andExpect(status().isOk())
                .andExpect(content().string("no added like"));
    }

    @Test
    void removeLikeFromReview_serviceReturnsNull_returnsInternalServerError() throws Exception {
        when(userNeo4jService.removeLikeFromReview("Lunark", "r1")).thenReturn(null);

        mockMvc.perform(
                        post("/user/reviewSelected/removeLikeReview")
                                .param("username", "Lunark")
                                .param("id", "r1"))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void removeLikeFromReview_serviceReturnsTrue_returnsRemovedLike() throws Exception {
        when(userNeo4jService.removeLikeFromReview("Lunark", "r1")).thenReturn(true);

        mockMvc.perform(
                        post("/user/reviewSelected/removeLikeReview")
                                .param("username", "Lunark")
                                .param("id", "r1"))
                .andExpect(status().isOk())
                .andExpect(content().string("removed like"));
    }

    @Test
    void removeLikeFromReview_serviceReturnsFalse_returnsNoRemovedLike() throws Exception {
        when(userNeo4jService.removeLikeFromReview("Lunark", "r1")).thenReturn(false);

        mockMvc.perform(
                        post("/user/reviewSelected/removeLikeReview")
                                .param("username", "Lunark")
                                .param("id", "r1"))
                .andExpect(status().isOk())
                .andExpect(content().string("no removed like"));
    }

    @Test
    void countGame_callerNotAdmin_forwardsRoleCheckStatus() throws Exception {
        when(iLoginService.roleUser("u1"))
                .thenReturn(new ResponseEntity<>("forbidden", HttpStatus.UNAUTHORIZED));

        mockMvc.perform(get("/user/countUser/u1")).andExpect(status().isUnauthorized());

        verify(userNeo4jService, never()).countUserDocument();
    }

    @Test
    void countGame_callerIsAdmin_returnsCount() throws Exception {
        when(iLoginService.roleUser("u1")).thenReturn(new ResponseEntity<>("ADMIN", HttpStatus.OK));
        when(userNeo4jService.countUserDocument()).thenReturn(7L);

        mockMvc.perform(get("/user/countUser/u1"))
                .andExpect(status().isOk())
                .andExpect(content().string("7"));
    }

    @Test
    void followUser_serviceReturnsNull_returnsInternalServerError() throws Exception {
        when(userNeo4jService.followUser("Lunark", "Kaistlin")).thenReturn(null);

        mockMvc.perform(
                        post("/user/userSelected/follow")
                                .param("followerUsername", "Lunark")
                                .param("followedUsername", "Kaistlin"))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void followUser_serviceReturnsTrue_returnsFollowedSuccessfully() throws Exception {
        when(userNeo4jService.followUser("Lunark", "Kaistlin")).thenReturn(true);

        mockMvc.perform(
                        post("/user/userSelected/follow")
                                .param("followerUsername", "Lunark")
                                .param("followedUsername", "Kaistlin"))
                .andExpect(status().isOk())
                .andExpect(content().string("Followed successfully"));
    }

    @Test
    void unfollowUser_serviceReturnsNull_returnsInternalServerError() throws Exception {
        when(userNeo4jService.unfollowUser("Lunark", "Kaistlin")).thenReturn(null);

        mockMvc.perform(
                        post("/user/userSelected/unfollow")
                                .param("followerUsername", "Lunark")
                                .param("followedUsername", "Kaistlin"))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void unfollowUser_serviceReturnsTrue_returnsUnfollowedSuccessfully() throws Exception {
        when(userNeo4jService.unfollowUser("Lunark", "Kaistlin")).thenReturn(true);

        mockMvc.perform(
                        post("/user/userSelected/unfollow")
                                .param("followerUsername", "Lunark")
                                .param("followedUsername", "Kaistlin"))
                .andExpect(status().isOk())
                .andExpect(content().string("Unfollowed successfully"));
    }

    @Test
    void updateUser_mongoUpdateFails_doesNotTouchNeo4j() throws Exception {
        when(iLoginService.updateUser("oldName", "newName"))
                .thenReturn(new ResponseEntity<>("username already used", HttpStatus.CONFLICT));

        mockMvc.perform(
                        patch("/user/updateUser")
                                .param("username", "oldName")
                                .param("newUsername", "newName"))
                .andExpect(status().isConflict());

        verify(userNeo4jService, never()).updateUser(anyString(), anyString());
    }

    @Test
    void updateUser_mongoAndNeo4jSucceed_returnsOk() throws Exception {
        when(iLoginService.updateUser("oldName", "newName"))
                .thenReturn(new ResponseEntity<>("username updated in mongo", HttpStatus.OK));
        when(userNeo4jService.updateUser("oldName", "newName"))
                .thenReturn(new ResponseEntity<>("username correctly updated", HttpStatus.OK));

        mockMvc.perform(
                        patch("/user/updateUser")
                                .param("username", "oldName")
                                .param("newUsername", "newName"))
                .andExpect(status().isOk())
                .andExpect(content().string("username correctly updated"));
    }

    @Test
    void updateUser_neo4jUpdateFails_rollsBackMongoUsernameAndReturnsFailureMessage()
            throws Exception {
        when(iLoginService.updateUser("oldName", "newName"))
                .thenReturn(new ResponseEntity<>("username updated in mongo", HttpStatus.OK));
        when(userNeo4jService.updateUser("oldName", "newName"))
                .thenReturn(new ResponseEntity<>("error", HttpStatus.INTERNAL_SERVER_ERROR));
        when(iLoginService.updateUser("newName", "oldName"))
                .thenReturn(new ResponseEntity<>("username updated in mongo", HttpStatus.OK));

        mockMvc.perform(
                        patch("/user/updateUser")
                                .param("username", "oldName")
                                .param("newUsername", "newName"))
                .andExpect(status().isOk())
                .andExpect(content().string("username update failed, please try again later"));

        // rollback swaps the arguments to restore the original username
        verify(iLoginService, times(1)).updateUser("newName", "oldName");
    }

    @Test
    void getUser_serviceReturnsNull_returnsInternalServerError() throws Exception {
        when(userNeo4jService.getUser("Lunark")).thenReturn(null);

        mockMvc.perform(get("/user/getUser").param("username", "Lunark"))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void getUser_userFound_returnsOkWithBody() throws Exception {
        when(userNeo4jService.getUser("Lunark")).thenReturn(new UserNeo4j("u1", "Lunark"));

        mockMvc.perform(get("/user/getUser").param("username", "Lunark"))
                .andExpect(status().isOk());
    }

    @Test
    void getUser_userNotFound_returnsEmptyOkBody() throws Exception {
        UserNeo4j sentinel = new UserNeo4j();
        sentinel.setId("null");
        when(userNeo4jService.getUser("Ghost")).thenReturn(sentinel);

        mockMvc.perform(get("/user/getUser").param("username", "Ghost"))
                .andExpect(status().isOk())
                .andExpect(content().string(""));
    }
}
