package it.unipi.lsmsd.gamehub.utils;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@AllArgsConstructor
@Getter
@Setter
@ToString
public class AuthResponse {
    private boolean success;
    private String errorMessage;
    // Codice stabile e non localizzato (es. "INVALID_CREDENTIALS") che il frontend mappa sulla
    // chiave i18n corretta: errorMessage resta in italiano per i log/Postman, ma non deve essere
    // mostrato direttamente all'utente perche' l'interfaccia supporta anche l'inglese.
    private String errorCode;
    private String username;
    private String token;
    private String role;

    public AuthResponse(boolean success, String errorMessage, String errorCode, String username) {
        this(success, errorMessage, errorCode, username, null, null);
    }
}
