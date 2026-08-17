package it.unipi.lsmsd.gamehub.config;

import java.util.concurrent.Executor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class AsyncConfig {

    // Pool dedicato alle query Neo4j lanciate in parallelo per i suggerimenti della Home (vedi
    // UserNeo4jService.getSuggestedFriends): separato dal ForkJoinPool comune della JVM perche'
    // qui i task sono I/O bloccante verso il DB, non calcolo CPU-bound.
    @Bean(name = "suggestionsExecutor")
    public Executor suggestionsExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("suggestions-");
        executor.initialize();
        return executor;
    }
}
