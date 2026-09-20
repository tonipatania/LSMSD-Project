package it.unipi.lsmsd.gamehub.model;

import java.time.Instant;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

// "segnalibro" del feed della Home: fino a quando l'utente ha effettivamente visto le attivita'.
// Tutto cio' che e' piu' recente di lastSeenAt e' "nuovo" per lui. Un solo documento per utente.
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@ToString
@Document(collection = "feed_states")
public class FeedState {
    @Id private String username;

    @Field("lastSeenAt")
    private Instant lastSeenAt;
}
