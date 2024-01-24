package it.unipi.lsmsd.gamehub.model;

import lombok.*;
import org.springframework.data.neo4j.core.schema.Id;
import org.neo4j.ogm.annotation.NodeEntity;
import org.springframework.data.neo4j.core.schema.Node;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@ToString

@Node
public class ReviewNeo4j {
    @Id
    private String id;
}
