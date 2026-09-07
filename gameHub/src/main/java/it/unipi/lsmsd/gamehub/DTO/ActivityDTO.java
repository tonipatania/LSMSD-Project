package it.unipi.lsmsd.gamehub.DTO;

import it.unipi.lsmsd.gamehub.model.ActivityType;
import java.time.Instant;
import lombok.*;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@ToString
public class ActivityDTO {
    private String username;
    private ActivityType type;
    private String gameName;
    private String gameHeaderImage;
    private Integer score;
    private Instant createdAt;
}
