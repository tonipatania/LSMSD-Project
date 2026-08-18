package it.unipi.lsmsd.gamehub.e2e;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;

import io.restassured.http.ContentType;
import it.unipi.lsmsd.gamehub.DTO.LoginDTO;
import it.unipi.lsmsd.gamehub.DTO.RegistrationDTO;
import it.unipi.lsmsd.gamehub.support.E2ETestSupport;
import org.junit.jupiter.api.Test;

// Drives signup -> login -> an authenticated call, purely over real HTTP, the way an actual
// client would - see backend-e2e-tests for how this differs from the controller-level
// LoginControllerIT (which inspects Mongo/Neo4j state directly after a single call).
class AuthJourneyE2EIT extends E2ETestSupport {

    @Test
    void signupThenLogin_returnsWorkingTokenUsableOnAProtectedEndpoint() {
        RegistrationDTO registration =
                new RegistrationDTO("Mario", "Rossi", "mariorossi", "pwd123", "mario@test.it");

        anonymous()
                .contentType(ContentType.JSON)
                .body(registration)
                .post("/signup")
                .then()
                .statusCode(201);

        String token =
                anonymous()
                        .contentType(ContentType.JSON)
                        .body(new LoginDTO("mariorossi", "pwd123"))
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
                new RegistrationDTO("Mario", "Rossi", "mariorossi", "pwd123", "mario@test.it");
        anonymous().contentType(ContentType.JSON).body(registration).post("/signup");

        anonymous()
                .contentType(ContentType.JSON)
                .body(new LoginDTO("mariorossi", "wrongpassword"))
                .post("/login")
                .then()
                .statusCode(401)
                .body("success", equalTo(false));
    }
}
