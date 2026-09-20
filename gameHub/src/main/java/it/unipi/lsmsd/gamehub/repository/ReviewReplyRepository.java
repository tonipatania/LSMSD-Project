package it.unipi.lsmsd.gamehub.repository;

import it.unipi.lsmsd.gamehub.model.ReviewReply;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ReviewReplyRepository extends MongoRepository<ReviewReply, String> {
    List<ReviewReply> findByReviewIdOrderByCreatedAtAsc(String reviewId, Pageable pageable);

    void deleteByReviewId(String reviewId);
}
