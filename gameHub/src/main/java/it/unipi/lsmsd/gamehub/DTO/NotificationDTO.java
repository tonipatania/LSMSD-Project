package it.unipi.lsmsd.gamehub.DTO;

import com.fasterxml.jackson.annotation.JsonInclude;
import it.unipi.lsmsd.gamehub.model.NotificationType;
import java.time.Instant;
import lombok.*;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@ToString
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NotificationDTO {
    private String id;
    private NotificationType type;
    // chi ha compiuto l'azione
    private String actor;
    private String gameName;
    private String reviewId;
    private String excerpt;
    private boolean read;
    private Instant createdAt;

    // solo FOLLOW: vero se il destinatario segue gia' l'autore, cosi la lista non propone
    // "Segui anche tu" a chi l'ha gia' fatto
    private Boolean followingBack;
}
