package it.unipi.lsmsd.gamehub.DTO;

import com.fasterxml.jackson.annotation.JsonInclude;
import it.unipi.lsmsd.gamehub.model.ActivityType;
import java.time.Instant;
import lombok.*;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@ToString
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ActivityDTO {
    private String id;
    private String username;
    private ActivityType type;
    private String gameName;
    private String gameHeaderImage;
    private Integer score;
    private Instant createdAt;

    // true se l'attivita' e' successiva all'ultima volta in cui l'utente ha visto il feed
    private boolean unseen;

    // WISHLIST_ADD, REVIEW e LIKE_REVIEW: il gioco a cui si riferisce l'attivita'
    private GameSnippetDTO game;

    // REVIEW e LIKE_REVIEW: la recensione (per LIKE_REVIEW l'autore e' un altro utente)
    private ReviewSnippetDTO review;

    // FOLLOW: l'utente seguito e i suoi numeri
    private String targetUsername;
    private Integer targetWishlistCount;
    private Integer targetFollowers;
}
