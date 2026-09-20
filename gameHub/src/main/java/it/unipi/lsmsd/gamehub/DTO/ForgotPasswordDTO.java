package it.unipi.lsmsd.gamehub.DTO;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

// Il NoArgsConstructor serve a Jackson: con un solo campo, il solo AllArgsConstructor verrebbe
// interpretato come costruttore "delegating" invece che come costruttore per proprieta'.
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@ToString
public class ForgotPasswordDTO {

    @NotBlank(message = "L'email e' obbligatoria")
    @Email(message = "Email non valida")
    private String email;
}
