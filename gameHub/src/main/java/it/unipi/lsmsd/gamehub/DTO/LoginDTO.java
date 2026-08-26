package it.unipi.lsmsd.gamehub.DTO;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@AllArgsConstructor
@Getter
@Setter
@ToString
public class LoginDTO {

    @NotBlank(message = "Lo username e' obbligatorio")
    private String username;

    // Solo un tetto massimo qui, niente requisiti di complessita': un utente registrato prima
    // di questa policy deve poter continuare ad autenticarsi con la sua password attuale. Il
    // valore deve restare allineato al massimo imposto in registrazione (vedi RegistrationDTO).
    @NotBlank(message = "La password e' obbligatoria")
    @Size(max = 32, message = "La password non puo' superare i 32 caratteri")
    private String password;
}
