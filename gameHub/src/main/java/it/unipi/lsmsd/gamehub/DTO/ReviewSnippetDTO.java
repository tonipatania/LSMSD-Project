package it.unipi.lsmsd.gamehub.DTO;

import lombok.*;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@ToString
public class ReviewSnippetDTO {
    private String id;
    private String title;
    private int userScore;
    private String comment;
    private String username;
    private int likeCount;
}
