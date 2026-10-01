package it.unipi.lsmsd.gamehub.service;

import org.springframework.http.ResponseEntity;

public interface IAccountService {
    // Elimina l'account di username e tutti i dati collegati (recensioni, risposte, like, follow,
    // wishlist, notifiche, attivita'), su Mongo e su Neo4j. La password serve a riconfermare
    // l'identita'.
    ResponseEntity<String> deleteAccount(String username, String password);
}
