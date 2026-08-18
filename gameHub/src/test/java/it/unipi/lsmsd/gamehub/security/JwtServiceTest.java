package it.unipi.lsmsd.gamehub.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.security.SignatureException;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

    private static final String SECRET =
            "0f3b8c2d7a41e59f6b0d8e2c4a7f1b93d5e6c8a0f2b4d6e8a1c3f5b7d9e0a2c4";
    private static final long ONE_DAY_MS = 86_400_000L;

    private JwtService jwtService(long expirationMs) {
        return new JwtService(SECRET, expirationMs);
    }

    @Test
    void generateToken_thenParseToken_roundTripsUsernameAndRole() {
        JwtService service = jwtService(ONE_DAY_MS);

        String token = service.generateToken("Lunark", "ADMIN");
        Claims claims = service.parseToken(token);

        assertThat(claims.getSubject()).isEqualTo("Lunark");
        assertThat(claims.get("role", String.class)).isEqualTo("ADMIN");
    }

    @Test
    void generateToken_expirationIsIssuedAtPlusConfiguredDuration() {
        JwtService service = jwtService(ONE_DAY_MS);

        Claims claims = service.parseToken(service.generateToken("Lunark", "USER"));

        long actualDuration = claims.getExpiration().getTime() - claims.getIssuedAt().getTime();
        assertThat(actualDuration).isEqualTo(ONE_DAY_MS);
    }

    @Test
    void parseToken_expiredToken_throwsExpiredJwtException() {
        // negative expiration puts the "expires at" before "issued at", so the token is already
        // expired the instant it's generated -- no need for a real sleep in the test.
        JwtService service = jwtService(-1_000L);

        String expiredToken = service.generateToken("Lunark", "USER");

        assertThatThrownBy(() -> service.parseToken(expiredToken))
                .isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void parseToken_tamperedPayload_throwsJwtException() {
        JwtService service = jwtService(ONE_DAY_MS);
        String token = service.generateToken("Lunark", "USER");
        String[] parts = token.split("\\.");
        // flip the signature segment so the payload no longer matches it
        String tampered = parts[0] + "." + parts[1] + "." + new StringBuilder(parts[2]).reverse();

        assertThatThrownBy(() -> service.parseToken(tampered)).isInstanceOf(JwtException.class);
    }

    @Test
    void parseToken_signedWithDifferentSecret_throwsSignatureException() {
        JwtService issuer =
                new JwtService(
                        "a-completely-different-secret-key-thats-also-long-enough", ONE_DAY_MS);
        JwtService verifier = jwtService(ONE_DAY_MS);
        String token = issuer.generateToken("Lunark", "USER");

        assertThatThrownBy(() -> verifier.parseToken(token)).isInstanceOf(SignatureException.class);
    }

    @Test
    void parseToken_malformedToken_throwsJwtException() {
        JwtService service = jwtService(ONE_DAY_MS);

        assertThatThrownBy(() -> service.parseToken("not-a-jwt")).isInstanceOf(JwtException.class);
    }
}
