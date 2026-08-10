package it.unipi.lsmsd.gamehub.repository.MongoDBAggregation;

import static org.springframework.data.mongodb.core.aggregation.Aggregation.*;

import it.unipi.lsmsd.gamehub.DTO.ReviewDTOAggregation;
import it.unipi.lsmsd.gamehub.DTO.ReviewDTOAggregation2;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.*;
import org.springframework.stereotype.Repository;

@Repository
public class ReviewRepositoryImpl implements ReviewRepositoryCustom {
    private final MongoTemplate mongoTemplate;

    @Autowired
    public ReviewRepositoryImpl(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public List<ReviewDTOAggregation> findAggregation2() {
        GroupOperation groupByUser = group("Username").sum("likeCount").as("likeCount");
        SortOperation sortByLikeCountDesc = sort(Sort.by(Sort.Direction.DESC, "likeCount"));
        // GroupOperation groupFirstAndLast=group().first("_id.username").as("maxlikeCount");
        LimitOperation limitToTenFirstDoc = limit(10);

        Aggregation aggregation =
                newAggregation(groupByUser, sortByLikeCountDesc, limitToTenFirstDoc);
        return mongoTemplate
                .aggregate(aggregation, "reviews", ReviewDTOAggregation.class)
                .getMappedResults();
    }

    @Override
    public List<ReviewDTOAggregation2> findAggregation3() {
        GroupOperation groupOperation =
                group("Title").avg("Userscore").as("Userscore").count().as("count");
        SortOperation sortByUserScore = sort(Sort.by(Sort.Direction.DESC, "Userscore"));
        Aggregation aggregation = newAggregation(groupOperation, sortByUserScore);
        return mongoTemplate
                .aggregate(aggregation, "reviews", ReviewDTOAggregation2.class)
                .getMappedResults();
    }
}
