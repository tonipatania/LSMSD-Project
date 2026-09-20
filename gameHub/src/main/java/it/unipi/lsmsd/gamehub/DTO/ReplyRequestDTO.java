package it.unipi.lsmsd.gamehub.DTO;

import lombok.*;

// corpo di POST /review/reply: l'autore non c'e' di proposito, lo dice il token
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@ToString
public class ReplyRequestDTO {
    private String reviewId;
    private String comment;
}
