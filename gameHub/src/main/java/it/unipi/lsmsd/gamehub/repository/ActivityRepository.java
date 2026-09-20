package it.unipi.lsmsd.gamehub.repository;

import it.unipi.lsmsd.gamehub.model.Activity;
import it.unipi.lsmsd.gamehub.model.ActivityType;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ActivityRepository extends MongoRepository<Activity, String> {
    // "since" limita il feed a una finestra recente: oltre non e' piu' una novita' e tenerlo
    // paginabile a ritroso all'infinito non ha valore
    Page<Activity> findByUsernameInAndCreatedAtAfterOrderByCreatedAtDesc(
            List<String> usernames, Instant since, Pageable pageable);

    // like/follow sono reversibili: annullandoli si toglie anche l'attivita', altrimenti
    // like -> unlike -> like riempirebbe il feed di duplicati
    void deleteByUsernameAndTypeAndReviewId(String username, ActivityType type, String reviewId);

    void deleteByUsernameAndTypeAndTargetUsername(
            String username, ActivityType type, String targetUsername);
}
