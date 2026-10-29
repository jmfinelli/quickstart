# Design: Migrate the `vertx` module from Narayana to the Quickstart repo

**Date:** 2026-10-27
**Status:** Approved
**Author:** drafted with Claude (brainstorming)

## Intent and why

The `vertx` sub-project currently lives in the **Narayana** core repository
(`narayana/vertx`). It is not core transaction-manager code — it is a set of
*examples* showing how to use Narayana's STM implementation inside a Vert.x
environment (a port of Mark Little's original TransactionalVert.x repo). That
is exactly what the **quickstart** repository is for.

Keeping example code in the core repo means it is built as part of the
Narayana reactor, carries Vert.x version management in Narayana's shared BOM,
and is maintained alongside production code it has no business living next to.
Moving it to the quickstart repo puts it with its siblings (the other STM,
JTA, JTS, XTS examples), where the project's conventions — automated,
CI-tested, built against a *released* Narayana — already apply.

This is a **clean move**: the `vertx` code leaves Narayana entirely. The STM
module itself **stays in Narayana** (it is core code); its self-contained
`VertxUnitTest` is unaffected and stays with it.

## Goals

1. All of `narayana/vertx` — the four sub-modules (`module`, `shared`,
   `echo`, `raw`) **and** the `docs` folder — is reproduced in the quickstart
   repository.
2. The migrated code builds under the quickstart's normal CI flow:
   `./build.sh clean install` over the reactor, against a freshly-built
   Narayana, with **no manual steps** (no separate Vert.x install, no hand-run
   `build.sh` inside a sub-module). Each example **actually runs and asserts**
   during the build via `exec-maven-plugin` bound to a lifecycle phase
   (`integration-test`), as the auto-run quickstarts do (e.g. `XTS/ssl`,
   `rts/at/annotation`). Note: the `STM/*` siblings — and `rts/at/simple` —
   wire `exec` *without* a phase
   binding, so they run only on an explicit `exec:exec` and are **not**
   exercised by CI's `clean install` — decision #6 deliberately goes further
   than the STM siblings by making these examples run in CI.
3. Narayana is left clean: no `vertx` module, no `vertx` references in the
   root reactor, QA config, or shared BOM.
4. STM remains in Narayana, untouched and still building.

## Non-goals

- Modernising the examples beyond what is needed to build and pass CI on
  Vert.x 5.2.2 (see "Known caveat" — the legacy Vert.x module-system packaging
  is kept as-is, not redesigned).
- Changing STM itself, or its `VertxUnitTest`.
- Preserving cross-repo git history. These are two independent git repos; the
  move is a copy-plus-delete. Original authorship is already acknowledged in
  the module READMEs (Mark Little / Tim Fox).
- Adding *new* example functionality. (Wiring existing examples to execute and
  assert in CI — decision #6 — is in scope; inventing new behaviour is not.)

## Anti-goals (what would make this a failure even if it "works")

- Quickstart CI that silently skips the new module, or a module that only
  builds with a manual pre-step. If it is not exercised by the standard
  `build.sh clean install`, the migration has failed its purpose.
- Leaving dangling `vertx` references in Narayana (reactor module list, BOM,
  QA excludes) that break or pollute the Narayana build.
- Breaking the Narayana build for STM or anything else as a side effect.

## Constraints

- **Target Vert.x version: 5.2.2** (human decision). The quickstart will
  **self-manage** this version in its own poms; it will **not** rely on
  Narayana's BOM for Vert.x coordinates.
- **Java 17** — the quickstart baseline (root pom `maven.compiler.source/target`
  = 17; CI enforces a 17 minimum). The Narayana `vertx` poms currently compile
  at Java 8; this must be raised to 17 on migration.
- Quickstarts build against a **published/installed** Narayana, not a reactor
  sibling. So the migrated poms must depend on the published artifact
  `org.jboss.narayana.stm:stm` at `${version.org.jboss.narayana}`
  (= `${project.version}`), instead of the reactor's `${project.version}`
  sibling reference.
- Version alignment: both repos are currently `7.3.5.Final-SNAPSHOT`, so the
  published-artifact dependency resolves to the Narayana built first in CI.
- **JDK verification target: 25, not 17.** The quickstart CI matrix defaults to
  **JDK 25** (`parser.yml`); `17` is only the enforced *minimum* (in
  `quickstart.sh`). Code compiles at release 17, but all tests and example runs
  execute on the JDK 25 JVM, so that is the real "green" target. Adding JVM args
  where JDK 25 needs them (e.g. `--add-opens` for `vertx-hazelcast` in `shared`)
  is expected and in scope.
- **Two independent repos; quickstart CI never sees the local Narayana clone.**
  `.github/scripts/quickstart.sh` clones *upstream* `jbosstm/narayana`
  (`NARAYANA_BRANCH` default `main`) and builds it to supply the published `stm`
  artifact. Therefore the Narayana-removal PR is validated only by *Narayana's*
  CI, never by quickstart CI. Merge order is safe either way: `stm` stays in
  Narayana, and the quickstart self-manages Vert.x, so neither side breaks the
  other regardless of which merges first.

## Decisions already made (by the human)

| # | Decision | Choice |
|---|----------|--------|
| 1 | Scope | Migrate **everything** — all four sub-modules + docs. |
| 2 | Placement | **Top-level `quickstart/vertx/`** reactor module, mirroring Narayana's layout, added to the quickstart root `pom.xml`. |
| 3 | Automation | **Everything builds in CI with no manual steps**, under `mvn clean install`. |
| 4 | Narayana BOM | **Remove** all `io.vertx` entries (and `version.vertx`) from Narayana's `boms/main` (`main-dependency-management`); the quickstart declares Vert.x versions itself. |
| 5 | Vert.x target version | **5.2.2.** |
| 6 | Run depth | **Run the examples in CI, not merely compile them** — each example *executes and asserts* via `exec-maven-plugin` bound to the `integration-test` phase (the pattern used by `XTS/ssl` and `rts/at/annotation`; note the `STM/*` siblings — and `rts/at/simple` — do **not** phase-bind their `exec`, so decision #6 goes further than them). Covers `echo` (orchestrate server + client), `shared` (run the sharing example), and `raw` (run the hammer, made to exit cleanly). |
| 7 | Repo scope | **Both repos, two PRs** — a quickstart-addition PR *and* a Narayana-removal PR, each verified by its own repo's CI. |

## Decisions made by the author (builder's calls — flagged for the human to veto)

- **The existing `quickstart/STM/vertx/` stub is removed.** It currently holds
  only a `README.adoc` saying the quickstart "was never automated so has been
  replaced by [the narayana conferences repo]." The real migration supersedes
  that pointer, and the new home is top-level `vertx/` (decision #2), so the
  stub becomes misleading.
- **The legacy `mod.json` / `vertx.mods` packaging in `module` is preserved
  as-is** (it still assembles and builds). Not redesigned — see Known caveat.
- Design document committed to the **quickstart** repo (destination of the
  work), since the parent `vertex_migration` directory is not itself a repo.

## Spike findings (throwaway, already run) — the actual port size

A throwaway spike resolved the Vert.x 5.2.2 artifacts and checked every
Vert.x API the code uses:

| API used by the code | In 5.2.2? | Consequence |
|----------------------|-----------|-------------|
| `io.vertx.core.streams.Pump` | **Removed** | Rewrite — only in `echo/EchoServer.java`. |
| `io.vertx.core.AbstractVerticle` | Present | Verticles (`module`, `shared`, `echo`) compile as-is. |
| `io.vertx.ext.unit.TestContext` (vertx-unit) | Present | Integration test compiles. |
| `io.vertx.test.core.VertxTestBase` | Present (in `vertx-core` **test-jar**, already a declared test dep) | Integration test base compiles. |
| `io.vertx.core.streams.ReadStream` | Present | Replacement path for `Pump`. |

**Conclusion:** the only source-level **API-compatibility** breakage is `Pump`,
in one file. The integration tests (`ModuleIntegrationTest`) are effectively
no-ops (`testPing` publishes to the event bus but never awaits;
`testSomethingElse` just calls `testComplete()`), so they pass trivially once
compiling. The *API porting* is near-zero; the bulk of the effort is
**structural repackaging** plus the run-in-CI wiring below.

### The code changes, concretely

Two kinds of source change are required:

1. **The `Pump` API fix (the only compatibility break).**
   `echo/EchoServer.java` uses `Pump.pump(sock, sock).start();`. Replace with
   the Vert.x 5 idiom `sock.pipeTo(sock)` (via `ReadStream.pipeTo`).

2. **Run-in-CI wiring for `shared`, `echo`, `raw` (required by decision #6).**
   Because these examples deploy verticles (which keep the JVM alive) and were
   never written as deterministic tests, each needs added code to **terminate
   cleanly** (close Vert.x, exit 0 on success / non-zero on failure) and to
   **assert** its expected outcome. This is not an API port, but it is real
   code beyond `Pump` — see the Risks section. The `module` sub-module needs no
   such wiring (its JUnit/failsafe tests already run under `install`).

## Known caveat (in scope to preserve, out of scope to fix)

The `module` sub-module packages "STM as a Vert.x module" using the **Vert.x 2/3
module system** (`mod.json`, the `vertx.mods` system property, an assembly that
builds a `groupId~artifactId~version` mod directory). **That module system was
removed in Vert.x 4+ and does not exist at runtime in Vert.x 5.** The assembly
is just file-copying, so it still *builds*; and because the integration test
does not actually deploy via that mechanism, CI stays green. But the packaged
artifact is semantically dead on Vert.x 5. A faithful migration keeps it
(decision #3 is "everything"); genuinely modernising "STM as a Vert.x module"
(e.g. as a deployable verticle) is **out of scope** unless the human asks.

## Source inventory (what moves)

```
narayana/vertx/
  pom.xml                       reactor parent (packaging=pom, 4 modules)
  README.md                     overview
  docs/README.md                the STM guide (prose; not a maven module)
  module/                       STM-as-Vert.x-module; real src/main + src/test; mod.json; assembly
    vertx_classpath.txt, pom.xml, README.md
    src/main/java/org/jboss/stm/STMVerticle.java
    src/main/resources/{mod.json, jbossts-properties.xml}
    src/main/assembly/mod.xml
    src/test/java/unit/{SampleUnitTest,BasicUnitTest,STMUnitTest}.java
    src/test/java/integration/java/ModuleIntegrationTest.java
  shared/                       runnable sharing example; src/main/java/*.java
  echo/                         LOOSE .java files + build.sh (NOT src layout) + jbossts-properties.xml
  raw/                          TxOJ locking demo; src/main/java/*.java + build.sh
```

## Destination shape (what gets built)

```
quickstart/
  pom.xml                       add <module>vertx</module> to <modules>
  vertx/
    pom.xml                     parent: narayana-quickstarts-all; declares Vert.x 5.2.2 version mgmt
    README.md (or .adoc)        overview (see "what's left to the builder" re: format)
    docs/                       the STM guide
    module/   shared/   echo/   raw/
```

## Work breakdown

### A. Narayana side — clean removal
1. Delete the `narayana/vertx` directory in its entirety.
2. Remove `<module>vertx</module>` from `narayana/pom.xml` (currently line 126).
3. Remove the `<exclude name="**/vertx/**"/>` from `narayana/qa/run-tests.xml`.
4. Remove all `io.vertx` dependency-management entries **and** the
   `<version.vertx>` property from `narayana/boms/main/pom.xml`.
   *(Verified during brainstorming: `io.vertx` / `version.vertx` are used
   nowhere in Narayana outside `boms/main` and the `vertx` module itself, so
   removal is safe for the rest of the Narayana build.)*
5. Confirm STM and the full Narayana reactor still build (STM's
   `VertxUnitTest` is self-contained and must keep passing).

### B. Quickstart side — add the module
6. Create `quickstart/vertx/` with the four sub-modules + `docs`, content
   copied from Narayana.
7. Re-parent every pom to `org.jboss.narayana.quickstart:narayana-quickstarts-all`
   (the quickstart root), replacing the old `org.jboss.narayana.vertx` /
   `narayana-all` parents.
8. Declare Vert.x **5.2.2** version management in the new `vertx/pom.xml`
   (`vertx-core`, `vertx-core` test-jar, `vertx-hazelcast`, `vertx-unit`) —
   this is what used to live in Narayana's BOM.
9. Change the STM dependency from the reactor-sibling form to the published
   artifact `org.jboss.narayana.stm:stm` at `${version.org.jboss.narayana}`.
10. Raise Java level from 8 to **17** in the migrated poms (align with the
    quickstart baseline).
11. Apply the `Pump` API fix: `Pump` → `ReadStream.pipeTo` in
    `echo/EchoServer.java` (the run-in-CI termination/assertion code is step 13).
12. Restructure `echo` from loose `.java` files + `build.sh` into a proper
    Maven module (`src/main/java/...`), so it compiles in the reactor with no
    manual Vert.x install. Remove/replace the manual `build.sh` flows in `echo`
    and `raw`.
13. **Wire each example to run and assert in CI** via `exec-maven-plugin`
    bound to the `integration-test` phase (decision #6), modelled on
    `quickstart/XTS/ssl` and `quickstart/rts/at/annotation` (phase-bound exec
    that CI's `clean install` actually runs — unlike `STM/optimistic` or
    `rts/at/simple`, whose exec is not phase-bound):
    - `shared`: execute the example's entry point (`ClientVerticle.main`) and
      assert the success marker. Note it uses a *non-clustered* local `Vertx`
      (`getLocalMap`), so `vertx-hazelcast` is on the classpath but not
      initialised — add JDK 25 `--add-opens` args only if a run actually throws
      `InaccessibleObjectException`.
    - `raw`: execute the hammer and ensure it **exits 0**. It already completes
      normally today (single-threaded, single-process — no lock contention, so
      no `TestException` escapes); the real work is a clean JVM shutdown /
      `System.exit(0)` in case Narayana leaves non-daemon threads alive, not
      suppressing a throw.
    - `echo`: orchestrate the two `main()`s — start the `EchoServer`
      (NetServer on 1234), run the `EchoClient` against it, assert the echo, and
      shut the server down — all within the build, no external process.
14. Add `<module>vertx</module>` to `quickstart/pom.xml`.
15. Remove the now-superseded `quickstart/STM/vertx/` stub (builder's call
    above).

### C. Verify (both sides) — on JDK 25 (the CI default)
16. Narayana: full reactor build green, STM green (its `VertxUnitTest` keeps
    passing).
17. Quickstart: `./build.sh clean install` green including the new `vertx`
    module **with every example actually executing and asserting**, reproducing
    what CI (`.github/scripts/quickstart.sh`) does — Narayana built/installed
    first, then the quickstart reactor against it — run on **JDK 25**.

## What's left to the builder

- **README format.** Narayana's vertx READMEs are Markdown (`.md`); the
  quickstart mixes `.md` and `.adoc`. The builder picks the format that matches
  the quickstart's prevailing convention for a new quickstart, and updates the
  prose to remove Narayana-internal references (e.g. "will be removed in a
  subsequent commit", the `raw` "does not belong here" note) so the examples
  read as finished quickstarts.
- **Exact pom wiring** (dependency scopes, assembly/failsafe plugin config,
  `mods.directory` handling) — reproduce Narayana's working configuration,
  adjusted for the new parent and self-managed Vert.x version.
- **Package/source-root fixes** for `echo` when moving loose files under
  `src/main/java` (e.g. default-package vs a package; keeping `main()` entry
  points runnable).
- **CODEOWNERS / CI matrix / dependabot** touch-ups in the quickstart repo if
  a new top-level module needs registering anywhere beyond the root pom
  `<modules>` list (builder to check `.github/` for any per-module lists).
- Whether `raw` (a non-Vert.x TxOJ locking demo) reads better as its own
  top-level quickstart vs. a sub-module of `vertx`; default is to keep it where
  the human said — inside `vertx/` — but the builder may flag it.

## Risks

- **Making the heavier examples run reliably in CI (decision #6) is the main
  effort and risk.** `echo` requires orchestrating a server + client inside the
  build and asserting the round-trip; `shared` runs on JDK 25 (it carries
  `vertx-hazelcast` but uses a non-clustered local `Vertx`, so `--add-opens`
  may be unnecessary — add only if a run throws `InaccessibleObjectException`);
  `raw` already completes today but must be made to exit the JVM cleanly. The
  shared risk across all three: a deployed verticle keeps the JVM alive, so each
  must terminate deterministically or `mvn install` hangs. This is where the
  port's real uncertainty lives — not in the (near-clean) API delta. CI
  flakiness (ports, timing, non-termination) is a live possibility the builder
  must design against.
- **Legacy module-system packaging** (see Known caveat) — preserved
  deliberately; a reviewer may still find it odd.
