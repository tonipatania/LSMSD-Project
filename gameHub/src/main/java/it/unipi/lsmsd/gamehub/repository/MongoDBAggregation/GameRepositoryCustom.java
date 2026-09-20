package it.unipi.lsmsd.gamehub.repository.MongoDBAggregation;

import it.unipi.lsmsd.gamehub.model.Game;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface GameRepositoryCustom {
    Page<Game> searchGames(String name, List<String> genres, Integer avgScore, Pageable pageable);

    List<String> findDistinctGenres();

    // id dei giochi con copertina usciti piu' di recente (mai quelli con data futura), dal piu'
    // nuovo. releaseDate e' una stringa, quindi la data si ricava dentro la pipeline.
    List<String> findLatestReleasedGameIds(int limit);
}
