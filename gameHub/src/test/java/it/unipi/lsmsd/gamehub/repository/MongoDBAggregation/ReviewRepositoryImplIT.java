package it.unipi.lsmsd.gamehub.repository.MongoDBAggregation;

import static org.assertj.core.api.Assertions.assertThat;

import it.unipi.lsmsd.gamehub.DTO.ReviewDTOAggregation;
import it.unipi.lsmsd.gamehub.DTO.ReviewDTOAggregation2;
import it.unipi.lsmsd.gamehub.model.Review;
import it.unipi.lsmsd.gamehub.support.IntegrationTestSupport;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class ReviewRepositoryImplIT extends IntegrationTestSupport {

    @Autowired private ReviewRepositoryImpl reviewRepositoryImpl;

    private Review review(String title, String username, int userScore, int likeCount) {
        Review review = new Review();
        review.setTitle(title);
        review.setUsername(username);
        review.setUserScore(userScore);
        review.setComment("comment");
        review.setLikeCount(likeCount);
        return review;
    }

    @Test
    void findAggregation2_groupsByUsernameAndSumsLikeCountDescending() {
        // two reviews from "Lunark" (likeCount 5+7=12), one from "Kaistlin" (likeCount 3).
        mongoTemplate.save(review("Game A", "Lunark", 8, 5));
        mongoTemplate.save(review("Game B", "Lunark", 6, 7));
        mongoTemplate.save(review("Game A", "Kaistlin", 9, 3));

        List<ReviewDTOAggregation> result = reviewRepositoryImpl.findAggregation2();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getUsername()).isEqualTo("Lunark");
        assertThat(result.get(0).getLikeCount()).isEqualTo(12);
        assertThat(result.get(1).getUsername()).isEqualTo("Kaistlin");
        assertThat(result.get(1).getLikeCount()).isEqualTo(3);
    }

    @Test
    void findAggregation3_groupsByTitleWithAverageUserScoreDescending() {
        // "Game A" reviews average to 8, "Game B" reviews average to 4.
        mongoTemplate.save(review("Game A", "Lunark", 6, 0));
        mongoTemplate.save(review("Game A", "Kaistlin", 10, 0));
        mongoTemplate.save(review("Game B", "Lunark", 4, 0));

        List<ReviewDTOAggregation2> result = reviewRepositoryImpl.findAggregation3();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getTitle()).isEqualTo("Game A");
        assertThat(result.get(0).getUserscore()).isEqualTo(8);
        assertThat(result.get(0).getCount()).isEqualTo(2);
        assertThat(result.get(1).getTitle()).isEqualTo("Game B");
        assertThat(result.get(1).getUserscore()).isEqualTo(4);
        assertThat(result.get(1).getCount()).isEqualTo(1);
    }
}
