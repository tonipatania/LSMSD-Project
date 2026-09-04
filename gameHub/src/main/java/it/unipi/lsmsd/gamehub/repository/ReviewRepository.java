package it.unipi.lsmsd.gamehub.repository;

import it.unipi.lsmsd.gamehub.model.Review;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ReviewRepository extends MongoRepository<Review, String> {
    List<Review> findByTitleOrderByLikeCountDesc(String title, Pageable pageable);
}
