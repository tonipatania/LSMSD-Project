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
}
