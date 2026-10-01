package it.unipi.lsmsd.gamehub.DTO;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Niente @ToString: la password non deve poter finire nei log per errore. La si chiede di nuovo
// (anche se l'utente ha gia' un token valido) perche' la cancellazione e' irreversibile: un token
// rubato o una sessione lasciata aperta non devono bastare a distruggere l'account.
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class DeleteAccountDTO {

    @NotBlank(message = "La password e' obbligatoria")
    private String password;
}
