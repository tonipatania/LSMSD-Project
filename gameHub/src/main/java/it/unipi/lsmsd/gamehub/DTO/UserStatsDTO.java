package it.unipi.lsmsd.gamehub.DTO;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

// numeri mostrati nella card di un utente seguito nel feed della Home
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@ToString
public class UserStatsDTO {
    private String username;
    private Integer wishlistCount;
    private Integer followers;
}
