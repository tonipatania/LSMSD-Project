package it.unipi.lsmsd.gamehub.service;

import it.unipi.lsmsd.gamehub.DTO.NotificationDTO;
import it.unipi.lsmsd.gamehub.model.Review;
import it.unipi.lsmsd.gamehub.model.ReviewReply;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface INotificationService {
    // I notify* non lanciano mai: una notifica e' un accessorio, il suo fallimento non deve far
    // fallire l'azione (follow, like, risposta) gia' riuscita altrove.
    void notifyFollow(String actor, String recipient);

    // recipient e' l'autore della recensione; non notifica se coincide con actor
    void notifyLike(String actor, Review review);

    void notifyReply(String actor, Review review, ReviewReply reply);

    void removeFollow(String actor, String recipient);

    void removeLike(String actor, String reviewId);

    void removeReply(String replyId);

    void removeForReview(String reviewId);

    Page<NotificationDTO> getNotifications(String recipient, Pageable pageable);

    long countUnread(String recipient);

    // false se la notifica non esiste o non e' del destinatario
    boolean markRead(String recipient, String id);

    void markAllRead(String recipient);

    // false se la notifica non esiste o non e' del destinatario
    boolean delete(String recipient, String id);
}
