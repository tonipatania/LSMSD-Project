package it.unipi.lsmsd.gamehub.controller;

import it.unipi.lsmsd.gamehub.DTO.LoginDTO;
import it.unipi.lsmsd.gamehub.DTO.RegistrationDTO;
import it.unipi.lsmsd.gamehub.service.ILoginService;
import it.unipi.lsmsd.gamehub.service.IUserNeo4jService;
import it.unipi.lsmsd.gamehub.utils.AuthResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@Slf4j
public class LoginController {
    @Autowired private ILoginService loginService;

    @Autowired private IUserNeo4jService userNeo4jService;

    /*Postman parameters
    {
        "username": "Lunark",
        "password": "jrmag6azycv"
    }*/
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody LoginDTO loginDTO) {
        AuthResponse authResponse = loginService.authenticate(loginDTO);
        if (authResponse.isSuccess()) {
            log.info("Login riuscito per l'utente {}", loginDTO.getUsername());
            return ResponseEntity.ok(authResponse);
        } else {
            log.warn("Tentativo di login fallito per l'utente {}", loginDTO.getUsername());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(authResponse);
        }
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
    public ResponseEntity<String> registration(@RequestBody RegistrationDTO registrationDTO) {
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
}
