---
name: backend-e2e-tests
description: Write JUnit 5 end-to-end tests for the gameHub Spring Boot backend that drive full multi-step user journeys over real HTTP (RestAssured, random port, real Testcontainers Mongo/Neo4j) - signup, login, using the returned JWT to call protected endpoints, following a flow across several endpoints. Use whenever the user asks for end-to-end tests, e2e tests, "full flow"/"user journey" tests, or wants to verify the whole app works together through the real HTTP surface rather than one layer at a time.
---

## Relationship to the other test skills

- **`backend-tests`** (unit, mocked repositories) and **`backend-integration-tests`** (real DB,
  one layer/flow at a time, often via `MockMvc` or direct service/repository calls) both go deep
  on a single piece of logic. This skill goes wide: a realistic sequence of HTTP calls a real
  client would make, asserting on the observable behavior of the whole system. If a task is about
  one endpoint's edge cases, it probably belongs in one of the other two skills instead - don't
  reach for a full HTTP journey just to check a single validation branch.
- Use `backend-integration-tests`' `IntegrationTestSupport` machinery (shared Testcontainers,
  `mongoTemplate`/`neo4jClient` for fixture seeding and DB-state assertions) - this skill's base
  class extends it, so read that skill's "Base class" section too; it isn't repeated here.

## Where tests go and how they're run

Class name **must end in `IT`** (Maven Failsafe convention - see `backend-integration-tests` for
why), and by further convention here, name it after the journey with an `E2E` marker before `IT`
so it's visually distinct from a lower-level integration test in a directory listing, e.g.
`AuthJourneyE2EIT`, `ReviewJourneyE2EIT`, `SocialGraphJourneyE2EIT`. Put these directly under
`src/test/java/it/unipi/lsmsd/gamehub/e2e/` (a flat package for journeys, since a journey usually
spans several controllers/packages and doesn't belong to any single one of them) rather than
mirroring the main package layout the other two skills use.

## Base class: extend `support.E2ETestSupport`

`src/test/java/it/unipi/lsmsd/gamehub/support/E2ETestSupport.java`:

- `@SpringBootTest(webEnvironment = RANDOM_PORT)`, extends `IntegrationTestSupport` (real
  Testcontainers Mongo/Neo4j, per-test cleanup - see that skill).
- Configures `RestAssured.baseURI`/`port` from `@LocalServerPort` in a `@BeforeEach`, so tests just
  call `given()...when()...then()` (static imports from `io.restassured.RestAssured.given`)
  directly, or use the two helpers below.
- `protected RequestSpecification authenticatedAs(String username, String role)` - mints a JWT via
  the real `JwtService` bean and returns a RestAssured spec with the `Authorization: Bearer ...`
  header already set. `role` is the raw claim (`"USER"`/`"ADMIN"`, no `ROLE_` prefix -
  `JwtAuthenticationFilter` adds that itself when building the Spring Security authority).
  Use this for any step in a journey that isn't specifically testing `/login` itself - no need to
  seed a BCrypt password and actually call `/login` just to get authenticated for a later step.
- `protected RequestSpecification anonymous()` - a plain spec, for asserting a protected endpoint
  correctly 401s without a token.

## Writing a journey

A journey test seeds only what a real client couldn't create through the API itself (e.g. an
existing `Game`/`UserNeo4j` a wishlist step needs to already exist), then drives the rest purely
through HTTP calls in sequence, asserting on each response before using its output (e.g. an id or
token) in the next call. Keep assertions on *status code and JSON shape*, matching what
`backend-tests`' controller-layer guidance already documents for each endpoint (see that skill and
`CLAUDE.md`'s "Layering"/"Auth" sections for the exact status codes/DTOs) - re-verifying business
rules already covered by unit tests inside a journey just makes it slower and more brittle without
adding coverage; the journey's job is proving the steps compose correctly end-to-end.

Two structural traps specific to this codebase, worth checking explicitly in a journey rather than
assuming:

- **Graph nodes must exist before graph relationships can be created.** `followUser`,
  `addGameToWishlist`, etc. return a "no-op" success-shaped response (not an error) if either side
  isn't already a `UserNeo4j`/`GameNeo4j` node - there's no cross-store validation that raises an
  error. A journey that does signup → follow-a-friend needs the friend to already be a `UserNeo4j`
  node - registering the friend through `/signup` creates it as a side effect (see
  `LoginController.registration()`), or seed the node directly via `neo4jClient` - or the follow
  step will silently "succeed" while doing nothing - assert the follow relationship actually landed
  (query `neo4jClient`, or call `GET /user/followedUser`) rather than trusting the 200.
- **The `{userId}`-suffixed "admin" endpoints are not gated by `ROLE_ADMIN`.** Endpoints like
  `POST /game/create/{userId}` or `DELETE /review/reviewSelected/delete/{userId}` only require
  *any* authenticated JWT at the Spring Security layer; the actual admin check is an
  application-level lookup of the Mongo `User` document whose `_id` equals the `{userId}` path
  segment, requiring its `role` field to be non-null. The JWT's own subject/role claims are
  irrelevant to this check. To exercise the admin-allowed branch, seed a Mongo `User` with a
  non-null `role` and use *that user's Mongo id* as `{userId}` - not the JWT subject. Only
  `POST /user/loadgames` is a real `hasRole("ADMIN")` check at the Security layer (test it by
  minting a token with `authenticatedAs(username, "ADMIN")` vs `"USER"` and asserting 200 vs 403).
  `/user/sync` used to be the other one but was removed - it let anyone with an ADMIN token trigger
  an unbounded full Mongo→Neo4j user resync on demand, which had no place in a production endpoint.

## Before calling a task done

Same as `backend-integration-tests` (read that skill's "Before calling a test task done" section -
not repeated here): run `./mvnw verify` from `gameHub/` (needs Docker and `mongo_local`/
`neo4j_local` already running for the pre-existing unit suite that runs first; Testcontainers
itself pulls fresh, disposable `mongo:7.0`/`neo4j:5.15` images and never touches those dev
containers), confirm green, and let/watch the Stop hook handle Checkstyle/Spotless on changed
files.
