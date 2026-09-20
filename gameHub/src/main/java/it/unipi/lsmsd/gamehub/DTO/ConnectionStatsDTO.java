package it.unipi.lsmsd.gamehub.DTO;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

// i tre numeri in cima alla pagina Community
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@ToString
public class ConnectionStatsDTO {
    private long following;
    private long followers;
    private long mutual;
}
