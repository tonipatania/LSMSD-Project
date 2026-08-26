package it.unipi.lsmsd.gamehub.DTO;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@AllArgsConstructor
@Getter
@Setter
@ToString
public class RegistrationDTO {

    @NotBlank(message = "Il nome e' obbligatorio")
    private String name;

    @NotBlank(message = "Il cognome e' obbligatorio")
    private String surname;

    @NotBlank(message = "Lo username e' obbligatorio")
    private String username;

    // Tra 8 e 32 caratteri, con una maiuscola e un carattere speciale. 32 e' una scelta di UX
    // (lunghezza tipica richiesta dalla maggior parte dei siti), non un vincolo tecnico: resta
    // ben al di sotto dei 72 byte oltre i quali BCryptPasswordEncoder (vedi SecurityConfig)
    // tronca silenziosamente l'input.
    @Pattern(
            regexp = "^(?=.*[A-Z])(?=.*[^A-Za-z0-9]).{8,32}$",
            message =
                    "La password deve essere lunga tra 8 e 32 caratteri, con almeno una lettera"
                            + " maiuscola e un carattere speciale")
    private String password;

    @NotBlank(message = "L'email e' obbligatoria")
    @Email(message = "Email non valida")
    private String email;
}
