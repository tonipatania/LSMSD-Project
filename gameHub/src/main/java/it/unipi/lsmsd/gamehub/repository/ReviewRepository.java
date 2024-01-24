package it.unipi.lsmsd.gamehub.repository;

import it.unipi.lsmsd.gamehub.model.Review;
import it.unipi.lsmsd.gamehub.repository.MongoDBAggregation.ReviewRepositoryCustom;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.Pageable;

import java.util.List;

@Repository
public interface ReviewRepository extends MongoRepository<Review,String>, ReviewRepositoryCustom {
    List<Review> findByTitle(String title);

    List<Review> findByTitleOrderByLikeCountDesc(String title,Pageable pageable);

}
