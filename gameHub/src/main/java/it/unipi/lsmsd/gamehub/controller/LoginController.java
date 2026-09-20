package it.unipi.lsmsd.gamehub.controller;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import it.unipi.lsmsd.gamehub.DTO.ForgotPasswordDTO;
import it.unipi.lsmsd.gamehub.DTO.LoginDTO;
import it.unipi.lsmsd.gamehub.DTO.RegistrationDTO;
import it.unipi.lsmsd.gamehub.DTO.ResetPasswordDTO;
import it.unipi.lsmsd.gamehub.security.JwtService;
import it.unipi.lsmsd.gamehub.security.LoginRateLimiter;
import it.unipi.lsmsd.gamehub.security.TokenBlacklistService;
import it.unipi.lsmsd.gamehub.service.ILoginService;
import it.unipi.lsmsd.gamehub.service.IUserNeo4jService;
import it.unipi.lsmsd.gamehub.utils.AuthResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

@RestController
@Slf4j
public class LoginController {
    @Autowired private ILoginService loginService;

    @Autowired private IUserNeo4jService userNeo4jService;

    @Autowired private LoginRateLimiter loginRateLimiter;

    @Autowired private JwtService jwtService;

    @Autowired private TokenBlacklistService tokenBlacklistService;

    /*Postman parameters
    {
        "username": "Lunark",
        "password": "jrmag6azycv"
    }*/
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginDTO loginDTO, HttpServletRequest request) {
        String rateLimitKey = clientIp(request) + ":" + loginDTO.getUsername().toLowerCase();
        if (loginRateLimiter.isBlocked(rateLimitKey)) {
            long retryAfterSeconds = loginRateLimiter.remainingBlockSeconds(rateLimitKey);
            log.warn(
                    "Login rifiutato per {} (rate limit attivo, riprova tra {}s)",
                    loginDTO.getUsername(),
                    retryAfterSeconds);
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .header("Retry-After", String.valueOf(retryAfterSeconds))
                    .body(
                            new AuthResponse(
                                    false,
                                    "Troppi tentativi falliti, riprova tra qualche minuto",
                                    "TOO_MANY_ATTEMPTS",
                                    null));
        }

        AuthResponse authResponse = loginService.authenticate(loginDTO);
        if (authResponse.isSuccess()) {
            loginRateLimiter.recordSuccess(rateLimitKey);
            log.info("Login riuscito per l'utente {}", loginDTO.getUsername());
            return ResponseEntity.ok(authResponse);
        } else {
            loginRateLimiter.recordFailure(rateLimitKey);
            log.warn("Tentativo di login fallito per l'utente {}", loginDTO.getUsername());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(authResponse);
        }
    }

    // Render (come la maggior parte degli hosting) instrada le richieste attraverso un reverse
    // proxy: getRemoteAddr() restituirebbe sempre l'IP del proxy, non quello del client. Il proxy
    // AGGIUNGE in coda a X-Forwarded-For, non lo sovrascrive: un client puo' mandare un header
    // X-Forwarded-For falso di suo pugno, che finisce come primo valore della lista. L'unico
    // valore fidato e' l'ULTIMO, perche' e' quello scritto dal proxy di Render stesso (il solo hop
    // fidato davanti a questa app) in base alla connessione TCP reale, non manipolabile dal client.
    private String clientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            String[] hops = forwardedFor.split(",");
            return hops[hops.length - 1].trim();
        }
        return request.getRemoteAddr();
    }

    /*Postman parameters
    {
        "name": "Prova",
            "surname": "Prova",
            "username": "prova",
            "email": "prova@gmail.it",
            "password": "prova"
    }*/
    @PostMapping("/signup")
    public ResponseEntity<String> registration(
            @Valid @RequestBody RegistrationDTO registrationDTO) {
        // registro su mongo
        ResponseEntity<String> responseEntity = loginService.registrate(registrationDTO);
        if (responseEntity.getStatusCode() != HttpStatus.CREATED) {
            log.warn(
                    "Registrazione rifiutata per l'utente {}: {}",
                    registrationDTO.getUsername(),
                    responseEntity.getBody());
            return responseEntity;
        }
        // aggiungo in neo4j
        ResponseEntity<String> response =
                userNeo4jService.addUser(responseEntity.getBody(), registrationDTO.getUsername());
        if (response.getStatusCode() == HttpStatus.CREATED) {
            loginService.sendVerificationEmail(responseEntity.getBody());
            log.info("Registrazione completata per l'utente {}", registrationDTO.getUsername());
            return response;
        }
        // neo4j ha fallito la creazione -> rimuovere utente in mongo. La rimozione riesce anche
        // quando la registrazione e' fallita, quindi non se ne puo' restituire lo stato: prima si
        // rispondeva 200 OK e il frontend festeggiava un account mai creato.
        log.error(
                "Creazione utente in Neo4j fallita per {}, rollback dell'utente Mongo {}",
                registrationDTO.getUsername(),
                responseEntity.getBody());
        loginService.removeUser(responseEntity.getBody());
        return new ResponseEntity<>(
                "Registrazione non riuscita, riprova piu tardi", HttpStatus.INTERNAL_SERVER_ERROR);
    }

    // Non serve elencarlo tra i permitAll in SecurityConfig: richiede gia' un Authorization
    // Bearer valido come ogni altro endpoint non elencato li'. Si legge l'header direttamente
    // invece di passare dal SecurityContext perche' serve il jti/scadenza grezzi del token, che
    // JwtAuthenticationFilter non propaga nell'Authentication (solo username e ruolo).
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            try {
                Claims claims = jwtService.parseToken(header.substring(7));
                long remainingMs = claims.getExpiration().getTime() - System.currentTimeMillis();
                tokenBlacklistService.revoke(claims.getId(), remainingMs);
                log.info("Logout effettuato per l'utente {}", claims.getSubject());
            } catch (JwtException e) {
                // token gia' scaduto o manomesso: niente da revocare
                log.debug("Logout con token non valido: {}", e.getMessage());
            }
        }
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/confirm-email")
    public ResponseEntity<String> confirmEmail(@RequestParam String token) {
        return loginService.confirmEmail(token);
    }

    // Risponde sempre 200 con lo stesso messaggio, che l'email corrisponda a un account o no
    // (vedi LoginService.requestPasswordReset). Riusa LoginRateLimiter con chiave per solo IP
    // (prefissata, per non collidere con quelle di /login): ogni richiesta conta, altrimenti un
    // client potrebbe usare l'endpoint per inondare di email la casella di chiunque.
    @PostMapping("/forgot-password")
    public ResponseEntity<String> forgotPassword(
            @Valid @RequestBody ForgotPasswordDTO forgotPasswordDTO, HttpServletRequest request) {
        String rateLimitKey = "forgot-password:" + clientIp(request);
        if (loginRateLimiter.isBlocked(rateLimitKey)) {
            long retryAfterSeconds = loginRateLimiter.remainingBlockSeconds(rateLimitKey);
            log.warn(
                    "Reset password rifiutato (rate limit attivo, riprova tra {}s)",
                    retryAfterSeconds);
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .header("Retry-After", String.valueOf(retryAfterSeconds))
                    .body("Troppe richieste, riprova tra qualche minuto");
        }
        loginRateLimiter.recordFailure(rateLimitKey);

        loginService.requestPasswordReset(forgotPasswordDTO.getEmail());
        return ResponseEntity.ok(
                "Se l'email e' associata a un account, riceverai un link per reimpostare la"
                        + " password");
    }

    @PostMapping("/reset-password")
    public ResponseEntity<String> resetPassword(
            @Valid @RequestBody ResetPasswordDTO resetPasswordDTO) {
        ResponseEntity<String> response =
                loginService.resetPassword(
                        resetPasswordDTO.getToken(), resetPasswordDTO.getNewPassword());
        if (response.getStatusCode() == HttpStatus.OK) {
            log.info("Password reimpostata tramite link di reset");
        } else {
            log.warn("Reimpostazione password rifiutata: {}", response.getStatusCode());
        }
        return response;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<String> handleValidationException(MethodArgumentNotValidException ex) {
        String message =
                ex.getBindingResult().getFieldErrors().stream()
                        .findFirst()
                        .map(FieldError::getDefaultMessage)
                        .orElse("Dati non validi");
        return new ResponseEntity<>(message, HttpStatus.BAD_REQUEST);
    }
}
