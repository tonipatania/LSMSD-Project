package it.unipi.lsmsd.gamehub.DTO;

import lombok.*;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@ToString
public class TrendingReviewDTO {
    private ReviewSnippetDTO review;
    private String gameHeaderImage;
    // like ricevuti dalla recensione nella finestra considerata (non il totale storico)
    private int recentLikes;
    private int windowHours;
}
