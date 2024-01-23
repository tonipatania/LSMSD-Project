package it.unipi.lsmsd.gamehub.service;


import it.unipi.lsmsd.gamehub.model.GameNeo4j;
import it.unipi.lsmsd.gamehub.model.Like;

import java.util.List;

public interface IGameNeo4jService {

    GameNeo4j getGameByTitle(String id);

    int getGamesIngoingLinks(String name);

}
