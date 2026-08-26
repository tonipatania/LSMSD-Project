package it.unipi.lsmsd.gamehub.service.impl;

import com.mongodb.MongoException;
import it.unipi.lsmsd.gamehub.DTO.LoginDTO;
import it.unipi.lsmsd.gamehub.DTO.RegistrationDTO;
import it.unipi.lsmsd.gamehub.model.User;
import it.unipi.lsmsd.gamehub.repository.LoginRepository;
import it.unipi.lsmsd.gamehub.security.JwtService;
import it.unipi.lsmsd.gamehub.service.IEmailService;
import it.unipi.lsmsd.gamehub.service.ILoginService;
import it.unipi.lsmsd.gamehub.utils.AuthResponse;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class LoginService implements ILoginService {
    @Autowired private LoginRepository loginRepository;

    @Autowired private PasswordEncoder passwordEncoder;

    @Autowired private JwtService jwtService;

    @Autowired private IEmailService emailService;

    @Value("${gamehub.email-verification.expiration-ms}")
    private long verificationExpirationMs;

    @Override
    public AuthResponse authenticate(LoginDTO loginDTO) {
        // retrieve value
        String username = loginDTO.getUsername();
        String password = loginDTO.getPassword();
        try {
            User u = loginRepository.findByUsername(username);
            if (u == null || !matchesPassword(u, password)) {
                return new AuthResponse(false, "Invalid username or password", null);
            }
            // enabled == null copre gli utenti del seed dataset, creati prima dell'introduzione
            // della conferma via email: solo false (impostato esplicitamente in registrate())
            // blocca il login.
            if (Boolean.FALSE.equals(u.getEnabled())) {
                return new AuthResponse(
                        false,
                        "Account non confermato: controlla la tua email per completare la"
                                + " registrazione",
                        null);
            }

            String token = jwtService.generateToken(u.getUsername(), resolveRole(u));
            return new AuthResponse(true, "Login Successful", u.getUsername(), token, u.getRole());
        } catch (MongoException e) {
            log.error("Errore durante il recupero dell'utente da MongoDB", e);
            return new AuthResponse(false, "Error occurred while authenticating", null);
        }
    }

    // il dump iniziale contiene password in chiaro: al primo login corretto vengono
    // sostituite con l'hash BCrypt, così i dati esistenti restano utilizzabili
    private boolean matchesPassword(User user, String rawPassword) {
        String stored = user.getPassword();
        if (stored == null) {
            return false;
        }
        if (stored.startsWith("$2a$") || stored.startsWith("$2b$") || stored.startsWith("$2y$")) {
            return passwordEncoder.matches(rawPassword, stored);
        }
        if (!Objects.equals(stored, rawPassword)) {
            return false;
        }
        user.setPassword(passwordEncoder.encode(rawPassword));
        loginRepository.save(user);
        return true;
    }

    // roleUser() considera admin qualunque utente con un ruolo valorizzato
    private String resolveRole(User user) {
        return user.getRole() == null ? "USER" : "ADMIN";
    }

    public ResponseEntity<String> roleUser(String userId) {
        try {
            Optional<User> user = loginRepository.findById(userId);
            String role = user.get().getRole();
            if (role == null) {
                return new ResponseEntity<>(
                        "you do not have permissions for this operation", HttpStatus.UNAUTHORIZED);
            }
            return ResponseEntity.ok(role);
        } catch (MongoException e) {
            log.error("Errore durante il recupero dell'utente da MongoDB", e);
            return new ResponseEntity<>(
                    "Errore durante il recupero del ruolo dell'utente",
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    public ResponseEntity<String> registrate(RegistrationDTO registrationDTO) {
        try {
            // registrate value
            String name = registrationDTO.getName();
            String surname = registrationDTO.getSurname();
            String username = registrationDTO.getUsername();
            String password = registrationDTO.getPassword();
            String email = registrationDTO.getEmail();

            // username ed email sono entrambi unici: 409 CONFLICT e' lo stato corretto per una
            // risorsa gia' esistente (prima si rispondeva 401, che il client interpreta come
            // "credenziali non valide"). I due casi hanno messaggi distinti perche' il form deve
            // poter dire all'utente quale dei due campi cambiare.
            if (loginRepository.existsByUsername(username)) {
                return new ResponseEntity<>(
                        "Username gia' in uso, scegline un altro", HttpStatus.CONFLICT);
            }
            if (loginRepository.existsByEmail(email)) {
                return new ResponseEntity<>(
                        "Email gia' registrata, usane un'altra o accedi", HttpStatus.CONFLICT);
            }

            // If the user with the same username doesn't exist, you can proceed with registration
            // logic
            // We want to create a new User object and save it to the database

            User newUser = new User();
            newUser.setName(name);
            newUser.setSurname(surname);
            newUser.setUsername(username);
            newUser.setPassword(passwordEncoder.encode(password));
            newUser.setEmail(email);
            // resta non confermato finche' non clicca il link ricevuto via email
            // (sendVerificationEmail, chiamato dal controller dopo la creazione in Neo4j)
            newUser.setEnabled(false);

            // Save the new user to the database
            loginRepository.save(newUser);

            // Return true to indicate successful registration
            return new ResponseEntity<>(newUser.getId(), HttpStatus.CREATED);
        } catch (DuplicateKeyException e) {
            // due signup concorrenti possono superare entrambi i controlli sopra: qui a rifiutare
            // e' l'indice unico, e il messaggio dipende da quale dei due indici ha fatto scattare
            // l'errore
            String message =
                    e.getMessage() != null && e.getMessage().contains("email")
                            ? "Email gia' registrata, usane un'altra o accedi"
                            : "Username gia' in uso, scegline un altro";
            log.warn("Registrazione concorrente rifiutata da indice unico Mongo", e);
            return new ResponseEntity<>(message, HttpStatus.CONFLICT);
        } catch (Exception e) {
            log.error("Errore in registrate", e);
            return new ResponseEntity<>(
                    "Error in interaction with Mongo" + e.getMessage(),
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    public ResponseEntity<String> removeUser(String userId) {
        try {
            loginRepository.deleteById(userId);
            return new ResponseEntity<>("try the registration again later", HttpStatus.OK);
        } catch (Exception e) {
            log.error("Errore in removeUser", e);
            return new ResponseEntity<>(
                    "error with Mongo" + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    public ResponseEntity<String> updateUser(String username, String newUsername) {
        try {
            // controllo se e gia presente il nuovo username
            User existingUser = loginRepository.findByUsername(newUsername);
            if (existingUser != null) {
                // username gia presente
                return new ResponseEntity<>(
                        "username already used, try again with another username",
                        HttpStatus.CONFLICT);
            }
            // aggiorno username
            User u = loginRepository.findByUsername(username);
            u.setUsername(newUsername);
            u = loginRepository.save(u);
            return new ResponseEntity<>("username updated in mongo", HttpStatus.OK);
        } catch (Exception e) {
            log.error("Errore in updateUser", e);
            return new ResponseEntity<>(
                    "error in updating username in mongo: " + e.getMessage(),
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public void sendVerificationEmail(String userId) {
        Optional<User> optionalUser = loginRepository.findById(userId);
        if (optionalUser.isEmpty()) {
            log.warn("Impossibile inviare l'email di conferma: utente {} non trovato", userId);
            return;
        }
        User user = optionalUser.get();
        String token = UUID.randomUUID().toString();
        user.setVerificationToken(token);
        user.setVerificationTokenExpiry(System.currentTimeMillis() + verificationExpirationMs);
        loginRepository.save(user);

        // Un fallimento dell'invio non deve far fallire la registrazione, gia' completata su
        // Mongo e Neo4j: viene solo loggato.
        try {
            emailService.sendVerificationEmail(user.getEmail(), user.getUsername(), token);
        } catch (Exception e) {
            log.error("Invio dell'email di conferma fallito per {}", user.getUsername(), e);
        }
    }

    @Override
    public ResponseEntity<String> confirmEmail(String token) {
        User user = loginRepository.findByVerificationToken(token);
        if (user == null) {
            return new ResponseEntity<>("Link di conferma non valido", HttpStatus.BAD_REQUEST);
        }
        if (Boolean.TRUE.equals(user.getEnabled())) {
            return new ResponseEntity<>("Account gia' confermato", HttpStatus.OK);
        }
        if (user.getVerificationTokenExpiry() == null
                || user.getVerificationTokenExpiry() < System.currentTimeMillis()) {
            return new ResponseEntity<>(
                    "Link di conferma scaduto, prova a registrarti di nuovo", HttpStatus.GONE);
        }

        // Il token NON viene invalidato qui apposta: un client email che pre-carica il link per
        // scansionarlo (es. Microsoft Safe Links, comune nelle caselle aziendali) o un doppio
        // click dell'utente arriverebbero altrimenti a un secondo /confirm-email con lo stesso
        // token gia' consumato, e senza il token ancora presente il controllo "gia' confermato"
        // sopra non potrebbe piu' trovare l'utente: si vedrebbero un falso "link non valido"
        // anche se il primo confirm e' andato a buon fine.
        user.setEnabled(true);
        loginRepository.save(user);
        return new ResponseEntity<>("Account confermato con successo", HttpStatus.OK);
    }
}
