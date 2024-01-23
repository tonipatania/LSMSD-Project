package it.unipi.lsmsd.gamehub.service.impl;

import com.mongodb.MongoException;
import it.unipi.lsmsd.gamehub.DTO.LoginDTO;
import it.unipi.lsmsd.gamehub.DTO.RegistrationDTO;
import it.unipi.lsmsd.gamehub.model.User;
import it.unipi.lsmsd.gamehub.repository.LoginRepository;
import it.unipi.lsmsd.gamehub.service.ILoginService;
import it.unipi.lsmsd.gamehub.utils.AuthResponse;
import org.springframework.beans.factory.annotation.Autowired;
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
    public ResponseEntity<Object> roleUser(String userId) {
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

        User u = loginRepository.findByUsername(username);
        return Objects.equals(u.getPassword(), password);

    }

    public boolean registrate(RegistrationDTO registrationDTO){
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
                return false;
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
            return true;
        }catch (Exception e){
            System.out.println(e.getMessage());
           return false;
        }

    }
}
