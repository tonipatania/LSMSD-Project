# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project overview

gameHub is a Spring Boot 3.2 / Java 17 REST backend (University of Pisa LSMSD course project) backing an Angular frontend (runs at `http://localhost:4200`, whitelisted in CORS). It uses **polyglot persistence**: MongoDB is the source of truth for all entity data, while Neo4j holds a lightweight graph projection used for relationship-heavy queries (follows, wishlists, likes, friend suggestions).

## Build and run

```bash
./mvnw clean install
```

```bash
./mvnw spring-boot:run
```

```bash
./mvnw test
```

```bash
./mvnw test -Dtest=GameHubApplicationTests#contextLoads
```

The app needs a running MongoDB (`mongodb://localhost:27017/game`) and Neo4j (`bolt://localhost:7687`) instance — see `src/main/resources/application.properties` for connection settings (Neo4j credentials are currently hardcoded there for local dev). `gamehub.jwt.secret` should be overridden via the `GAMEHUB_JWT_SECRET` env var outside local dev — with the `prod` Spring profile active (`SPRING_PROFILES_ACTIVE=prod`, set on the hosting provider), `JwtService` refuses to start if it's still the default value (see `.env.example`).

There is no linter/formatter configured in this project.

## Testing

Three layers, three skills (`.claude/skills/backend-tests`, `backend-integration-tests`,
`backend-e2e-tests` — read the relevant one before writing tests):

- **Unit tests** (`*Test.java`, mocked repositories) — `./mvnw test`. Almost all of them need no
  DB, but `GameHubApplicationTests.contextLoads()` loads the full app context against the real
  `mongo_local`/`neo4j_local` connection settings in `application.properties`, so those two
  containers must already be running (`docker start mongo_local neo4j_local`) even for a plain
  `./mvnw test`.
