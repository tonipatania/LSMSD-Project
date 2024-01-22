package it.unipi.lsmsd.gamehub.model;


import it.unipi.lsmsd.gamehub.DTO.GameWishlistDTO;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.util.List;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@ToString
@Document(collection = "users")
public class User {

    @Id
    private String id;
    private String username;
    private String name;
    private String surname;
    private String password;
    private String email;
    @Field("games")
    private List<GameWishlistDTO> games;
    private String role;
}
