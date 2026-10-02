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
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class JwtService {

    private final SecretKey key;
    private final long expirationMs;

    // gamehub.jwt.secret non ha piu' un default in application.properties (solo in
    // application-dev.properties, mai attivo in produzione): se GAMEHUB_JWT_SECRET non e'
    // impostata, Spring fallisce a questo punto con un placeholder non risolto invece di avviarsi
    // con una chiave pubblicamente nota in questo repo.
    public JwtService(
            @Value("${gamehub.jwt.secret}") String secret,
            @Value("${gamehub.jwt.expiration-ms}") long expirationMs) {
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
