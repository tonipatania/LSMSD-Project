package it.unipi.lsmsd.gamehub.repository;

import it.unipi.lsmsd.gamehub.model.Notification;
import it.unipi.lsmsd.gamehub.model.NotificationType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface NotificationRepository extends MongoRepository<Notification, String> {
    Page<Notification> findByRecipientOrderByCreatedAtDesc(String recipient, Pageable pageable);

    long countByRecipientAndReadFalse(String recipient);

    // like/follow sono reversibili: annullandoli sparisce anche la notifica, altrimenti
    // like -> unlike -> like riempirebbe la lista del destinatario di duplicati
    void deleteByRecipientAndTypeAndActor(String recipient, NotificationType type, String actor);

    void deleteByTypeAndActorAndReviewId(NotificationType type, String actor, String reviewId);

    void deleteByReplyId(String replyId);

    void deleteByReviewId(String reviewId);

    // solo il destinatario puo' eliminare la propria notifica: torna quante ne ha tolte (0 o 1)
    long deleteByIdAndRecipient(String id, String recipient);
}
