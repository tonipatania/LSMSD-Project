# GameHub — Backend

Spring Boot backend for **GameHub**, a social network for video games. Built for the
"Large-Scale and Multi-Structured Databases" course at the University of Pisa as a study in
polyglot persistence: **MongoDB** stores the core entities (users, games, reviews), while
**Neo4j** models the social graph (follows, wishlist, likes, friend/game suggestions).

Frontend companion project: [`gameHub-FE`](https://github.com/tonipatania/gameHub-FE) (Angular).

## Stack

- Java 17, Spring Boot 3.2
- Spring Security + JWT (`io.jsonwebtoken`)
- Spring Data MongoDB (reactive) — document storage
- Spring Data Neo4j / Neo4j OGM — graph storage
- ModelMapper, Lombok

## Prerequisites

- JDK 17+
- MongoDB running on `localhost:27017`
- Neo4j running on `localhost:7687` (Bolt)

Connection settings live in
[`src/main/resources/application.properties`](gameHub/src/main/resources/application.properties):

```properties
spring.data.mongodb.uri=mongodb://localhost:27017/game
spring.neo4j.uri=bolt://localhost:7687
spring.neo4j.authentication.username=neo4j
spring.neo4j.authentication.password=elliejoel
```

The JWT signing secret can be overridden via the `GAMEHUB_JWT_SECRET` environment variable
(a default dev value is baked in).

Account confirmation emails are sent via Gmail SMTP. Set `GAMEHUB_MAIL_USERNAME` (the sending
Gmail address) and `GAMEHUB_MAIL_PASSWORD` (a Google [App Password](https://myaccount.google.com/apppasswords),
not the account password — requires 2-Step Verification enabled). Without them, registration
still succeeds but the confirmation email fails to send (logged, not thrown) and the account
stays unconfirmed until a link can be issued.

## Running

```bash
cd gameHub
./mvnw spring-boot:run
```

The API is served on `http://localhost:8080`. A Postman collection is available under
[`postman/`](postman) for manual testing.

## Database structure

### MongoDB (`game` database)

| Collection | Description |
|---|---|
| `users` | `username` (unique), `name`, `surname`, `password` (BCrypt after first login), `email` (unique), `role` |
| `games` | `name`, `genres`, `releaseDate`, `avgScore` (average `Userscore` of reviews sharing the game's name, 0 if none), `price`, description, store metadata (`URL`, developers, publishers, categories, supported languages), and an embedded `reviews` list (top 20 by `likeCount`, used as a read cache — see `GameService`) |
| `reviews` | `title`, `userScore`, `comment`, `username`, `likeCount` |

Indexes: unique index on `users.username` and `users.email`; index on `games.genres` and
`games.avgScore`, plus a compound index `{genres: 1, avgScore: 1}` used by the catalog
search/filter endpoints.

### Neo4j (graph model)

Nodes:

- `UserNeo4j {id, username}`
- `GameNeo4j {id, name}`
- `ReviewNeo4j {id}`

Relationships:

| Relationship | Meaning |
|---|---|
| `(User)-[:FOLLOW]->(User)` | social follow graph |
| `(User)-[:ADD]->(Game)` | wishlist / owned games — also used to compute game and friend suggestions via 2nd-degree traversal |
| `(User)-[:LIKE]->(Review)` | review likes |

Constraints/indexes (created at startup by
[`Neo4jIndexInitializer`](gameHub/src/main/java/it/unipi/lsmsd/gamehub/utils/Neo4jIndexInitializer.java)):
uniqueness constraint on `UserNeo4j.username`, plus indexes on `UserNeo4j.id`, `GameNeo4j.name`,
`GameNeo4j.id`, `ReviewNeo4j.id`.

Dataset scale: 83,554 games, 279,883 reviews, 131,757 users in MongoDB; 83,554 `GameNeo4j`,
131,757 `UserNeo4j`, 98,557 `ReviewNeo4j` nodes, 147,597 `ADD` and 482,172 `FOLLOW`
relationships in Neo4j — node counts match 1:1 across the two stores, with no orphan or
duplicate nodes.

A couple of properties of the data worth knowing before writing queries against it:

- **Usernames are unique; game names are not.** The catalog legitimately contains distinct
  games (different `_id`, release date, developer) that share a title, so lookups by
  name/title — `findByTitleOrderByLikeCountDesc`, and the review↔game linking in general —
  can match more than one document by design.
- **Reviews link to their game by title, not by id** (there's no `gameId` field on `Review`).
  A minority of reviews have an empty `Title` and are therefore not attributable to any game;
  they're excluded when computing `avgScore` but are otherwise valid rows.

## Importing the dumps

Dumps for both databases are provided so the app can be tried with realistic data volumes.

### MongoDB

BSON dumps live in [`MongoDBDump/`](MongoDBDump) (`games`, `reviews`, `users`). With a MongoDB
container named `mongo_local` and the dump copied into `/tmp/dump` inside the container:

```bash
docker exec -it mongo_local mkdir -p /tmp/dump/game
docker exec -it mongo_local sh -c "mv /tmp/dump/*.bson /tmp/dump/*.json /tmp/dump/game/"
docker exec -it mongo_local mongorestore --nsInclude="game.*" /tmp/dump/
```

Adjust the container name/copy step if you're restoring from a different setup — the key
requirement is that `mongorestore` sees a `game/` folder containing the `.bson`/`.metadata.json`
pairs.

### Neo4j

The dump is [`neo4j.dump`](neo4j.dump). `neo4j-admin database load` cannot run against a live
database, so the target Neo4j container must be stopped and the dump loaded via a disposable
container mounting the **same** data volume:

```bash
# 1. Stop the running Neo4j container
docker stop neo4j_local

# 2. Load the dump into the same data volume with a throwaway container
docker run --rm \
  --volume=<path-to-neo4j-data-volume>:/data \
  --volume=<path-to-this-repo>/LSMSD-Project:/dumps \
  neo4j:latest \
  neo4j-admin database load neo4j --from-path=/dumps --overwrite-destination=true

# 3. Restart the original container
docker start neo4j_local

# 4. Verify the import
docker exec neo4j_local cypher-shell -u neo4j -p elliejoel \
  "MATCH (n) RETURN labels(n) AS label, count(*) AS count;"
```

`--from-path` must point to the **folder** containing the dump, not the file itself, and the
target database name (`neo4j`) must match the dump's file name (`neo4j.dump`).
`--overwrite-destination=true` replaces whatever is currently in that data volume.

## API overview

| Resource | Endpoints |
|---|---|
| Auth (`/login`) | login, signup |
| Games (`/game`) | search/filter, genres list, paginated listing, create/delete, aggregations, game suggestions, ingoing-link count |
| Reviews (`/review`) | search by game title, create/delete, aggregations |
| Users (`/user`) | wishlist (list, paginated, common), follow/unfollow, followed-users list, friend suggestions, user search, review likes |

Full request/response shapes are in the Postman collection under [`postman/`](postman).
