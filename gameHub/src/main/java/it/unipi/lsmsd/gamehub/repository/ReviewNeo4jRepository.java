package it.unipi.lsmsd.gamehub.repository;

import it.unipi.lsmsd.gamehub.model.ReviewNeo4j;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ReviewNeo4jRepository extends Neo4jRepository<ReviewNeo4j,String> {

    @Query("MATCH (r:ReviewNeo4j)<-[:LIKE]-(u:UserNeo4j) WHERE r.id = $id RETURN count(u) as numberOfLinks")
    int findReviewIngoingLinks(@Param("id") String id);
}
