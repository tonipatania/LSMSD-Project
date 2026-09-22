package it.unipi.lsmsd.gamehub.DTO;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Niente @ToString: token e nuova password non devono poter finire nei log per errore.
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class ResetPasswordDTO {

    @NotBlank(message = "Il link di reimpostazione non e' valido")
    private String token;

    // Stessa policy della registrazione (vedi RegistrationDTO): va tenuta allineata. @Pattern da
    // solo accetta null, quindi serve anche @NotBlank.
    @NotBlank(message = "La password e' obbligatoria")
    @Pattern(
            regexp = "^(?=.*[A-Z])(?=.*[^A-Za-z0-9\\s])\\S{8,32}$",
            message =
                    "La password deve essere lunga tra 8 e 32 caratteri, senza spazi, con almeno"
                            + " una lettera maiuscola e un carattere speciale")
    private String newPassword;
}
