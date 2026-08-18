package it.unipi.lsmsd.gamehub.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.unipi.lsmsd.gamehub.DTO.LoginDTO;
import it.unipi.lsmsd.gamehub.DTO.RegistrationDTO;
import it.unipi.lsmsd.gamehub.model.User;
import it.unipi.lsmsd.gamehub.support.IntegrationTestSupport;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

// Rollback for /signup is orchestrated in LoginController.registration() itself, not in
// LoginService - a mocked unit test of LoginController (see LoginControllerTest) can only assert
// that the compensating call was invoked, not that the rollback actually leaves Mongo consistent.
// This exercises the real Mongo + Neo4j write-then-rollback pair, see backend-integration-tests.
@AutoConfigureMockMvc
class LoginControllerIT extends IntegrationTestSupport {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private PasswordEncoder passwordEncoder;

    private RegistrationDTO registrationDTO() {
        return new RegistrationDTO("Mario", "Rossi", "mariorossi", "pwd123", "mario@test.it");
    }

    private Optional<Map<String, Object>> findNeo4jUserByUsername(String username) {
        return neo4jClient
                .query("MATCH (a:UserNeo4j {username: $username}) RETURN a.id AS id")
                .bindAll(Map.of("username", username))
                .fetch()
                .one();
    }

    @Test
    void registration_newUsername_createsUserInBothMongoAndNeo4j() throws Exception {
        mockMvc.perform(
                        post("/signup")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(registrationDTO())))
                .andExpect(status().isCreated());

        User savedUser =
                mongoTemplate.findOne(
                        Query.query(Criteria.where("username").is("mariorossi")), User.class);
        assertThat(savedUser).isNotNull();
        assertThat(passwordEncoder.matches("pwd123", savedUser.getPassword())).isTrue();

        Optional<Map<String, Object>> neo4jUser = findNeo4jUserByUsername("mariorossi");
        assertThat(neo4jUser).isPresent();
        assertThat(neo4jUser.get()).containsEntry("id", savedUser.getId());
    }

    @Test
    void registration_usernameAlreadyTakenInNeo4j_rollsBackMongoUserAndReturnsServerError()
            throws Exception {
        // Pre-seed a UserNeo4j node with the target username: Neo4jIndexInitializer creates a
        // uniqueness constraint on UserNeo4j.username at startup, so the CREATE inside
        // UserNeo4jService.addUser throws, forcing the controller's rollback branch.
        neo4jClient
                .query("CREATE (a:UserNeo4j {id: $id, username: $username})")
                .bindAll(Map.of("id", "some-other-id", "username", "mariorossi"))
                .run();

        mockMvc.perform(
                        post("/signup")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(registrationDTO())))
                .andExpect(status().isInternalServerError());

        User rolledBackUser =
                mongoTemplate.findOne(
                        Query.query(Criteria.where("username").is("mariorossi")), User.class);
        assertThat(rolledBackUser).isNull();
    }

    @Test
    void login_legacyPlaintextPasswordMatches_migratesToBcryptAndReturnsToken() throws Exception {
        User user =
                new User(null, "Lunark", "Name", "Surname", "plaintextpwd", "lunark@test.it", null);
        mongoTemplate.save(user);

        mockMvc.perform(
                        post("/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(
                                                new LoginDTO("Lunark", "plaintextpwd"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.token").isNotEmpty());

        User migrated = mongoTemplate.findById(user.getId(), User.class);
        assertThat(migrated).isNotNull();
        assertThat(migrated.getPassword()).startsWith("$2a$");
        assertThat(passwordEncoder.matches("plaintextpwd", migrated.getPassword())).isTrue();
    }

    @Test
    void login_wrongPassword_returnsUnauthorized() throws Exception {
        User user =
                new User(
                        null,
                        "Lunark",
                        "Name",
                        "Surname",
                        passwordEncoder.encode("correct"),
                        "lunark@test.it",
                        null);
        mongoTemplate.save(user);

        mockMvc.perform(
                        post("/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(
                                                new LoginDTO("Lunark", "wrong"))))
                .andExpect(status().isUnauthorized());
    }
}
