package it.unipi.lsmsd.gamehub.support;

import org.bson.Document;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.containers.Neo4jContainer;
import org.testcontainers.utility.DockerImageName;

// Base class for tests that need a real Spring context wired to real Mongo and Neo4j (not mocks
// or an embedded fake) - use for the dual-write rollback paths and aggregation queries that a
// mocked-repository unit test (see the backend-tests skill) can't meaningfully exercise.
//
// Containers are started once per JVM in a static initializer (the classic Testcontainers
// "singleton container" pattern) instead of via @Container/@Testcontainers, so every subclass in
// the same `mvn test` run shares the same Mongo/Neo4j instance instead of paying the ~10s Neo4j
// startup cost per test class. They are never stopped explicitly - Testcontainers' Ryuk reaper
// kills them when the JVM exits.
@SpringBootTest
public abstract class IntegrationTestSupport {

    protected static final MongoDBContainer MONGO_CONTAINER =
            new MongoDBContainer(DockerImageName.parse("mongo:7.0"));

    protected static final Neo4jContainer<?> NEO4J_CONTAINER =
            new Neo4jContainer<>(DockerImageName.parse("neo4j:5.15"));

    static {
        MONGO_CONTAINER.start();
        NEO4J_CONTAINER.start();
    }

    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", MONGO_CONTAINER::getReplicaSetUrl);
        registry.add("spring.neo4j.uri", NEO4J_CONTAINER::getBoltUrl);
        registry.add("spring.neo4j.authentication.username", () -> "neo4j");
        registry.add("spring.neo4j.authentication.password", NEO4J_CONTAINER::getAdminPassword);
    }

    @Autowired protected MongoTemplate mongoTemplate;
    @Autowired protected Neo4jClient neo4jClient;

    // Deletes documents/nodes but keeps the schema (Mongo unique indexes, Neo4j constraints) that
    // the application created once at context startup - dropping the whole database would wipe
    // those too, since Spring Data only (re)creates them on ApplicationContext startup, not on
    // every access. Duplicate-key/constraint-violation tests rely on that schema surviving.
    protected void wipeDatabases() {
        for (String collection : new String[] {"users", "games", "reviews"}) {
            mongoTemplate.getCollection(collection).deleteMany(new Document());
        }
        neo4jClient.query("MATCH (n) DETACH DELETE n").run();
    }

    @AfterEach
    void cleanUpAfterEachTest() {
        wipeDatabases();
    }
}
