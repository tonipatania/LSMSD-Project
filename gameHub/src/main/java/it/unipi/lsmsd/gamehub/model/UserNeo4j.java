package it.unipi.lsmsd.gamehub.model;

import lombok.*;
import org.neo4j.ogm.annotation.NodeEntity;
import org.springframework.data.neo4j.core.schema.Id;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@ToString
@NodeEntity("UserNeo4j")
public class UserNeo4j {
    @Id private String id;
    private String username;
}
