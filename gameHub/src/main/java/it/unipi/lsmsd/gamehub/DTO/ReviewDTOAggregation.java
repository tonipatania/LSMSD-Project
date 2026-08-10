package it.unipi.lsmsd.gamehub.DTO;

import lombok.*;
import org.springframework.data.annotation.Id;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@ToString
public class ReviewDTOAggregation {
    @Id private String Username;
    private Integer likeCount;
}
