package it.unipi.lsmsd.gamehub.repository.MongoDBAggregation;

import static org.springframework.data.mongodb.core.aggregation.Aggregation.*;

import it.unipi.lsmsd.gamehub.DTO.GameDTOAggregation;
import it.unipi.lsmsd.gamehub.DTO.GameDTOAggregation2;
import it.unipi.lsmsd.gamehub.model.Game;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationOperation;
import org.springframework.data.mongodb.core.aggregation.GroupOperation;
import org.springframework.data.mongodb.core.aggregation.ProjectionOperation;
import org.springframework.data.mongodb.core.aggregation.SortOperation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

@Repository
public class GameRepositoryImpl implements GameRepositoryCustom {

    private final MongoTemplate mongoTemplate;

    @Autowired
    public GameRepositoryImpl(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public List<GameDTOAggregation> findAggregation() {

        GroupOperation groupByGenres =
                group("genres").avg("avgScore").as("avgScore").count().as("count");

        SortOperation sortByAvgScoreDesc = sort(Sort.by(Sort.Direction.DESC, "avgScore"));

        Aggregation aggregation = newAggregation(groupByGenres, sortByAvgScoreDesc);
        return mongoTemplate
                .aggregate(aggregation, "games", GameDTOAggregation.class)
                .getMappedResults();
    }

    @Override
    public List<GameDTOAggregation2> findAggregation4() {

        // Stage 2: Project
        ProjectionOperation projectOperation =
                Aggregation.project()
                        .andExpression("{$year: {$toDate: '$releaseDate'}}")
                        .as("year")
                        .and("avgScore")
                        .as("avgScore");

        // Stage 3: Group
        GroupOperation groupOperation =
                Aggregation.group("year").count().as("count").avg("avgScore").as("avgScore");

        // Stage 4: Sort
        SortOperation sortOperation = sort(Sort.by(Sort.Direction.DESC, "avgScore"));

        // Combine all stages
        Aggregation aggregation = newAggregation(projectOperation, groupOperation, sortOperation);

        // Execute aggregation
        return mongoTemplate
                .aggregate(aggregation, "games", GameDTOAggregation2.class)
                .getMappedResults();
    }

    @Override
    public Page<Game> searchGames(
            String name, List<String> genres, Integer avgScore, Pageable pageable) {
        List<Criteria> criteriaList = new ArrayList<>();

        if (name != null && !name.isBlank()) {
            criteriaList.add(Criteria.where("name").regex(Pattern.quote(name.trim()), "i"));
        }
        if (genres != null && !genres.isEmpty()) {
            for (String genre : genres) {
                if (genre == null || genre.isBlank()) {
                    continue;
                }
                // genres is stored as a single comma-separated string (e.g. "Casual,Indie,Sports"),
                // so each selected genre must match as a whole comma-delimited token
                criteriaList.add(
                        Criteria.where("genres")
                                .regex(
                                        "(^|,)\\s*" + Pattern.quote(genre.trim()) + "\\s*(,|$)",
                                        "i"));
            }
        }
        if (avgScore != null) {
            criteriaList.add(Criteria.where("avgScore").gte(avgScore));
        }

        Query query = new Query();
        if (!criteriaList.isEmpty()) {
            query.addCriteria(new Criteria().andOperator(criteriaList.toArray(new Criteria[0])));
        }

        long total = mongoTemplate.count(query, Game.class);
        List<Game> content = mongoTemplate.find(query.with(pageable), Game.class);

        return new PageImpl<>(content, pageable, total);
    }

    @Override
    public List<String> findDistinctGenres() {
        // Built as raw pipeline stages (instead of the ProjectionOperation/andExpression DSL)
        // because
        // Spring Data Mongo's string-based aggregation expressions aren't parsed correctly in this
        // project setup.
        AggregationOperation splitGenres =
                context ->
                        new Document(
                                "$project",
                                new Document(
                                        "genreArray",
                                        new Document(
                                                "$split",
                                                Arrays.asList(
                                                        new Document(
                                                                "$ifNull",
                                                                Arrays.asList("$genres", "")),
                                                        ","))));

        AggregationOperation unwindGenres = context -> new Document("$unwind", "$genreArray");

        AggregationOperation trimGenre =
                context ->
                        new Document(
                                "$project",
                                new Document(
                                        "genre",
                                        new Document(
                                                "$trim", new Document("input", "$genreArray"))));

        AggregationOperation filterEmpty =
                context -> new Document("$match", new Document("genre", new Document("$ne", "")));

        AggregationOperation groupGenre =
                context -> new Document("$group", new Document("_id", "$genre"));

        AggregationOperation sortGenre = context -> new Document("$sort", new Document("_id", 1));

        Aggregation aggregation =
                newAggregation(
                        splitGenres, unwindGenres, trimGenre, filterEmpty, groupGenre, sortGenre);

        List<Document> results =
                mongoTemplate.aggregate(aggregation, "games", Document.class).getMappedResults();
        List<String> genres = new ArrayList<>();
        for (Document doc : results) {
            Object id = doc.get("_id");
            if (id != null) {
                genres.add(id.toString());
            }
        }
        return genres;
    }
}
