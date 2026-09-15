package it.unipi.lsmsd.gamehub.model;

import java.time.Instant;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

// registro delle azioni mostrate nel feed "attivita' amici" della Home. E' l'unica fonte con un
// timestamp reale: ne' la relazione ADD (wishlist) ne' la Review su Mongo ne registrano uno oggi.
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@ToString
@Document(collection = "activities")
@CompoundIndexes({
    @CompoundIndex(name = "username_createdAt", def = "{'username': 1, 'createdAt': -1}")
})
public class Activity {
    @Id private String id;

    @Field("username")
    private String username;

    @Field("type")
    private ActivityType type;

    @Field("gameName")
    private String gameName;

    // valorizzato solo per il tipo REVIEW
    @Field("reviewId")
    private String reviewId;

    // valorizzato solo per il tipo REVIEW
    @Field("score")
    private Integer score;

    @Field("createdAt")
    private Instant createdAt;
}
