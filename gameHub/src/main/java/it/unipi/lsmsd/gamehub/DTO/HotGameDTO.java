package it.unipi.lsmsd.gamehub.DTO;

import lombok.*;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@ToString
public class HotGameDTO {
    private GameSnippetDTO game;
    // aggiunte alla wishlist negli ultimi giorni
    private int recentWishlistAdds;
}
