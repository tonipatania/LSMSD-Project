package it.unipi.lsmsd.gamehub.model;

import java.util.List;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@ToString
@Document(collection = "games")
@CompoundIndexes({@CompoundIndex(name = "genres_avgScore", def = "{'genres' : 1, 'avgScore': 1}")})
public class Game {
    @Id private String id;

    @Field("name")
    private String name;

    @Indexed
    @Field("genres")
    private String genres;

    @Field("releaseDate")
    private String releaseDate;

    @Indexed
    @Field("avgScore")
    private int avgScore;

    @Field("Price")
    private double price;

    @Field("About the game")
    private String aboutTheGame;

    @Field("Supported languages")
    private String supportedLanguages;

    @Field("Developers")
    private String developers;

    @Field("Publishers")
    private String publishers;

    @Field("Categories")
    private String categories;

    @Field("URL")
    private URL URL;

    @Field("Reviews")
    private List<Review> reviews;

    // Precalcolato ad ogni scrittura delle Reviews embedded (vedi
    // GameService.updateGameReviewFromScratch): rispecchia "la prima review embedded ha un
    // commento non vuoto", lo stesso criterio prima espresso come filtro Reviews.0.Comment != ''.
    // Un campo dedicato e indicizzato evita il COLLSCAN che quel filtro causava sull'array
    // embedded.
    @Indexed
    @Field("hasReviews")
    private boolean hasReviews;
}
