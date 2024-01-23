package it.unipi.lsmsd.gamehub.repository.MongoDBAggregation;

import it.unipi.lsmsd.gamehub.DTO.GameDTOAggregation;
import it.unipi.lsmsd.gamehub.DTO.GameDTOAggregation2;
import it.unipi.lsmsd.gamehub.model.Game;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.GroupOperation;
import org.springframework.data.mongodb.core.aggregation.ProjectionOperation;
import org.springframework.data.mongodb.core.aggregation.SortOperation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Repository;

import java.util.List;

import static org.springframework.data.mongodb.core.aggregation.Aggregation.*;


@Repository
public class GameRepositoryImpl implements GameRepositoryCustom {

    private final MongoTemplate mongoTemplate;

    @Autowired
    public GameRepositoryImpl(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }
    @Override
    public List<GameDTOAggregation> findAggregation() {
    
        GroupOperation groupByGenres = group("genres")
                .avg("avgScore").as("avgScore")
                .count().as("count");

        SortOperation sortByAvgScoreDesc = sort(Sort.by(Sort.Direction.DESC, "avgScore"));



        Aggregation aggregation = newAggregation(groupByGenres, sortByAvgScoreDesc);
        return mongoTemplate.aggregate(aggregation, "games", GameDTOAggregation.class).getMappedResults();


    }

    @Override
    public List<GameDTOAggregation2> findAggregation4() {

        // Stage 2: Project
        ProjectionOperation projectOperation = Aggregation.project()
                .andExpression("{$year: {$toDate: '$releaseDate'}}").as("year")
                .and("avgScore").as("avgScore");

        // Stage 3: Group
        GroupOperation groupOperation = Aggregation.group("year")
                .count().as("count")
                .avg("avgScore").as("avgScore");

        // Stage 4: Sort
        SortOperation sortOperation = sort(Sort.by(Sort.Direction.DESC, "avgScore"));

        // Combine all stages
        Aggregation aggregation = newAggregation(
                projectOperation,
                groupOperation,
                sortOperation
        );

        // Execute aggregation
        return mongoTemplate.aggregate(aggregation, "games", GameDTOAggregation2.class).getMappedResults();
    }
}
