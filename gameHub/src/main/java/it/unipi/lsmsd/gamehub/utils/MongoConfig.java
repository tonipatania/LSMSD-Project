package it.unipi.lsmsd.gamehub.utils;

import org.springframework.data.mongodb.config.AbstractMongoClientConfiguration;

public class MongoConfig extends AbstractMongoClientConfiguration {
    @Override
    protected String getDatabaseName() {
        return "game";
    }

    @Override
    protected boolean autoIndexCreation() {
        return true;
    }
}
