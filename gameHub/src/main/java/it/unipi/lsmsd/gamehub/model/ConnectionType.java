package it.unipi.lsmsd.gamehub.model;

// quale elenco della pagina Community si sta chiedendo
public enum ConnectionType {
    FOLLOWING,
    FOLLOWERS,
    MUTUAL;

    // null se il valore non e' uno dei tre: il controller lo traduce in 400
    public static ConnectionType parse(String value) {
        if (value == null) {
            return null;
        }
        for (ConnectionType type : values()) {
            if (type.name().equalsIgnoreCase(value.trim())) {
                return type;
            }
        }
        return null;
    }
}
