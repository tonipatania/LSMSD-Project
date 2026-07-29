package it.unipi.lsmsd.gamehub.service.impl;

import com.mongodb.MongoException;
import it.unipi.lsmsd.gamehub.DTO.LoginDTO;
import it.unipi.lsmsd.gamehub.DTO.RegistrationDTO;
import it.unipi.lsmsd.gamehub.model.User;
import it.unipi.lsmsd.gamehub.repository.LoginRepository;
import it.unipi.lsmsd.gamehub.security.JwtService;
import it.unipi.lsmsd.gamehub.service.ILoginService;
import it.unipi.lsmsd.gamehub.utils.AuthResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.Optional;

@Service
public class LoginService implements ILoginService {
    @Autowired
    private LoginRepository loginRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

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

            String token = jwtService.generateToken(u.getUsername(), resolveRole(u));
            return new AuthResponse(true, "Login Successful", u.getUsername(), token, u.getRole());
        }
        catch (MongoException e) {
            System.out.println("Errore durante il recupero dell'utente da MongoDB: " + e.getMessage());
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
            if(role == null) {
                return new ResponseEntity<>("you do not have permissions for this operation", HttpStatus.UNAUTHORIZED);
            }
            return ResponseEntity.ok(role);
        }
        catch (MongoException e) {
            System.out.println("Errore durante il recupero dell'utente da MongoDB: " + e.getMessage());
            return new ResponseEntity<>("Errore durante il recupero del ruolo dell'utente", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    public ResponseEntity<String> registrate(RegistrationDTO registrationDTO){
        try {
            //registrate value
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
                return new ResponseEntity<>("Username gia' in uso, scegline un altro", HttpStatus.CONFLICT);
            }
            if (loginRepository.existsByEmail(email)) {
                return new ResponseEntity<>("Email gia' registrata, usane un'altra o accedi", HttpStatus.CONFLICT);
            }

            // If the user with the same username doesn't exist, you can proceed with registration logic
            // We want to create a new User object and save it to the database

            User newUser = new User();
            newUser.setName(name);
            newUser.setSurname(surname);
            newUser.setUsername(username);
            newUser.setPassword(passwordEncoder.encode(password));
            newUser.setEmail(email);

            // Save the new user to the database
            loginRepository.save(newUser);

            // Return true to indicate successful registration
            return new ResponseEntity<>(newUser.getId(), HttpStatus.CREATED);
        }catch (DuplicateKeyException e){
            // due signup concorrenti possono superare entrambi i controlli sopra: qui a rifiutare
            // e' l'indice unico, e il messaggio dipende da quale dei due indici ha fatto scattare
            // l'errore
            String message = e.getMessage() != null && e.getMessage().contains("email")
                    ? "Email gia' registrata, usane un'altra o accedi"
                    : "Username gia' in uso, scegline un altro";
            return new ResponseEntity<>(message, HttpStatus.CONFLICT);
        }catch (Exception e){
            return new ResponseEntity<>("Error in interaction with Mongo" + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
    public ResponseEntity<String> removeUser(String userId) {
        try {
            loginRepository.deleteById(userId);
            return new ResponseEntity<>("try the registration again later", HttpStatus.OK);
        }
        catch (Exception e) {
            return new ResponseEntity<>("error with Mongo" + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
    public ResponseEntity<String> updateUser(String username, String newUsername) {
        try {
            // controllo se e gia presente il nuovo username
            User existingUser = loginRepository.findByUsername(newUsername);
            if (existingUser != null) {
                // username gia presente
                return new ResponseEntity<>("username already used, try again with another username", HttpStatus.CONFLICT);
            }
            // aggiorno username
            User u = loginRepository.findByUsername(username);
            u.setUsername(newUsername);
            u = loginRepository.save(u);
            return new ResponseEntity<>("username updated in mongo", HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>("error in updating username in mongo: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}
