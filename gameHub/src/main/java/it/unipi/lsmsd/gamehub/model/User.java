package it.unipi.lsmsd.gamehub.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@ToString
@Document(collection = "users")
public class User {

    @Id private String id;

    @Indexed(unique = true)
    private String username;

    private String name;
    private String surname;
    private String password;

    @Indexed(unique = true)
    private String email;

    private String role;

    // Boolean (non boolean) apposta: i documenti del seed dataset precedenti a questa feature
    // non hanno il campo, che quindi deserializza a null. null viene trattato come "confermato"
    // in LoginService.authenticate, cosi' gli account gia' esistenti restano utilizzabili - stessa
    // logica di compatibilita' gia' usata per la migrazione lazy delle password in chiaro.
    private Boolean enabled;

    private String verificationToken;
    private Long verificationTokenExpiry;

    // Del token di reset password si salva solo l'hash SHA-256: chi leggesse il database non
    // potrebbe usarlo per reimpostare la password di nessuno (il token in chiaro esiste solo
    // nell'email inviata all'utente).
    private String passwordResetTokenHash;
    private Long passwordResetTokenExpiry;
}
