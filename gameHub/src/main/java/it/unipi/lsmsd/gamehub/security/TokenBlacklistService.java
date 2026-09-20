package it.unipi.lsmsd.gamehub.security;

import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

// Revoca dei JWT (logout): un token firmato resta valido finche' non scade, il filtro non ha
// modo di "dimenticarlo" da solo - l'unico modo per invalidarlo prima e' tenere un elenco dei
// jti (identificativo univoco, vedi JwtService.generateToken) marcati come revocati.
// Redis e' la scelta naturale: gia' usato nel progetto (vedi RedisConfig) e la scadenza
// automatica della entry (= validita' residua del token) evita di dover ripulire la lista a mano.
@Component
@Slf4j
public class TokenBlacklistService {

    private static final String KEY_PREFIX = "jwt:blacklist:";

    private final RedisTemplate<String, Object> redisTemplate;

    public TokenBlacklistService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void revoke(String jti, long remainingValidityMs) {
        if (jti == null || remainingValidityMs <= 0) {
            // il token e' gia' scaduto o mai scaduto: non c'e' nulla da revocare
            return;
        }
        redisTemplate
                .opsForValue()
                .set(KEY_PREFIX + jti, Boolean.TRUE, remainingValidityMs, TimeUnit.MILLISECONDS);
    }

    public boolean isRevoked(String jti) {
        if (jti == null) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(KEY_PREFIX + jti));
        } catch (Exception e) {
            // Redis irraggiungibile: si sceglie di non bloccare l'intera autenticazione per un
            // problema di infrastruttura separato (fail-open) - stesso comportamento che si
            // aveva prima di introdurre la revoca, la firma/scadenza del token restano comunque
            // verificate a monte in JwtAuthenticationFilter.
            log.error("Impossibile verificare la blacklist dei token JWT su Redis", e);
            return false;
        }
    }
}
