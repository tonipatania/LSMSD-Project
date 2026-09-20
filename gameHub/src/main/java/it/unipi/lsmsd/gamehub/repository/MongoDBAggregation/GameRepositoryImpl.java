package it.unipi.lsmsd.gamehub.repository.MongoDBAggregation;

import static org.springframework.data.mongodb.core.aggregation.Aggregation.*;

import it.unipi.lsmsd.gamehub.model.Game;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.regex.Pattern;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationOperation;
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

    @Override
    public List<String> findLatestReleasedGameIds(int limit) {
        // Pipeline grezza, come findDistinctGenres. Le date del dump sono "Oct 21, 2008", e una
        // parte ha solo mese e anno ("May 2020"): si prova il primo formato, poi il secondo, e le
        // date illeggibili restano null e vengono scartate.
        AggregationOperation onlyWithCover =
                context ->
                        new Document(
                                "$match",
                                new Document(
                                        "URL.Header image",
                                        new Document("$exists", true)
                                                .append("$nin", Arrays.asList(null, ""))));

        Document monthYear =
                new Document(
                        "$dateFromString",
                        new Document("dateString", "$releaseDate")
                                .append("format", "%b %Y")
                                .append("onError", null)
                                .append("onNull", null));
        Document dayMonthYear =
                new Document(
                        "$dateFromString",
                        new Document("dateString", "$releaseDate")
                                .append("format", "%b %d, %Y")
                                .append("onError", monthYear)
                                .append("onNull", null));
        AggregationOperation parseDate =
                context -> new Document("$addFields", new Document("parsedRelease", dayMonthYear));

        AggregationOperation alreadyReleased =
                context ->
                        new Document(
                                "$match",
                                new Document(
                                        "parsedRelease",
                                        new Document("$ne", null).append("$lte", new Date())));

        // a parita' di giorno (il dump ha centinaia di titoli con la stessa data) vince il voto
        // piu'
        // alto, poi il nome, cosi l'ordine e' stabile tra una chiamata e l'altra
        AggregationOperation newestFirst =
                context ->
                        new Document(
                                "$sort",
                                new Document("parsedRelease", -1)
                                        .append("avgScore", -1)
                                        .append("name", 1));

        AggregationOperation top = context -> new Document("$limit", limit);

        Aggregation aggregation =
                newAggregation(onlyWithCover, parseDate, alreadyReleased, newestFirst, top);
        List<String> ids = new ArrayList<>();
        for (Document doc :
                mongoTemplate.aggregate(aggregation, "games", Document.class).getMappedResults()) {
            Object id = doc.get("_id");
            if (id != null) {
                ids.add(id.toString());
            }
        }
        return ids;
    }
}
