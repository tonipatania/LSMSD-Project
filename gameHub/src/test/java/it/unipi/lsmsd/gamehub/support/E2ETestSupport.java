package it.unipi.lsmsd.gamehub.support;

import static io.restassured.RestAssured.given;

import io.restassured.RestAssured;
import io.restassured.specification.RequestSpecification;
import it.unipi.lsmsd.gamehub.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

// Base class for end-to-end tests: a fully started app (random port, real HTTP, real
// SecurityConfig/JwtAuthenticationFilter chain) exercised through RestAssured instead of MockMvc.
// Reuses AbstractIntegrationTest's shared Testcontainers Mongo/Neo4j and per-test cleanup, so an
// E2E journey seeds its own fixtures (through the API or directly via mongoTemplate/neo4jClient)
// and can assume an empty database at the start of every test method.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class E2ETestSupport extends IntegrationTestSupport {

    @LocalServerPort protected int port;

    @Autowired protected JwtService jwtService;

    @BeforeEach
    void configureRestAssured() {
        RestAssured.baseURI = "http://localhost";
        RestAssured.port = port;
    }

    // Mints a JWT directly instead of going through /login, so a journey that isn't specifically
    // testing login itself doesn't have to seed a BCrypt password just to get authenticated.
    // `role` is the raw claim value ("USER"/"ADMIN", no "ROLE_" prefix - JwtAuthenticationFilter
    // adds that itself).
    protected RequestSpecification authenticatedAs(String username, String role) {
        return given().header(
                        "Authorization", "Bearer " + jwtService.generateToken(username, role));
    }

    protected RequestSpecification anonymous() {
        return given();
    }
}
