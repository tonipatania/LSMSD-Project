package it.unipi.lsmsd.gamehub.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class JwtService {

    // Stesso valore del fallback in application.properties (gamehub.jwt.secret): serve solo a
    // riconoscere quando GAMEHUB_JWT_SECRET non e' stato impostato, per rifiutare l'avvio in
    // produzione invece di firmare i token con una chiave pubblicamente nota in questo repo.
    private static final String DEFAULT_DEV_SECRET =
            "0f3b8c2d7a41e59f6b0d8e2c4a7f1b93d5e6c8a0f2b4d6e8a1c3f5b7d9e0a2c4";

    private final SecretKey key;
    private final long expirationMs;

    public JwtService(
            @Value("${gamehub.jwt.secret}") String secret,
            @Value("${gamehub.jwt.expiration-ms}") long expirationMs,
            Environment environment) {
        if (DEFAULT_DEV_SECRET.equals(secret) && environment.matchesProfiles("prod")) {
            throw new IllegalStateException(
                    "GAMEHUB_JWT_SECRET non impostato: obbligatorio con il profilo 'prod' attivo"
                            + " (SPRING_PROFILES_ACTIVE=prod). Genera un secret con: openssl rand"
                            + " -hex 32 (vedi .env.example).");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
    }

    public String generateToken(String username, String role) {
        Date now = new Date();
        log.debug("Generazione token JWT per l'utente {}", username);
        return Jwts.builder()
                .subject(username)
                // jti univoco per token: e' la chiave con cui TokenBlacklistService lo marca
                // come revocato al logout, prima della scadenza naturale.
                .id(UUID.randomUUID().toString())
                .claim("role", role)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expirationMs))
                .signWith(key)
                .compact();
    }

    public Claims parseToken(String token) throws JwtException {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }
}
