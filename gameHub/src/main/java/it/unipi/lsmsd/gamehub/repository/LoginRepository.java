package it.unipi.lsmsd.gamehub.repository;

import it.unipi.lsmsd.gamehub.model.User;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LoginRepository extends MongoRepository<User, String> {
    User findByUsername(String username);

    User save(User user);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);
}
