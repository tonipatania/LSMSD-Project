package it.unipi.lsmsd.gamehub.security;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

// Rate limiting in-memory su /login, per chiave (IP, username): protegge un singolo account dal
// brute-force senza rischiare di bloccare l'utente legittimo se l'attacco arriva da un'altra rete
// (la chiave include l'IP, non solo lo username - altrimenti un attaccante potrebbe bloccare
// account altrui semplicemente sbagliando la password ripetutamente).
// Stato in-memory: sufficiente per la singola istanza del deploy attuale; con piu' istanze
// andrebbe spostato su Redis (gia' usato altrove nel progetto, vedi RedisConfig).
@Component
@Slf4j
public class LoginRateLimiter {

    private static final class Attempt {
        final AtomicInteger failures = new AtomicInteger(0);
        volatile long windowStart = System.currentTimeMillis();
        volatile long blockedUntil = 0L;
    }

    private final ConcurrentHashMap<String, Attempt> attempts = new ConcurrentHashMap<>();

    private final int maxAttempts;
    private final long windowMs;
    private final long blockMs;

    public LoginRateLimiter(
            @Value("${gamehub.login.rate-limit.max-attempts}") int maxAttempts,
            @Value("${gamehub.login.rate-limit.window-ms}") long windowMs,
            @Value("${gamehub.login.rate-limit.block-ms}") long blockMs) {
        this.maxAttempts = maxAttempts;
        this.windowMs = windowMs;
        this.blockMs = blockMs;
    }

    public boolean isBlocked(String key) {
        Attempt attempt = attempts.get(key);
        return attempt != null && attempt.blockedUntil > System.currentTimeMillis();
    }

    public long remainingBlockSeconds(String key) {
        Attempt attempt = attempts.get(key);
        if (attempt == null) {
            return 0;
        }
        return Math.max(0, (attempt.blockedUntil - System.currentTimeMillis()) / 1000);
    }

    public void recordFailure(String key) {
        long now = System.currentTimeMillis();
        Attempt attempt = attempts.computeIfAbsent(key, k -> new Attempt());
        synchronized (attempt) {
            if (now - attempt.windowStart > windowMs) {
                attempt.windowStart = now;
                attempt.failures.set(0);
            }
            int failures = attempt.failures.incrementAndGet();
            if (failures >= maxAttempts) {
                attempt.blockedUntil = now + blockMs;
                log.warn("Login bloccato per {} dopo {} tentativi falliti", key, failures);
            }
        }
        // pulizia occasionale delle voci scadute, per non far crescere la mappa all'infinito
        if (ThreadLocalRandom.current().nextInt(100) == 0) {
            cleanupStaleEntries(now);
        }
    }

    public void recordSuccess(String key) {
        attempts.remove(key);
    }

    private void cleanupStaleEntries(long now) {
        attempts.entrySet()
                .removeIf(
                        entry -> {
                            Attempt a = entry.getValue();
                            return a.blockedUntil < now && (now - a.windowStart) > windowMs;
                        });
    }
}
