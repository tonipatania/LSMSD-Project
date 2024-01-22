package it.unipi.lsmsd.gamehub.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@ToString
@Document(collection = "games")
public class Game {
    @Id
    private String id;
    private String Name;
    @Field("Release date")
    private String releaseDate;
    private Double Price;
    @Field("About the game")
    private String aboutTheGame;
    @Field("Supported languages")
    private String supportedLanguages;
    private String Developers;
    private String Publishers;
    private String Categories;
    private String Genres;
    // mettere i voti
}
