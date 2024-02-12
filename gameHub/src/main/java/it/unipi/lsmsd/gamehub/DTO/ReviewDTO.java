package it.unipi.lsmsd.gamehub.DTO;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import org.springframework.data.mongodb.core.mapping.Field;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@ToString
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ReviewDTO {
    private String id;
    private String title;
    private int userScore;
    private String comment;
    private String username;

}
