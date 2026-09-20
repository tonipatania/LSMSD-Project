package it.unipi.lsmsd.gamehub.DTO;

import lombok.*;

// versione ridotta di Game per le card del feed: niente descrizioni ne' recensioni embedded,
// che da sole peserebbero piu' di tutto il resto del payload
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@ToString
public class GameSnippetDTO {
    private String id;
    private String name;
    private String headerImage;
    private String genres;
    private int avgScore;
    private double price;
}
