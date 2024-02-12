package it.unipi.lsmsd.gamehub.controller;

import it.unipi.lsmsd.gamehub.DTO.LoginDTO;
import it.unipi.lsmsd.gamehub.DTO.RegistrationDTO;
import it.unipi.lsmsd.gamehub.model.UserNeo4j;
import it.unipi.lsmsd.gamehub.service.ILoginService;
import it.unipi.lsmsd.gamehub.service.IUserNeo4jService;
import it.unipi.lsmsd.gamehub.service.impl.UserNeo4jService;
import it.unipi.lsmsd.gamehub.utils.AuthResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public class LoginController {
    @Autowired
    private ILoginService loginService;

    @Autowired
    private IUserNeo4jService userNeo4jService;

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody LoginDTO loginDTO) {
        AuthResponse authResponse = loginService.authenticate(loginDTO);
        if(authResponse.isSuccess()) {
            return ResponseEntity.ok(authResponse);
        }
        else {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(authResponse);
        }
    }


    @PostMapping("/signup")
    public ResponseEntity<String> registration(@RequestBody RegistrationDTO registrationDTO){
        // registro su mongo
        ResponseEntity<String> responseEntity = loginService.registrate(registrationDTO);
        if(responseEntity.getStatusCode() != HttpStatus.CREATED){
            return responseEntity;
        }
        // aggiungo in neo4j
        ResponseEntity<String> response = userNeo4jService.addUser(responseEntity.getBody(), registrationDTO.getUsername());
        if(response.getStatusCode() == HttpStatus.CREATED)
            return response;
        // neo4j ha fallito la creazione -> rimuovere utente in mongo
        return loginService.removeUser(responseEntity.getBody());
    }


}
