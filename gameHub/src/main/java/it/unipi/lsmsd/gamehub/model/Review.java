package it.unipi.lsmsd.gamehub.model;

import java.time.Instant;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@ToString
@Document(collection = "reviews")
public class Review {
    @Id private String id;

    @Field("Title")
    private String title;

    @Field("Userscore")
    private int userScore;

    @Field("Comment")
    private String comment;

    @Field("Username")
    private String username;

    @Field("likeCount")
    private int likeCount;

    // assente sulle review pre-esistenti nel dump (null): usato solo per il feed attivita' amici
    // della Home, non per l'ordinamento delle review esistenti (quello resta su likeCount).
    @Field("createdAt")
    private Instant createdAt;
}
