package it.unipi.lsmsd.gamehub.service.impl;


import it.unipi.lsmsd.gamehub.model.GameNeo4j;
import it.unipi.lsmsd.gamehub.repository.GameNeo4jRepository;
import it.unipi.lsmsd.gamehub.repository.UserNeo4jRepository;
import it.unipi.lsmsd.gamehub.service.IGameNeo4jService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class GameNeo4jService implements IGameNeo4jService {
    @Autowired
    GameNeo4jRepository gameNeo4jRepository;

    @Autowired
    UserNeo4jRepository userNeo4jRepository;

    @Override
    public GameNeo4j getGameByTitle(String name) {
        try {
            return gameNeo4jRepository.findGameByName(name);
        } catch (Exception e){
            System.out.println(e.getMessage());
            return null;
        }
    }

    @Override
    public Integer getGamesIngoingLinks(String name) {
        try {
            return gameNeo4jRepository.findGameIngoingLinks(name);
        }catch (Exception e){
            System.out.println(e.getMessage());
            return null;
        }
    }
}
