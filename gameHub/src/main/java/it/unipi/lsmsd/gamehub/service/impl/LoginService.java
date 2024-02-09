package it.unipi.lsmsd.gamehub.service.impl;

import com.mongodb.MongoException;
import it.unipi.lsmsd.gamehub.DTO.LoginDTO;
import it.unipi.lsmsd.gamehub.DTO.RegistrationDTO;
import it.unipi.lsmsd.gamehub.model.User;
import it.unipi.lsmsd.gamehub.repository.LoginRepository;
import it.unipi.lsmsd.gamehub.service.ILoginService;
import it.unipi.lsmsd.gamehub.utils.AuthResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.Optional;

@Service
public class LoginService implements ILoginService {
    @Autowired
    private LoginRepository loginRepository;

    @Override
    public AuthResponse authenticate(LoginDTO loginDTO) {
        // retrieve value
        String username = loginDTO.getUsername();
        String password = loginDTO.getPassword();
        try {
            User u = loginRepository.findByUsername(username);
            if(Objects.equals(u.getPassword(), password)) {
                return new AuthResponse(true, "Login Successful", username);
            }
            else {
                return new AuthResponse(false, "Invalid username or password", null);
            }
        }
        catch (MongoException e) {
            System.out.println("Errore durante il recupero dell'utente da MongoDB: " + e.getMessage());
            return new AuthResponse(false, "Error occurred while authenticating", null);
        }
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
            //User u=loginRepository.findByUsername(username);
            User existingUser = loginRepository.findByUsername(username);

            // If the user with the same username exists, return false
            if (existingUser != null) {
                return new ResponseEntity<>("username already present, try again with another", HttpStatus.UNAUTHORIZED);
            }

            // If the user with the same username doesn't exist, you can proceed with registration logic
            // We want to create a new User object and save it to the database

            User newUser = new User();
            newUser.setName(name);
            newUser.setSurname(surname);
            newUser.setUsername(username);
            newUser.setPassword(password);
            newUser.setEmail(email);

            // Save the new user to the database
            loginRepository.save(newUser);

            // Return true to indicate successful registration
            return new ResponseEntity<>(newUser.getId(), HttpStatus.CREATED);
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
