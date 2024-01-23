package it.unipi.lsmsd.gamehub.DTO;

import lombok.*;
import org.springframework.data.annotation.Id;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@ToString
public class GameDTOAggregation2 {
    @Id
    private int year;
    private Double avgScore;
    private int count;
}
