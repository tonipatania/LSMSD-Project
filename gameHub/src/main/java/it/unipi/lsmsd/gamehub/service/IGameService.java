package it.unipi.lsmsd.gamehub.service;

import it.unipi.lsmsd.gamehub.DTO.GameDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;


public interface IGameService {
    public Page<GameDTO> getAll(Pageable pageable);
    public GameDTO createGame(GameDTO gameDTO);
    public void deleteGame(String id);
}
