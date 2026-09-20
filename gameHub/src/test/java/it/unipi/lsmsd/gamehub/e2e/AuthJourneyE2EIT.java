package it.unipi.lsmsd.gamehub.e2e;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;

import io.restassured.http.ContentType;
import it.unipi.lsmsd.gamehub.DTO.ForgotPasswordDTO;
import it.unipi.lsmsd.gamehub.DTO.LoginDTO;
import it.unipi.lsmsd.gamehub.DTO.RegistrationDTO;
import it.unipi.lsmsd.gamehub.DTO.ResetPasswordDTO;
import it.unipi.lsmsd.gamehub.model.User;
import it.unipi.lsmsd.gamehub.support.E2ETestSupport;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

// Drives signup -> login -> an authenticated call, purely over real HTTP, the way an actual
// client would - see backend-e2e-tests for how this differs from the controller-level
// LoginControllerIT (which inspects Mongo/Neo4j state directly after a single call).
class AuthJourneyE2EIT extends E2ETestSupport {

    @Test
    void signupThenLogin_returnsWorkingTokenUsableOnAProtectedEndpoint() {
        RegistrationDTO registration =
                new RegistrationDTO("Mario", "Rossi", "mariorossi", "Passw0rd!", "mario@test.it");

        anonymous()
                .contentType(ContentType.JSON)
                .body(registration)
                .post("/signup")
                .then()
                .statusCode(201);

        // La registrazione lascia l'account non confermato: qui si simula il click sul link
        // ricevuto via email leggendo il token direttamente da Mongo, dato che nei test non
        // c'e' un vero server SMTP a cui accedere.
        User registeredUser =
                mongoTemplate.findOne(
                        Query.query(Criteria.where("username").is("mariorossi")), User.class);
        anonymous()
                .queryParam("token", registeredUser.getVerificationToken())
                .get("/confirm-email")
                .then()
                .statusCode(200);

        String token =
                anonymous()
                        .contentType(ContentType.JSON)
                        .body(new LoginDTO("mariorossi", "Passw0rd!"))
                        .post("/login")
                        .then()
                        .statusCode(200)
                        .body("success", equalTo(true))
                        .body("token", not(blankOrNullString()))
                        .extract()
                        .path("token");

        given().header("Authorization", "Bearer " + token)
                .queryParam("username", "mariorossi")
                .get("/user/getUser")
                .then()
                .statusCode(200)
                .body("username", equalTo("mariorossi"));
    }

    @Test
    void protectedEndpoint_withoutToken_returnsUnauthorized() {
        anonymous().queryParam("username", "anyone").get("/user/getUser").then().statusCode(401);
    }

    @Test
    void login_wrongPassword_returnsUnauthorizedWithoutToken() {
        RegistrationDTO registration =
                new RegistrationDTO("Mario", "Rossi", "mariorossi", "Passw0rd!", "mario@test.it");
        anonymous().contentType(ContentType.JSON).body(registration).post("/signup");

        anonymous()
                .contentType(ContentType.JSON)
                .body(new LoginDTO("mariorossi", "wrongpassword"))
                .post("/login")
                .then()
                .statusCode(401)
                .body("success", equalTo(false));
    }

    @Test
    void forgotPassword_unknownEmail_returnsOkWithoutRevealingThatNoAccountExists() {
        anonymous()
                .contentType(ContentType.JSON)
                .body(new ForgotPasswordDTO("ghost@test.it"))
                .post("/forgot-password")
                .then()
                .statusCode(200);
    }

    @Test
    void resetPassword_withValidToken_changesThePasswordAndTheTokenWorksOnlyOnce()
            throws Exception {
        RegistrationDTO registration =
                new RegistrationDTO("Mario", "Rossi", "mariorossi", "Passw0rd!", "mario@test.it");
        anonymous().contentType(ContentType.JSON).body(registration).post("/signup");
        User user =
                mongoTemplate.findOne(
                        Query.query(Criteria.where("username").is("mariorossi")), User.class);
        anonymous().queryParam("token", user.getVerificationToken()).get("/confirm-email");

        // Il token in chiaro esiste solo nell'email (su Mongo c'e' solo il suo hash), che nei test
        // non si puo' leggere: si salva l'hash di un token noto, come farebbe /forgot-password.
        user =
                mongoTemplate.findOne(
                        Query.query(Criteria.where("username").is("mariorossi")), User.class);
        user.setPasswordResetTokenHash(
                HexFormat.of()
                        .formatHex(
                                MessageDigest.getInstance("SHA-256")
                                        .digest("known-token".getBytes(StandardCharsets.UTF_8))));
        user.setPasswordResetTokenExpiry(System.currentTimeMillis() + 60_000);
        mongoTemplate.save(user);

        ResetPasswordDTO reset = new ResetPasswordDTO("known-token", "NewPassw0rd!");
        anonymous()
                .contentType(ContentType.JSON)
                .body(reset)
                .post("/reset-password")
                .then()
                .statusCode(200);

        anonymous()
                .contentType(ContentType.JSON)
                .body(new LoginDTO("mariorossi", "Passw0rd!"))
                .post("/login")
                .then()
                .statusCode(401);
        anonymous()
                .contentType(ContentType.JSON)
                .body(new LoginDTO("mariorossi", "NewPassw0rd!"))
                .post("/login")
                .then()
                .statusCode(200)
                .body("success", equalTo(true));

        anonymous()
                .contentType(ContentType.JSON)
                .body(reset)
                .post("/reset-password")
                .then()
                .statusCode(400);
    }
}
