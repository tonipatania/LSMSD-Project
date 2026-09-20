package it.unipi.lsmsd.gamehub.DTO;

import it.unipi.lsmsd.gamehub.model.Game;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

// gli "scaffali" della pagina Giochi (stile piattaforme di streaming). Game "leggeri": solo
// quello che serve a una card, niente descrizioni ne' recensioni embedded.
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@ToString
public class GameRailsDTO {
    // i piu' in movimento negli ultimi 7 giorni (wishlist + recensioni)
    private List<Game> weekly;
    // i piu' amati dalla community: molto desiderati e con un voto alto
    private List<Game> favorites;
    // le uscite piu' recenti
    private List<Game> latest;
}
