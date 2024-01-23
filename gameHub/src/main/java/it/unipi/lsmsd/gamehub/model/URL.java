package it.unipi.lsmsd.gamehub.model;

import lombok.*;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@ToString
@Document(collection = "games")
public class URL {
    @Field("Website")
    private String website;
    @Field("Header image")
    private String headerImage;
    @Field("Support url")
    private String supportUrl;
    @Field("Support email")
    private String supportEmaill;
    @Field("Screenshots")
    private String screenshots;

}
