package it.unipi.lsmsd.gamehub.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.unipi.lsmsd.gamehub.DTO.LoginDTO;
import it.unipi.lsmsd.gamehub.DTO.RegistrationDTO;
import it.unipi.lsmsd.gamehub.security.JwtService;
import it.unipi.lsmsd.gamehub.service.ILoginService;
import it.unipi.lsmsd.gamehub.service.IUserNeo4jService;
import it.unipi.lsmsd.gamehub.utils.AuthResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;

@ExtendWith(SpringExtension.class)
@WebMvcTest(LoginController.class)
@AutoConfigureMockMvc(addFilters = false)
class LoginControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private ILoginService loginService;
    @MockBean private IUserNeo4jService userNeo4jService;

    // @WebMvcTest still wires SecurityConfig -> JwtAuthenticationFilter, whose constructor needs a
    // JwtService bean, even though @AutoConfigureMockMvc(addFilters = false) means it never runs:
    // without this @MockBean, context startup fails with a NoSuchBeanDefinitionException.
    @MockBean private JwtService jwtService;

    @Test
    void login_validCredentials_returnsOkWithAuthResponse() throws Exception {
        AuthResponse success =
                new AuthResponse(true, null, null, "Lunark", "jwt", "USER");
        when(loginService.authenticate(any(LoginDTO.class))).thenReturn(success);

        mockMvc.perform(
                        post("/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(
                                                new LoginDTO("Lunark", "pwd"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.token").value("jwt"));
    }

    @Test
    void login_invalidCredentials_returnsUnauthorized() throws Exception {
        AuthResponse failure =
                new AuthResponse(false, "Credenziali non valide", "INVALID_CREDENTIALS", null);
        when(loginService.authenticate(any(LoginDTO.class))).thenReturn(failure);

        mockMvc.perform(
                        post("/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(
                                                new LoginDTO("Lunark", "wrong"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void login_passwordOver32Characters_returnsBadRequestWithoutCallingService() throws Exception {
        mockMvc.perform(
                        post("/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(
                                                new LoginDTO("Lunark", "x".repeat(33)))))
                .andExpect(status().isBadRequest());

        verify(loginService, never()).authenticate(any(LoginDTO.class));
    }

    @Test
    void login_blankUsername_returnsBadRequest() throws Exception {
        mockMvc.perform(
                        post("/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(new LoginDTO("", "pwd"))))
                .andExpect(status().isBadRequest());

        verify(loginService, never()).authenticate(any(LoginDTO.class));
    }

    private RegistrationDTO registrationDTO() {
        return new RegistrationDTO("Mario", "Rossi", "mariorossi", "Passw0rd!", "mario@test.it");
    }

    @Test
    void registration_mongoAndNeo4jSucceed_returnsCreated() throws Exception {
        when(loginService.registrate(any(RegistrationDTO.class)))
                .thenReturn(new ResponseEntity<>("id1", HttpStatus.CREATED));
        when(userNeo4jService.addUser(eq("id1"), eq("mariorossi")))
                .thenReturn(new ResponseEntity<>("registered", HttpStatus.CREATED));

        mockMvc.perform(
                        post("/signup")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(registrationDTO())))
                .andExpect(status().isCreated());

        verify(loginService).sendVerificationEmail("id1");
    }

    @Test
    void registration_passwordMissingUppercaseAndSpecialChar_returnsBadRequest()
            throws Exception {
        RegistrationDTO weakPassword =
                new RegistrationDTO(
                        "Mario", "Rossi", "mariorossi", "weakpassword", "mario@test.it");

        mockMvc.perform(
                        post("/signup")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(weakPassword)))
                .andExpect(status().isBadRequest());

        verify(loginService, never()).registrate(any(RegistrationDTO.class));
    }

    @Test
    void registration_passwordOver32Characters_returnsBadRequest() throws Exception {
        RegistrationDTO tooLong =
                new RegistrationDTO(
                        "Mario", "Rossi", "mariorossi", "Aa1!" + "x".repeat(30), "mario@test.it");

        mockMvc.perform(
                        post("/signup")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(tooLong)))
                .andExpect(status().isBadRequest());

        verify(loginService, never()).registrate(any(RegistrationDTO.class));
    }

    @Test
    void registration_mongoConflict_returnsConflictWithoutTouchingNeo4j() throws Exception {
        when(loginService.registrate(any(RegistrationDTO.class)))
                .thenReturn(
                        new ResponseEntity<>(
                                "Username gia' in uso, scegline un altro", HttpStatus.CONFLICT));

        mockMvc.perform(
                        post("/signup")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(registrationDTO())))
                .andExpect(status().isConflict());

        verify(userNeo4jService, never()).addUser(any(), any());
    }

    @Test
    void registration_neo4jCreationFails_rollsBackMongoUserAndReturnsServerError()
            throws Exception {
        when(loginService.registrate(any(RegistrationDTO.class)))
                .thenReturn(new ResponseEntity<>("id1", HttpStatus.CREATED));
        when(userNeo4jService.addUser(eq("id1"), eq("mariorossi")))
                .thenReturn(new ResponseEntity<>("error", HttpStatus.INTERNAL_SERVER_ERROR));

        mockMvc.perform(
                        post("/signup")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(registrationDTO())))
                .andExpect(status().isInternalServerError());

        verify(loginService).removeUser("id1");
    }
}
