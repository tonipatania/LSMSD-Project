package it.unipi.lsmsd.gamehub.repository;

import it.unipi.lsmsd.gamehub.model.UserNeo4j;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserNeo4jRepository extends Neo4jRepository<UserNeo4j, String> {

}
