package it.unipi.lsmsd.gamehub.controller;

import it.unipi.lsmsd.gamehub.DTO.GameWishlistDTO;
import it.unipi.lsmsd.gamehub.service.IInteractionGameService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

//tengo da remoto
@RequestMapping("wishlist")
@RestController
public class InteractionGameController {
   /* @Autowired
    private IInteractionGameService iInteractionGameService;

    @PatchMapping("/addGame/{userId}")
    public ResponseEntity<Object> addGame(@PathVariable String userId, @RequestParam( name = "idGame") String idGame, @RequestParam(name = "nameGame") String nameGame) {
        return iInteractionGameService.addGame(userId, idGame, nameGame);
    }
    @GetMapping("/view/{userId}")
    public ResponseEntity<List<GameWishlistDTO>> viewWishlist(@PathVariable String userId) {
        return iInteractionGameService.getWishlist(userId);
    }
    @PatchMapping("/removeGame/{userId}")
    public ResponseEntity<Object> removeGame(@PathVariable String userId, @RequestParam int index) {
        return iInteractionGameService.removeGameWishlist(userId, index);
    }*/
}
