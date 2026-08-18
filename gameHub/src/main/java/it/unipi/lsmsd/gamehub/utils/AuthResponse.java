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
    private String username;
    private String token;
    private String role;

    public AuthResponse(boolean success, String errorMessage, String username) {
        this(success, errorMessage, username, null, null);
    }
}
