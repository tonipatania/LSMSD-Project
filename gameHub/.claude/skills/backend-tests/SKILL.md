---
name: backend-tests
description: Write JUnit 5 / Mockito / MockMvc tests for the gameHub Spring Boot backend (controller, service, and dual-write Mongo+Neo4j rollback logic under src/main/java/it/unipi/lsmsd/gamehub). Use this whenever the user asks to write, add, update, or improve tests for this backend, or mentions test coverage, unit tests, or the Mongo/Neo4j rollback behavior — even without saying "test" explicitly, e.g. "cover LoginService", "make sure the registration rollback is tested", "test the genre filter in GameRepositoryImpl".
---

## Where tests go

Mirror the main package layout under `src/test/java/it/unipi/lsmsd/gamehub/...`. Class under test `Foo` → test class `FooTest` in the matching package (e.g. `service/impl/LoginService.java` → `service/impl/LoginServiceTest.java`).

Method names: `methodName_condition_expectedResult()` — e.g. `registrate_duplicateUsername_returnsConflict()`, `authenticate_wrongPassword_returnsUnsuccessfulResponse()`. Keep this consistent across the suite rather than mixing styles.

## What to use per layer

- **Service layer** (`service/impl/*`): JUnit 5 + Mockito. `@ExtendWith(MockitoExtension.class)`, `@Mock` the repositories/collaborators, `@InjectMocks` the service. No Spring context needed here — these are plain object tests.
- **Controller layer**: `@WebMvcTest(FooController.class)` + `MockMvc`, `@MockBean` the service interfaces (`I*Service`). Assert HTTP status and JSON body shape, not service internals.
  - `SecurityConfig` wires `JwtAuthenticationFilter` in front of everything except `/login`, `/signup`, and CORS preflight, and `@WebMvcTest` loads it by default — an unauthenticated `MockMvc` call to a protected endpoint gets 401 before it reaches your controller. Add `@AutoConfigureMockMvc(addFilters = false)` to disable the filter chain when the test is about controller/request-mapping logic, not auth. If a test is specifically about auth (e.g. verifying `/user/loadgames` requires `ROLE_ADMIN`), keep the filters on and use `@WithMockUser` (or a real JWT via `JwtService`, mocked) instead of disabling them.
  - `@AutoConfigureMockMvc(addFilters = false)` only stops the filter chain from *running* — it doesn't remove `JwtAuthenticationFilter` from the context, and Spring still has to construct that bean at startup. Its constructor needs a `JwtService`, which is a plain `@Service` that `@WebMvcTest`'s restricted component scan does **not** pick up on its own (unlike `@Component`/`Filter`/security-config classes, which it does include). Without an explicit `@MockBean private JwtService jwtService;` in every `@WebMvcTest` for a controller behind `SecurityConfig`, context startup fails with `NoSuchBeanDefinitionException` for `JwtService` — add that `@MockBean` line as a matter of course, not only when a test happens to touch auth.
- **Avoid `@SpringBootTest`** (full context, real Mongo/Neo4j) unless the user explicitly asks for an integration test — there's no Testcontainers setup in this project yet, so those tests need a real local Mongo (`mongodb://localhost:27017/game`) and Neo4j (`bolt://localhost:7687`) and will be slow or flaky without one.

## Priority: dual-write rollback paths

This is the most distinctive and highest-risk logic in the codebase (see the repo's `CLAUDE.md`, "write-then-rollback pattern") — every write that touches both Mongo and Neo4j (registration, username update, etc.) must undo the first write if the second fails, since the two stores aren't transactional together. Bugs here silently corrupt data across two databases, so when a task touches this path, cover all three branches, not just the happy one:

1. **Both writes succeed** → normal success response, no rollback call made.
2. **Second write fails** (e.g. Neo4j node creation fails after the Mongo write in `LoginController.registration()`) → assert the compensating call happens (e.g. `loginService.removeUser(...)` is invoked) and the response reflects failure, not partial success.
3. **First write fails** → assert the second write is never attempted (`verify(secondCollaborator, never())...`).

Mock at the repository/service-collaborator boundary (e.g. mock `LoginRepository` and `IUserNeo4jService`), not the underlying Mongo/Neo4j driver — that's what makes these fast, deterministic unit tests instead of integration tests.

## Data quirks to test around

- `Game.genres` / `Game.categories` are comma-separated strings, not arrays. A genre-filter test must not pass on a loose substring match (e.g. filtering `"RPG"` matching a stored `"RPGaction"`) — assert against the actual whole-token regex path used in `GameRepositoryImpl.searchGames`, and include a case with multiple comma-separated values to catch a naive `contains()` bug.
- `Game.releaseDate` is a string in `"MMM d, yyyy"` format, sorted/filtered in-memory (not delegated to Mongo). Date-ordering tests should feed unordered input and assert the output order, rather than relying on natural document order to happen to look sorted.
- Building a `new PageImpl<>(content, pageable, total)` by hand in a mock is not as literal as it looks: Spring's constructor silently overrides `total` to `pageable.getOffset() + content.size()` whenever `content` is non-empty and `pageable.getOffset() + pageable.getPageSize() > total` — it assumes an inconsistent total means you're describing the last page. This bites pagination tests in `GameController`/`GameRepositoryImpl` two ways: (1) a small `total` with a large page size gets silently rewritten, so pass a `total >= pageSize` when you need an exact total to survive; (2) constructing "the last/only page" with a non-zero offset inflates `total` in the same way — use offset `0` unless the inflation is what you're testing. When you need a page whose content is empty but more pages exist (e.g. to reach an `isEmpty()` branch after a "page beyond range" 404 check), give it a `total` bigger than one page's worth, not `0` — an empty page with `total=0` always has zero total pages, so a "current page number >= total pages" guard fires first and the branch you wanted is unreachable.

## Before calling a test task done

- Run `./mvnw test` from `gameHub/` (or `./mvnw test -Dtest=ClassName#method` to target one test) and confirm it's green.
- Checkstyle (`checkstyle.xml`, `includeTestSourceDirectory=true`) runs on test sources too and fails the build on any warning — new test files need to satisfy it, not just compile. Fix what it flags rather than suppressing it.
- Spotless (google-java-format, AOSP style) also formats test sources; run `./mvnw spotless:apply` if formatting gets flagged.
