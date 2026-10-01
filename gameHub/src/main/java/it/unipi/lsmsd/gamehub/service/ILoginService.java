package it.unipi.lsmsd.gamehub.service;

import it.unipi.lsmsd.gamehub.DTO.LoginDTO;
import it.unipi.lsmsd.gamehub.DTO.RegistrationDTO;
import it.unipi.lsmsd.gamehub.utils.AuthResponse;
import org.springframework.http.ResponseEntity;

public interface ILoginService {
    public AuthResponse authenticate(LoginDTO loginDTO);

    public ResponseEntity<String> roleUser(String userId);

    public ResponseEntity<String> registrate(RegistrationDTO registrationDTO);

    public ResponseEntity<String> removeUser(String userId);

    // true se la password e' quella dell'utente (stessa logica del login, compresa la migrazione
    // delle password in chiaro del dump): serve per riconfermare l'identita' prima di azioni
    // irreversibili come la cancellazione dell'account
    public boolean checkPassword(String username, String rawPassword);

    public ResponseEntity<String> updateUser(String username, String newUsername);

    public void sendVerificationEmail(String userId);

    public ResponseEntity<String> confirmEmail(String token);

    public void requestPasswordReset(String email);

    public ResponseEntity<String> resetPassword(String token, String newPassword);
}
