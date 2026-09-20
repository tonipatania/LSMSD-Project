package it.unipi.lsmsd.gamehub.model;

import java.time.Instant;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

// risposta di un utente a una recensione altrui. Sta in una collezione a parte e non dentro la
// Review: le recensioni vengono copiate (embedded) nei documenti Game, e tenere li' anche le
// risposte le farebbe divergere a ogni scrittura. Non esiste il "rispondi a una risposta": il
// thread e' piatto, una recensione ha N risposte.
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@ToString
@Document(collection = "review_replies")
@CompoundIndex(name = "reviewId_createdAt", def = "{'reviewId': 1, 'createdAt': 1}")
public class ReviewReply {
    @Id private String id;

    @Field("reviewId")
    private String reviewId;

    @Field("username")
    private String username;

    @Field("comment")
    private String comment;

    @Field("createdAt")
    private Instant createdAt;
}
