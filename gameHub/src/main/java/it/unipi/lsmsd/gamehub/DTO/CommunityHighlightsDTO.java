package it.unipi.lsmsd.gamehub.DTO;

import java.util.List;
import lombok.*;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@ToString
public class CommunityHighlightsDTO {
    private List<TrendingReviewDTO> trendingReviews;
    private List<HotGameDTO> hotGames;
}
