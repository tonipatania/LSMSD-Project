package it.unipi.lsmsd.gamehub.service.impl;

import it.unipi.lsmsd.gamehub.DTO.ReplyRequestDTO;
import it.unipi.lsmsd.gamehub.model.Review;
import it.unipi.lsmsd.gamehub.model.ReviewReply;
import it.unipi.lsmsd.gamehub.repository.ReviewReplyRepository;
import it.unipi.lsmsd.gamehub.repository.ReviewRepository;
import it.unipi.lsmsd.gamehub.service.INotificationService;
import it.unipi.lsmsd.gamehub.service.IReviewReplyService;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class ReviewReplyService implements IReviewReplyService {
    static final int MAX_REPLY_LENGTH = 500;
    // tetto di sicurezza: una recensione con migliaia di risposte non deve gonfiare il payload
    static final int MAX_REPLIES_PER_REVIEW = 100;
    static final int MAX_COUNT_IDS = 100;

    @Autowired private ReviewReplyRepository replyRepository;
    @Autowired private ReviewRepository reviewRepository;
    @Autowired private MongoTemplate mongoTemplate;
    @Autowired private INotificationService notificationService;

    @Override
    public ResponseEntity<Object> createReply(String username, ReplyRequestDTO request) {
        try {
            if (request == null
                    || request.getReviewId() == null
                    || request.getReviewId().isBlank()
                    || request.getComment() == null
                    || request.getComment().isBlank()) {
                return new ResponseEntity<>(
                        "reviewId and comment are required", HttpStatus.BAD_REQUEST);
            }
            String comment = request.getComment().trim();
            if (comment.length() > MAX_REPLY_LENGTH) {
                return new ResponseEntity<>(
                        "reply too long, max " + MAX_REPLY_LENGTH + " characters",
                        HttpStatus.BAD_REQUEST);
            }

            Optional<Review> review = reviewRepository.findById(request.getReviewId());
            if (review.isEmpty()) {
                return new ResponseEntity<>("review not found", HttpStatus.NOT_FOUND);
            }
            // l'autore dice la sua nella recensione: le risposte sono per gli altri
            if (username.equals(review.get().getUsername())) {
                return new ResponseEntity<>(
                        "you cannot reply to your own review", HttpStatus.FORBIDDEN);
            }

            ReviewReply saved =
                    replyRepository.save(
                            new ReviewReply(
                                    null, request.getReviewId(), username, comment, Instant.now()));
            // l'autore della recensione viene avvisato (best effort: non fa fallire la risposta)
            notificationService.notifyReply(username, review.get(), saved);
            return new ResponseEntity<>(saved, HttpStatus.CREATED);
        } catch (Exception e) {
            log.error("Errore in createReply", e);
            return new ResponseEntity<>(
                    "error creating the reply: " + e.getMessage(),
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public List<ReviewReply> getReplies(String reviewId) {
        try {
            return replyRepository.findByReviewIdOrderByCreatedAtAsc(
                    reviewId, PageRequest.of(0, MAX_REPLIES_PER_REVIEW));
        } catch (Exception e) {
            log.error("Errore in getReplies", e);
            return Collections.emptyList();
        }
    }

    @Override
    public Map<String, Long> countReplies(List<String> reviewIds) {
        try {
            if (reviewIds == null || reviewIds.isEmpty()) {
                return Collections.emptyMap();
            }
            List<String> ids = reviewIds.stream().distinct().limit(MAX_COUNT_IDS).toList();
            Aggregation aggregation =
                    Aggregation.newAggregation(
                            Aggregation.match(Criteria.where("reviewId").in(ids)),
                            Aggregation.group("reviewId").count().as("count"));
            Map<String, Long> counts = new HashMap<>();
            for (Document row :
                    mongoTemplate
                            .aggregate(aggregation, "review_replies", Document.class)
                            .getMappedResults()) {
                counts.put(row.getString("_id"), ((Number) row.get("count")).longValue());
            }
            return counts;
        } catch (Exception e) {
            log.error("Errore in countReplies", e);
            return Collections.emptyMap();
        }
    }

    @Override
    public ResponseEntity<String> deleteReply(String replyId, String username) {
        try {
            Optional<ReviewReply> reply = replyRepository.findById(replyId);
            if (reply.isEmpty()) {
                return new ResponseEntity<>("reply not found", HttpStatus.NOT_FOUND);
            }
            if (!username.equals(reply.get().getUsername())) {
                return new ResponseEntity<>(
                        "only the author can delete a reply", HttpStatus.FORBIDDEN);
            }
            replyRepository.deleteById(replyId);
            notificationService.removeReply(replyId);
            return new ResponseEntity<>("reply deleted", HttpStatus.OK);
        } catch (Exception e) {
            log.error("Errore in deleteReply", e);
            return new ResponseEntity<>(
                    "error deleting the reply: " + e.getMessage(),
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}
