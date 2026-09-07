package it.unipi.lsmsd.gamehub.repository;

import it.unipi.lsmsd.gamehub.model.Activity;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ActivityRepository extends MongoRepository<Activity, String> {
    Page<Activity> findByUsernameInOrderByCreatedAtDesc(List<String> usernames, Pageable pageable);
}
