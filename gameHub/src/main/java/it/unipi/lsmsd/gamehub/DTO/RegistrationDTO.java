package it.unipi.lsmsd.gamehub.DTO;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@AllArgsConstructor
@Getter
@Setter
@ToString

public class RegistrationDTO {

    private String name;
    private String surname;
    private String username;
    private String password;
    private String email;
}
