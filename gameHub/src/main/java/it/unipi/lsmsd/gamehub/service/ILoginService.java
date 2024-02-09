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
    public ResponseEntity<String> updateUser(String username, String newUsername);
}
