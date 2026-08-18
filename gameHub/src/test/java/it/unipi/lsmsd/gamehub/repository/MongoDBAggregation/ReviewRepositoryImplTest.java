package it.unipi.lsmsd.gamehub.repository.MongoDBAggregation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import it.unipi.lsmsd.gamehub.DTO.ReviewDTOAggregation;
import it.unipi.lsmsd.gamehub.DTO.ReviewDTOAggregation2;
import java.util.List;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;

@ExtendWith(MockitoExtension.class)
class ReviewRepositoryImplTest {

    @Mock private MongoTemplate mongoTemplate;
    @InjectMocks private ReviewRepositoryImpl reviewRepositoryImpl;

    @Test
    void findAggregation2_delegatesToMongoTemplateAggregateOnReviewsCollection() {
        List<ReviewDTOAggregation> expected = List.of(new ReviewDTOAggregation("Kaistlin", 12));
        when(mongoTemplate.aggregate(
                        any(Aggregation.class), eq("reviews"), eq(ReviewDTOAggregation.class)))
                .thenReturn(new AggregationResults<>(expected, new Document()));

        List<ReviewDTOAggregation> result = reviewRepositoryImpl.findAggregation2();

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void findAggregation3_delegatesToMongoTemplateAggregateOnReviewsCollection() {
        List<ReviewDTOAggregation2> expected =
                List.of(new ReviewDTOAggregation2("BARRIER X", 8, 4));
        when(mongoTemplate.aggregate(
                        any(Aggregation.class), eq("reviews"), eq(ReviewDTOAggregation2.class)))
                .thenReturn(new AggregationResults<>(expected, new Document()));

        List<ReviewDTOAggregation2> result = reviewRepositoryImpl.findAggregation3();

        assertThat(result).isEqualTo(expected);
    }
}
