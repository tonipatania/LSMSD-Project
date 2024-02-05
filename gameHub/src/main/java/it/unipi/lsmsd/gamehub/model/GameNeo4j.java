package it.unipi.lsmsd.gamehub.model;

import lombok.*;
import org.neo4j.ogm.annotation.NodeEntity;
import org.springframework.data.neo4j.core.schema.GeneratedValue;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Relationship;
import java.util.List;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@ToString

@NodeEntity("GameNeo4j")
public class GameNeo4j {
    @Id @GeneratedValue
    private String id;
    private String name;
    /*private String developers;
    private String categories;
    private String genres;*/
    //private String numberOfLinks;
    /*@Relationship(type = "ADD", direction = Relationship.Direction.INCOMING)
    private List<UserNeo4j> users;*/
}
