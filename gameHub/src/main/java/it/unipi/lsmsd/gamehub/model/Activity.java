package it.unipi.lsmsd.gamehub.model;

import java.time.Instant;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

// registro delle azioni mostrate nel feed della Home e usato per le statistiche "community"
// (recensioni in tendenza, giochi piu' desiderati). E' l'unica fonte con un timestamp reale: ne'
// la relazione ADD (wishlist) ne' LIKE/FOLLOW su Neo4j ne' la Review su Mongo ne registrano uno.
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@ToString
@Document(collection = "activities")
@CompoundIndexes({
    @CompoundIndex(name = "username_createdAt", def = "{'username': 1, 'createdAt': -1}"),
    // per le aggregazioni community, che filtrano per tipo in una finestra temporale recente
    @CompoundIndex(name = "type_createdAt", def = "{'type': 1, 'createdAt': -1}")
})
public class Activity {
    @Id private String id;

    @Field("username")
    private String username;

    @Field("type")
    private ActivityType type;

    // gioco coinvolto: assente per il tipo FOLLOW
    @Field("gameName")
    private String gameName;

    // valorizzato per i tipi REVIEW e LIKE_REVIEW
    @Field("reviewId")
    private String reviewId;

    // valorizzato solo per il tipo REVIEW
    @Field("score")
    private Integer score;

    // valorizzato solo per il tipo FOLLOW: l'utente che e' stato seguito
    @Field("targetUsername")
    private String targetUsername;

    @Field("createdAt")
    private Instant createdAt;
}
