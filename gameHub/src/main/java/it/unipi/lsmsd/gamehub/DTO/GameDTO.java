package it.unipi.lsmsd.gamehub.DTO;

import lombok.*;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@ToString
public class GameDTO {
    private String id;
    private String name;
    private String releaseDate;
    private Double price;
    private String aboutTheGame;
    private String supportedLanguages;
    private String developers;
    private String publishers;
    private String categories;
    private String genres;
    // mettere i voti
}
