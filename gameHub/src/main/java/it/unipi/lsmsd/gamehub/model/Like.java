package it.unipi.lsmsd.gamehub.model;

import lombok.*;
import org.springframework.data.annotation.PersistenceConstructor;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.util.ArrayList;
import java.util.List;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@ToString
@Document(collection = "games")
public class Like {
    @Field("count")
    private int count;
    @Field("user_like")
    private List<String> userLike;
}
