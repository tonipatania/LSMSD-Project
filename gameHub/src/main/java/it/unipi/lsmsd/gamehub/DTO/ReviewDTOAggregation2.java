package it.unipi.lsmsd.gamehub.DTO;

import lombok.*;
import org.springframework.data.annotation.Id;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@ToString
public class ReviewDTOAggregation2 {
    @Id
    private String Title;
    private int Userscore;
    private int count;
}
