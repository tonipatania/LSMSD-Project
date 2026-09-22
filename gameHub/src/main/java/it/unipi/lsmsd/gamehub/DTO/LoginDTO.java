package it.unipi.lsmsd.gamehub.DTO;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
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

    // Stessa policy della registrazione (vedi RegistrationDTO): a differenza della password, lo
    // username e' in chiaro e ogni account registrato dall'app la rispetta gia', quindi qui puo'
    // essere irrigidita senza rischiare di bloccare utenti esistenti (verificato sul dump: i soli
    // username che non rispettano il pattern appartengono ai record Kaggle senza campo `enabled`,
    // rimossi da scripts/cleanup_prod_db.py).
    @NotBlank(message = "Lo username e' obbligatorio")
    @Pattern(
            regexp = "^[A-Za-z0-9._-]{3,20}$",
            message =
                    "Lo username puo' contenere solo lettere, numeri, punti, underscore e"
                            + " trattini, senza spazi (3-20 caratteri)")
    private String username;

    // Solo un tetto massimo qui, niente requisiti di complessita': un utente registrato prima
    // di questa policy deve poter continuare ad autenticarsi con la sua password attuale. Il
    // valore deve restare allineato al massimo imposto in registrazione (vedi RegistrationDTO).
    @NotBlank(message = "La password e' obbligatoria")
    @Size(max = 32, message = "La password non puo' superare i 32 caratteri")
    private String password;
}
