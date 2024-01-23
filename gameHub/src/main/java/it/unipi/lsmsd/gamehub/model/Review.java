package it.unipi.lsmsd.gamehub.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.util.ArrayList;
import java.util.List;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@ToString
@Document(collection = "reviews")

public class Review {
    @Id
    private String id;
    @Field("Title")
    private String title;
    @Field("Userscore")
    private int userScore;
    @Field("Comment")
    private String comment;
    @Field("Username")
    private String username;
    @Field("Like")
    private Like like;
    //RICORDARSI IL CAMPO likeCount
}
