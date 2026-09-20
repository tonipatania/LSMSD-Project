package it.unipi.lsmsd.gamehub.DTO;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

// una persona nella pagina Community, vista da chi la guarda: mutual e' vero se le due persone si
// seguono a vicenda, cosi la card puo' dire "ti segue anche" senza una seconda chiamata
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@ToString
public class ConnectionDTO {
    private String id;
    private String username;
    private Boolean mutual;
}
