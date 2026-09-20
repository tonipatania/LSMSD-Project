package it.unipi.lsmsd.gamehub.model;

import java.time.Instant;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

// "qualcuno ha fatto qualcosa che ti riguarda": nuovo follower, like a una tua recensione, risposta
// a una tua recensione. A differenza di Activity (il feed pubblico degli amici) qui c'e' un solo
// destinatario e uno stato letto/non letto. Le anteprime del testo sono una copia presa alla
// creazione: le recensioni e le risposte non sono modificabili, quindi non possono andare fuori
// sincrono, e la lista non deve fare join con altre collezioni.
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@ToString
@Document(collection = "notifications")
@CompoundIndexes({
    @CompoundIndex(name = "recipient_createdAt", def = "{'recipient': 1, 'createdAt': -1}"),
    // conteggio dei non letti e "segna tutte come lette" restano selettivi per utente
    @CompoundIndex(name = "recipient_read", def = "{'recipient': 1, 'read': 1}")
})
public class Notification {
    // oltre questa soglia una notifica non serve piu': il TTL la elimina senza un job dedicato
    public static final int TTL_DAYS = 90;

    @Id private String id;

    // chi riceve la notifica (mai uguale ad actor)
    @Field("recipient")
    private String recipient;

    @Field("type")
    private NotificationType type;

    // chi ha compiuto l'azione
    @Field("actor")
    private String actor;

    // gioco della recensione: assente per il tipo FOLLOW
    @Field("gameName")
    private String gameName;

    // recensione coinvolta: assente per il tipo FOLLOW
    @Field("reviewId")
    private String reviewId;

    // valorizzato solo per REPLY_REVIEW: serve a togliere la notifica se la risposta viene
    // cancellata
    @Field("replyId")
    private String replyId;

    // anteprima della recensione (LIKE_REVIEW) o della risposta (REPLY_REVIEW)
    @Field("excerpt")
    private String excerpt;

    @Field("read")
    private boolean read;

    @Indexed(expireAfterSeconds = TTL_DAYS * 24 * 60 * 60)
    @Field("createdAt")
    private Instant createdAt;
}
