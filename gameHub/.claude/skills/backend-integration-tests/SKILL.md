---
name: backend-integration-tests
description: Write JUnit 5 integration tests for the gameHub Spring Boot backend that run against real Mongo/Neo4j (via Testcontainers), not mocks - for the dual-write Mongo+Neo4j rollback paths, MongoDB aggregation pipelines, and Neo4j Cypher queries that a mocked-repository unit test can't meaningfully exercise. Use this whenever the user asks for integration tests, tests "with a real database", tests that verify data actually lands in Mongo/Neo4j correctly, or wants a rollback/consistency bug reproduced against real stores instead of mocks.
---

## Relationship to the other test skills

- **`backend-tests`** (unit tests, mocked repositories) is still the default for testing business
  logic in isolation - use it first. Reach for this skill when a bug or requirement is about what
  actually happens in the databases (a real unique-constraint violation, a real aggregation
  pipeline result, a real rollback under a real failure) rather than about a service method's
  control flow.
- **`backend-e2e-tests`** covers full HTTP journeys through the real app (RestAssured, random
  port). This skill is narrower and lower-level: one flow, inspected directly against the
  database, usually through `MockMvc` or by calling a service/repository method directly - not a
  multi-step user journey over HTTP. If a task is "does registration actually roll back the Mongo
  user when Neo4j fails", write it here. If it's "can a new user sign up, log in, and see their
  own wishlist", that belongs in `backend-e2e-tests`.

## Where tests go and how they're run

Class name **must end in `IT`** (e.g. `LoginControllerIT`, `GameRepositoryImplIT`), mirroring the
main package layout under `src/test/java/it/unipi/lsmsd/gamehub/...` like the unit-test skill
does. This is not just a style choice: `maven-failsafe-plugin` is bound to the `verify` phase and
picks up `**/*IT.java` by default, while `maven-surefire-plugin` (bound to `test`) does **not**
match that pattern. This is what keeps `./mvnw test` fast (mocked unit tests only, no Docker) while
`./mvnw verify` additionally runs these against real, ephemeral Mongo/Neo4j containers. Naming a
class `FooTest.java` and putting real-database logic in it will make it run under `mvnw test`
every time and defeats the whole point - don't do that.

Method naming follows the same `methodName_condition_expectedResult()` convention as the unit-test
skill.

## Base class: extend `support.IntegrationTestSupport`

`src/test/java/it/unipi/lsmsd/gamehub/support/IntegrationTestSupport.java` already:

- Starts one `MongoDBContainer` and one `Neo4jContainer` **once per JVM** (static initializer, not
  `@Container`/`@Testcontainers`) and wires them into the Spring context via
  `@DynamicPropertySource`. Every `IT` class in the same `mvnw verify` run shares the same
  containers - don't add your own `@Container` fields, and don't call `.stop()` on them.
- Exposes `protected MongoTemplate mongoTemplate;` and `protected Neo4jClient neo4jClient;` for
  seeding fixtures and asserting on raw DB state.
- Wipes the `users`/`games`/`reviews` Mongo collections and all Neo4j nodes in an `@AfterEach` -
  every test method starts with empty stores it seeded itself. **Do not rely on data from another
  test method or class still being there.** Because containers are shared across the whole `mvnw
  verify` run, also don't rely on being the *only* data ever created - use fixture values specific
  to the test (e.g. include the test/method name in a username) if a collision would be surprising,
  though the `@AfterEach` wipe makes actual collisions rare in practice.

For a controller-level integration test (needed whenever the rollback logic lives in the
controller, not the service - see below), autowire `MockMvc` the normal Spring Boot way; it's
available because the base class uses the default `@SpringBootTest` (mock web environment), which
auto-configures `MockMvc` once you add `@AutoConfigureMockMvc` to your test class. Unlike the
`backend-tests` unit-test skill, do **not** disable the security filter chain here - part of the
point of an integration test is exercising real JWT auth against a real request; mint a token via
the real `JwtService` bean (autowire it) and send it as `Authorization: Bearer <token>`.

## Where the rollback logic actually lives (read the code, not just CLAUDE.md's summary)

The write-then-rollback orchestration is in the **controller**, not the service, for both
examples CLAUDE.md names:

- `LoginController.registration()` - `POST /signup`. Calls `loginService.registrate()` (Mongo),
  then `userNeo4jService.addUser()` (Neo4j); rolls back via `loginService.removeUser()` if the
  Neo4j write fails.
- `UserController.updateUser()` - `PATCH /user/updateUser`. Calls `iLoginService.updateUser()`
  (Mongo), then `userNeo4jService.updateUser()` (Neo4j); rolls back by calling
  `iLoginService.updateUser(newUsername, username)` (swapped args) if the Neo4j write fails.