- **Integration tests** (`*IT.java`, real Mongo+Neo4j via Testcontainers, one flow/layer at a
  time) and **e2e tests** (`*E2EIT.java`, full HTTP journeys via RestAssured against a
  fully-started app) both run under `./mvnw verify` (bound to `maven-failsafe-plugin`, not
  `maven-surefire-plugin`) — needs a working Docker daemon, and `mongo_local`/`neo4j_local` running
  too, since `verify` runs the full `test` phase first. Testcontainers itself starts fresh,
  disposable `mongo:7.0`/`neo4j:5.15` containers (see `support/IntegrationTestSupport.java`), never
  touching `mongo_local`/`neo4j_local` or their dataset. `pom.xml`'s `maven-failsafe-plugin` sets
  `api.version=1.40` for the forked test JVM — without it, Testcontainers' hardcoded default Docker
  API version (1.32) gets rejected by newer Docker Engine builds ("client version 1.32 is too old.
  Minimum supported API version is 1.40"); this is a testcontainers-java quirk unrelated to this
  project's code, confirmed still present as of testcontainers 1.21.3.
- The `backend-test-runner` subagent (`.claude/agents/`) runs and summarizes the Docker-backed
  suite without dumping raw Maven output into the main conversation — use it for `./mvnw verify`
  runs during normal work.

## Architecture: dual-database write-through pattern

Every core entity (`User`, `Game`, `Review`) has two representations:

- **MongoDB documents** (`model/User.java`, `Game.java`, `Review.java`) — full data, the source of truth. Repositories: `LoginRepository`, `GameRepository`, `ReviewRepository` (Spring Data MongoDB), plus custom aggregation repos under `repository/MongoDBAggregation/` (`GameRepositoryImpl`, `ReviewRepositoryImpl`) for MongoDB aggregation-pipeline queries (grouping, faceted search, distinct genres, etc.) that don't fit the Spring Data query-method model.
- **Neo4j nodes** (`model/UserNeo4j.java`, `GameNeo4j.java`, `ReviewNeo4j.java`) — deliberately minimal (usually just `id` + one display field like `username`/`name`). These exist only to hold graph edges (`FOLLOWS`, wishlist/like relationships) that Neo4j can traverse efficiently but Mongo can't. Repositories: `UserNeo4jRepository`, `GameNeo4jRepository`, `ReviewNeo4jRepository`.

Because the two stores aren't transactional together, write operations that touch both follow a **write-then-rollback** pattern instead of 2PC:

1. Write to Mongo first.
2. Write the corresponding node/edge to Neo4j.
3. If the Neo4j write fails, undo the Mongo write (or vice versa) and return an error — never leave the two stores silently inconsistent.

See `LoginController.registration()` (rolls back the Mongo user if the Neo4j node creation fails) and `UserController.updateUser()` (rolls back the Mongo username if the Neo4j rename fails) for the canonical examples. When adding a new endpoint that mutates an entity present in both stores, follow this same pattern.

Bulk (re)population of the Neo4j graph from Mongo is done via an admin-only endpoint, not on every write:
- `POST /user/loadgames` — copies all Mongo games into Neo4j (`UserNeo4jService.loadGames`), requires `ROLE_ADMIN` (enforced in `SecurityConfig`), uses `ModelMapper` for the Mongo→Neo4j field copy.

`UserNeo4jService.SyncUser` (the Mongo→Neo4j user-copy counterpart) is no longer exposed over HTTP — `POST /user/sync` let anyone with an ADMIN token trigger an unbounded full-graph resync on demand, an unacceptable production attack surface, so the endpoint was removed from `UserController`. The method itself still exists and is covered by `UserNeo4jServiceIT`; wire it up again only behind something safer than a plain public endpoint (an internal-only route, a scheduled job, a CLI/admin tool) if bulk user resync is needed again.

`Neo4jIndexInitializer` (an `ApplicationRunner`) creates Neo4j indexes/constraints at startup — a uniqueness constraint on `UserNeo4j.username` (falling back to a plain index if the constraint can't be created, e.g. due to existing duplicates) plus lookup indexes on `id`/`name` for all three Neo4j node types.

## Account deletion

`DELETE /user/account` (body `{password}`, `AccountService`) erases a user and everything tied to
them. **There is no rollback at all** — not even the write-then-rollback pattern described above:
Mongo and Neo4j are both mutated as the method proceeds, and if any step throws, everything already
done stays done. The only thing this guarantees is that the Mongo user document is deleted **last**,
so a failure midway leaves a loginable account the user can retry, rather than an orphaned Neo4j
node with no Mongo user.

Order: likes given (`likeCount` decremented on others' reviews) -> own reviews + their
replies/notifications/activity/`ReviewNeo4j` nodes -> replies, notifications, activity,
`feed_states` naming the user -> embedded `Game.reviews` refreshed for touched games -> Neo4j
`DETACH DELETE` of the user node (follows, wishlist) -> Mongo user document.

Retrying after a failure is safe **only because each step is written to also be idempotent on its
own**, not because of any compensating rollback — a step that just re-reads "what's left to do"
from a store that a later step hasn't touched yet would double-apply on retry. The likes step is
the instructive example: it used to read liked-review ids from Neo4j (`MATCH ... RETURN r.id`) and
decrement `likeCount` in Mongo, while the actual `LIKE` edges were only removed later by the final
`DETACH DELETE`. If that final step (or anything after the likes step) failed and the user retried,
the same edges were still there, so the same reviews got `likeCount` decremented a second time —
silently wrong, drifting further with every retry. Fixed by making
`UserNeo4jRepository.consumeAllLikedReviewIds` find-and-delete the edges in one Cypher query, so a
retry finds nothing left to re-decrement. Apply the same find-and-delete-together shape to any
future step here, rather than a separate read then a separate delete later.

Two narrower, accepted trade-offs remain (both judged low-severity enough to leave as is rather
than add more machinery — revisit only if they turn out to matter in practice):

- **A failure between `consumeAllLikedReviewIds` and the Mongo `likeCount` decrement** (i.e. the
  Neo4j edge is gone but the decrement never ran) permanently undercounts that review's
  `likeCount` by one — the edge was the only record of "this user liked this review" and it's now
  gone, so a retry has nothing left to re-derive the missed decrement from. This is the deliberate
  flip side of the fix above: better a one-time, bounded miss than an unbounded double-count on
  every retry, but it isn't perfect. Fixing it for real would mean tracking who-liked-what on the
  Mongo side too (there's no such record today, only the aggregate `likeCount` field), which is a
  bigger data-model change, not a tweak to this method.
- **`refreshEmbeddedReviews` only recomputes `Game.reviews` for games this *specific* call actually
  touched** (`affectedGames`, built fresh from what steps 1–2 just deleted/decremented). If an
  earlier failed attempt already deleted a review or decremented a like but died before reaching
  this step, a retry's steps 1–2 find nothing left to do (already gone) and so never re-add that
  game to `affectedGames` — the embedded preview list on that one `Game` document can stay stale
  (e.g. still showing a review that's already gone from the `reviews` collection) until something
  unrelated recomputes it. The `reviews` collection itself is never wrong, only this cached
  embedded copy. A real fix needs some persisted "this game still needs a refresh" marker that
  survives across attempts, since by the time of a retry the evidence of which games were touched
  is itself already gone.

Wrong password is 403 (never 401: the frontend logs out on any 401), admins get 409, attempts are
rate-limited like `/login`. Covered by `AccountServiceTest` and `AccountServiceIT`. Any new
collection that stores a username must be added to `AccountService.removeUserContent`.

## Layering

`controller` → `service` (interface `I*Service` + `impl/*Service`) → `repository`. Controllers are thin: they call one or two service methods and translate the result/exception into an HTTP status. Business logic — including the cross-database write/rollback orchestration — lives in the service layer, not the controllers.

DTOs (`DTO/`) are used both for request bodies (`LoginDTO`, `RegistrationDTO`) and for shaping aggregation/query results (`GameDTOAggregation`, `GameDTOAggregation2`, `ReviewDTOAggregation`, `ReviewDTOAggregation2`, `SuggestedUserDTO`) — the `*Aggregation`/`*Aggregation2` DTOs map directly onto MongoDB aggregation pipeline output shapes in `repository/MongoDBAggregation/`.

## Auth

Stateless JWT auth via `JwtAuthenticationFilter` (reads `Authorization: Bearer <token>`, populates `SecurityContextHolder`) + `JwtService` (issue/parse). `SecurityConfig` disables CSRF and sessions, permits `/login`, `/signup`, `/confirm-email`, `/forgot-password`, `/reset-password`, `/actuator/health`, and CORS preflight (`OPTIONS`) without auth, requires `ROLE_ADMIN` for `/user/loadgames`, and requires authentication for everything else. Passwords are hashed with `BCryptPasswordEncoder`.

## Data model notes

- `Game.genres` and `Game.categories` are stored as a single **comma-separated string**, not an array — filtering by genre requires a regex matching a whole comma-delimited token (see `GameRepositoryImpl.searchGames`), not a plain substring match.
- `Game.releaseDate` is a **string** in the dataset's original format (`"MMM d, yyyy"`, e.g. `"Oct 21, 2008"`), not a date type — sorting/filtering by date can't be delegated to MongoDB and is done in-memory where needed (see `UserNeo4jService.RELEASE_DATE_FORMAT`).
- Wishlist membership and "who follows whom" live only in Neo4j as graph edges; the actual `Game`/`User` data returned to the client is fetched from Mongo and joined in the service layer (see `UserNeo4jService.enrichFromMongo`-style helpers).
- API responses always return valid JSON (e.g. `ResponseEntity.ok().build()` / `[]`, never a bare string like `"empty"`) because Angular's `HttpClient` fails to parse non-JSON 200 bodies even with a JSON content type.
