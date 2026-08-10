package it.unipi.lsmsd.gamehub.utils;

import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class Neo4jIndexInitializer implements ApplicationRunner {

    // Il vincolo di unicita' su username porta con se' il proprio indice, quindi copre anche le
    // lookup: Neo4j rifiuta un constraint se sulla stessa (label, property) esiste gia' un indice
    // semplice, percio' i due non vanno creati insieme.
    private static final String USERNAME_CONSTRAINT =
            "CREATE CONSTRAINT user_neo4j_username_unique IF NOT EXISTS "
                    + "FOR (u:UserNeo4j) REQUIRE u.username IS UNIQUE";
    // fallback se il constraint non e' creabile (dati ancora duplicati): senza indice le query
    // per username tornerebbero a scandire l'intera label
    private static final String USERNAME_INDEX =
            "CREATE INDEX user_neo4j_username IF NOT EXISTS FOR (u:UserNeo4j) ON (u.username)";

    private static final List<String> INDEXES =
            List.of(
                    "CREATE INDEX user_neo4j_id IF NOT EXISTS FOR (u:UserNeo4j) ON (u.id)",
                    "CREATE INDEX game_neo4j_name IF NOT EXISTS FOR (g:GameNeo4j) ON (g.name)",
                    "CREATE INDEX game_neo4j_id IF NOT EXISTS FOR (g:GameNeo4j) ON (g.id)",
                    "CREATE INDEX review_neo4j_id IF NOT EXISTS FOR (r:ReviewNeo4j) ON (r.id)");

    private final Neo4jClient neo4jClient;

    public Neo4jIndexInitializer(Neo4jClient neo4jClient) {
        this.neo4jClient = neo4jClient;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!tryRun(USERNAME_CONSTRAINT)) {
            tryRun(USERNAME_INDEX);
        }
        for (String statement : INDEXES) {
            tryRun(statement);
        }
    }

    private boolean tryRun(String statement) {
        try {
            neo4jClient.query(statement).run();
            return true;
        } catch (Exception e) {
            // uno schema incompleto degrada le prestazioni ma non deve impedire l'avvio
            log.error("Statement di schema Neo4j non riuscito (", e);
            return false;
        }
    }
}
