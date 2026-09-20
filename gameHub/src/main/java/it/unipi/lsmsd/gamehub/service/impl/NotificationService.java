package it.unipi.lsmsd.gamehub.service.impl;

import it.unipi.lsmsd.gamehub.DTO.NotificationDTO;
import it.unipi.lsmsd.gamehub.model.Notification;
import it.unipi.lsmsd.gamehub.model.NotificationType;
import it.unipi.lsmsd.gamehub.model.Review;
import it.unipi.lsmsd.gamehub.model.ReviewReply;
import it.unipi.lsmsd.gamehub.repository.NotificationRepository;
import it.unipi.lsmsd.gamehub.repository.UserNeo4jRepository;
import it.unipi.lsmsd.gamehub.service.INotificationService;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class NotificationService implements INotificationService {
    static final int EXCERPT_LENGTH = 140;

    @Autowired private NotificationRepository notificationRepository;
    @Autowired private UserNeo4jRepository userNeo4jRepository;
    @Autowired private MongoTemplate mongoTemplate;

    @Override
    public void notifyFollow(String actor, String recipient) {
        save("notifyFollow", NotificationType.FOLLOW, actor, recipient, null, null, null, null);
    }

    @Override
    public void notifyLike(String actor, Review review) {
        save(
                "notifyLike",
                NotificationType.LIKE_REVIEW,
                actor,
                review.getUsername(),
                review.getTitle(),
                review.getId(),
                null,
                review.getComment());
    }

    @Override
    public void notifyReply(String actor, Review review, ReviewReply reply) {
        save(
                "notifyReply",
                NotificationType.REPLY_REVIEW,
                actor,
                review.getUsername(),
                review.getTitle(),
                review.getId(),
                reply.getId(),
                reply.getComment());
    }

    private void save(
            String operation,
            NotificationType type,
            String actor,
            String recipient,
            String gameName,
            String reviewId,
            String replyId,
            String text) {
        try {
            // nessuno riceve notifiche per cio' che fa lui stesso
            if (recipient == null || recipient.equals(actor)) {
                return;
            }
            notificationRepository.save(
                    new Notification(
                            null,
                            recipient,
                            type,
                            actor,
                            gameName,
                            reviewId,
                            replyId,
                            excerpt(text),
                            false,
                            Instant.now()));
        } catch (Exception e) {
            log.error("Errore in {}", operation, e);
        }
    }

    private static String excerpt(String text) {
        if (text == null) {
            return null;
        }
        String trimmed = text.trim();
        return trimmed.length() <= EXCERPT_LENGTH
                ? trimmed
                : trimmed.substring(0, EXCERPT_LENGTH).trim() + "…";
    }

    @Override
    public void removeFollow(String actor, String recipient) {
        try {
            notificationRepository.deleteByRecipientAndTypeAndActor(
                    recipient, NotificationType.FOLLOW, actor);
        } catch (Exception e) {
            log.error("Errore in removeFollow", e);
        }
    }

    @Override
    public void removeLike(String actor, String reviewId) {
        try {
            notificationRepository.deleteByTypeAndActorAndReviewId(
                    NotificationType.LIKE_REVIEW, actor, reviewId);
        } catch (Exception e) {
            log.error("Errore in removeLike", e);
        }
    }

    @Override
    public void removeReply(String replyId) {
        try {
            notificationRepository.deleteByReplyId(replyId);
        } catch (Exception e) {
            log.error("Errore in removeReply", e);
        }
    }

    @Override
    public void removeForReview(String reviewId) {
        try {
            notificationRepository.deleteByReviewId(reviewId);
        } catch (Exception e) {
            log.error("Errore in removeForReview", e);
        }
    }

    @Override
    public Page<NotificationDTO> getNotifications(String recipient, Pageable pageable) {
        try {
            Page<Notification> page =
                    notificationRepository.findByRecipientOrderByCreatedAtDesc(recipient, pageable);

            // una sola query per sapere quali follower il destinatario segue gia'
            List<String> followers =
                    page.getContent().stream()
                            .filter(n -> n.getType() == NotificationType.FOLLOW)
                            .map(Notification::getActor)
                            .distinct()
                            .toList();
            Set<String> alreadyFollowed =
                    followers.isEmpty()
                            ? Set.of()
                            : new HashSet<>(
                                    userNeo4jRepository.findFollowedAmong(recipient, followers));

            return page.map(n -> toDTO(n, alreadyFollowed));
        } catch (Exception e) {
            log.error("Errore in getNotifications", e);
            return Page.empty(pageable);
        }
    }

    private NotificationDTO toDTO(Notification n, Set<String> alreadyFollowed) {
        NotificationDTO dto = new NotificationDTO();
        dto.setId(n.getId());
        dto.setType(n.getType());
        dto.setActor(n.getActor());
        dto.setGameName(n.getGameName());
        dto.setReviewId(n.getReviewId());
        dto.setExcerpt(n.getExcerpt());
        dto.setRead(n.isRead());
        dto.setCreatedAt(n.getCreatedAt());
        if (n.getType() == NotificationType.FOLLOW) {
            dto.setFollowingBack(alreadyFollowed.contains(n.getActor()));
        }
        return dto;
    }

    @Override
    public long countUnread(String recipient) {
        try {
            return notificationRepository.countByRecipientAndReadFalse(recipient);
        } catch (Exception e) {
            log.error("Errore in countUnread", e);
            return 0;
        }
    }

    @Override
    public boolean markRead(String recipient, String id) {
        try {
            // il filtro sul destinatario impedisce di toccare le notifiche di un altro utente
            return mongoTemplate
                            .updateFirst(
                                    Query.query(
                                            Criteria.where("_id")
                                                    .is(id)
                                                    .and("recipient")
                                                    .is(recipient)),
                                    new Update().set("read", true),
                                    Notification.class)
                            .getMatchedCount()
                    > 0;
        } catch (Exception e) {
            log.error("Errore in markRead", e);
            return false;
        }
    }

    @Override
    public void markAllRead(String recipient) {
        try {
            mongoTemplate.updateMulti(
                    Query.query(Criteria.where("recipient").is(recipient).and("read").is(false)),
                    new Update().set("read", true),
                    Notification.class);
        } catch (Exception e) {
            log.error("Errore in markAllRead", e);
        }
    }

    @Override
    public boolean delete(String recipient, String id) {
        try {
            return notificationRepository.deleteByIdAndRecipient(id, recipient) > 0;
        } catch (Exception e) {
            log.error("Errore in delete", e);
            return false;
        }
    }
}
