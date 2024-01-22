package it.unipi.lsmsd.gamehub.model;

import lombok.*;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@ToString
@Node
public class GameNeo4j {
    @Id
    private String id;
    private String name;
    private String developers;
    private String categories;
    private String genres;
}
