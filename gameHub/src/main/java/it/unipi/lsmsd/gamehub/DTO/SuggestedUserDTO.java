package it.unipi.lsmsd.gamehub.DTO;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@ToString
public class SuggestedUserDTO {
    // motivo per cui l'utente viene suggerito, usato dal frontend per l'etichetta
    public static final String COMMON_FRIENDS = "COMMON_FRIENDS";
    public static final String SIMILAR_TASTES = "SIMILAR_TASTES";
    public static final String POPULAR = "POPULAR";

    private String id;
    private String username;
    private String reason;
    private Integer commonGames;
    private Integer followers;
}
