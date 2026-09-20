package it.unipi.lsmsd.gamehub.service;

import it.unipi.lsmsd.gamehub.DTO.ReplyRequestDTO;
import it.unipi.lsmsd.gamehub.model.ReviewReply;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;

public interface IReviewReplyService {
    // 201 con la risposta creata; 400 testo non valido, 403 risposta a una propria recensione,
    // 404 recensione inesistente
    ResponseEntity<Object> createReply(String username, ReplyRequestDTO request);

    // dalla piu' vecchia alla piu' recente, come in una conversazione
    List<ReviewReply> getReplies(String reviewId);

    // numero di risposte per recensione, in una sola query; le recensioni senza risposte mancano
    // dalla mappa
    Map<String, Long> countReplies(List<String> reviewIds);

    // solo l'autore della risposta puo' cancellarla
    ResponseEntity<String> deleteReply(String replyId, String username);
}
