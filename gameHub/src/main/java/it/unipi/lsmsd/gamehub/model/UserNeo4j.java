package it.unipi.lsmsd.gamehub.model;

import lombok.*;
import org.neo4j.ogm.annotation.NodeEntity;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Relationship;
import java.util.List;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@ToString

@NodeEntity("UserNeo4j")

public class UserNeo4j {
    @Id
    private String id;
    private String username;
}
