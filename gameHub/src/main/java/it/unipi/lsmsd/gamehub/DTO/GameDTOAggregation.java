package it.unipi.lsmsd.gamehub.DTO;

import lombok.*;
import org.springframework.data.annotation.Id;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@ToString
public class GameDTOAggregation {
    @Id
    private String genres;
    private double avgScore;
    private int count;
}