So testing the rollback branch for real means driving it through `MockMvc` against the controller,
not calling `LoginService`/`UserNeo4jService` directly - a pure service-level integration test can
only ever exercise the "both succeed" happy path in isolation.

**How to force the Neo4j write to fail deterministically** (don't rely on flaky infra failures):
both `UserNeo4jRepository.addUser` and `.updateUser` run a raw Cypher `CREATE`/`SET` against
`UserNeo4j.username`, and `Neo4jIndexInitializer` creates a uniqueness constraint on that property
at context startup. Pre-seed a `UserNeo4j` node (via `neo4jClient`) with the username that's about
to collide, and the constraint violation throws inside `UserNeo4jService.addUser`/`.updateUser`,
which is caught there and surfaces as a non-2xx `ResponseEntity` - exactly the branch that triggers
the controller's rollback. This is far more reliable than trying to kill/pause a container mid-test.

After asserting the HTTP response, assert the **actual rollback happened in the database**: query
`mongoTemplate` directly to confirm the Mongo user was deleted (registration) or the username was
reverted (update) - asserting only the HTTP status without checking the DB state defeats the
purpose of an integration test over the equivalent (already-covered) unit test.

## Aggregation pipelines and Cypher queries

`GameRepositoryImpl`/`ReviewRepositoryImpl` (MongoDB aggregation pipelines) and the `@Query` Cypher
methods on `UserNeo4jRepository`/`GameNeo4jRepository` are the other prime candidates for this
skill - seed a handful of documents/nodes directly via `mongoTemplate`/`neo4jClient`, call the
repository method, and assert on the actual result shape. This is where the data quirks documented
in the `backend-tests` skill (comma-separated `genres`/`categories`, string `releaseDate`, the
`PageImpl` total-count gotcha) matter just as much - a real aggregation pipeline can fail on a
quirk a mocked test glossed over, so don't skip re-reading that section just because this is a
different skill.

## Redis

`GameNeo4jService`/`UserNeo4jService` wrap every Redis read/write in a try/catch that logs a
warning and falls through to the non-cached path on failure (see the "Redis non raggiungibile"
comments in those classes) - Redis is an optimization, not a correctness dependency. There is
deliberately **no Redis Testcontainer** in `IntegrationTestSupport`: correctness assertions work
fine without one. Only add a Redis container in a specific test if you're testing caching
*behavior itself* (e.g. a suggestion result is actually cached/expired), not as a default.

## Before calling a test task done

- Run `./mvnw verify -Dtest=none -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=YourClassIT`
  (or `./mvnw verify` for the full suite) from `gameHub/` - **not** `./mvnw test`, which never even
  runs `*IT.java` classes in this project's Failsafe binding. Confirm it's green.
- `./mvnw verify` runs the *entire* Maven lifecycle up through `verify`, which includes the plain
  `mvnw test` unit suite first - and `GameHubApplicationTests.contextLoads()` in that suite needs a
  real, already-running `mongo_local`/`neo4j_local` (see CLAUDE.md's "Build and run") or the whole
  build fails before it ever reaches your `IT`/`E2EIT` classes. `docker start mongo_local
  neo4j_local` if they're not up - this is a pre-existing project requirement, unrelated to
  Testcontainers.
- Needs a working Docker daemon - Testcontainers pulls `mongo:7.0` and `neo4j:5.15` images and
  starts fresh, disposable containers; it never touches `mongo_local`/`neo4j_local` or their
  production-scale dataset, so it's safe to have both running at once.
- **If Testcontainers fails at class-init with "Could not find a valid Docker environment"** and
  the logged attempted-strategies show `client version 1.32 is too old. Minimum supported API
  version is 1.40`: this isn't a project bug, it's `DockerClientProviderStrategy` (shaded inside
  every testcontainers-java release checked so far, including recent ones - bumping the
  `testcontainers.version` property does *not* fix it) hardcoding Docker API `1.32` whenever no
  explicit version is configured, which newer Docker Engine builds reject outright. Already fixed
  in this project's `pom.xml` (`maven-failsafe-plugin` sets the system property
  `api.version=1.40` for the forked test JVM) - if it resurfaces on a different machine with an
  even newer Docker Engine that raises its own minimum further, bump that property, not the
  testcontainers dependency version.
- Checkstyle/Spotless apply to `IT` classes exactly as they do to unit tests (see the `backend-tests`
  skill) - the Stop hook (`gameHub/.claude/hooks/stop-lint-check.sh`) runs both automatically on
  changed `.java` files, but don't rely on it silently; if it blocks, fix what it flags.
