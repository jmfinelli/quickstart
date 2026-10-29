# Vertx Quickstart Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Reproduce the Narayana `vertx` sub-project as a top-level `vertx/` quickstart that builds and runs (examples execute and assert) under the quickstart's standard `clean install` on JDK 25.

**Architecture:** A new top-level reactor module `quickstart/vertx/` whose parent is `narayana-quickstarts-all`, with four sub-modules (`module`, `shared`, `echo`, `raw`) plus `docs`. The `module` sub-module keeps its JUnit/failsafe tests (which run natively under `install`); `shared`, `echo`, and `raw` are wired to **execute during the build** via `exec-maven-plugin` bound to the `integration-test` phase (because CI runs only `clean install`, never `exec:exec`). Dependencies resolve against the *published* Narayana (`org.jboss.narayana.stm:stm`), and the Vert.x 5.2.2 version is self-managed in the new parent pom.

**Tech Stack:** Maven, Java 17 (compiled; executed on JDK 25 in CI), Vert.x 5.2.2 (`vertx-core`, `vertx-hazelcast`, `vertx-unit`), Narayana STM, JUnit, `exec-maven-plugin` 3.6.4.

**Spec:** `docs/superpowers/specs/2026-10-27-vertx-quickstart-migration-design.md`

**Companion plan:** `narayana/docs/superpowers/plans/2026-10-27-vertx-removal.md` removes the sources from Narayana. Independent PR; any merge order.

## Global Constraints

- Quickstart version `7.3.5.Final-SNAPSHOT`.
- Compile at release **17** (`maven.compiler.source/target` inherited from the quickstart root pom); verify green on **JDK 25** — the CI matrix default. Add JVM args (e.g. `--add-opens java.base/...` for `vertx-hazelcast`) where JDK 25 requires them.
- Self-manage Vert.x **5.2.2** in `vertx/pom.xml`; do **not** rely on Narayana's BOM for `io.vertx` coordinates.
- Depend on the published STM artifact: `org.jboss.narayana.stm:stm` at `${version.org.jboss.narayana}` (= `${project.version}`). Not a reactor-sibling reference.
- Everything must pass under `./build.sh -B clean install` with **no manual steps and no explicit `exec:exec`** — examples run only if their `exec` execution is bound to a lifecycle phase.
- Preserve the `module` sub-module's legacy `mod.json` / assembly / `vertx.mods` packaging as-is (see spec "Known caveat").
- New Java files carry the SPDX header used across the repo (`Copyright The Narayana Authors` / `SPDX-License-Identifier: Apache-2.0`); no `@author` tags.
- **Prerequisite for every build command below:** the published Narayana `stm` artifact (`7.3.5.Final-SNAPSHOT`) must already be installed in the local Maven repo. Build/install Narayana once up front (`cd narayana && ./build.sh clean install -DskipTests`); the quickstart never rebuilds it (`-am` only spans the quickstart reactor, and `stm` is external to it).

## Review Focus

- **Verticles keep the JVM alive.** A deployed Vert.x verticle holds event-loop threads, so an example's `main()` will not return and `mvn install` will hang forever. Every executed example (`shared`, `echo`, `raw`) must deterministically finish and terminate the JVM (close Vert.x, then exit 0 on success / non-zero on failure). — Pinned in Tasks 3, 4, 5.
- **`echo` must assert the round-trip, not just start.** Server + client must actually exchange bytes and compare them, else a broken echo "passes". — Pinned in Task 4.
- **JDK 25 + hazelcast module access.** `shared` carries `vertx-hazelcast`, which *can* need `--add-opens` on modern JDKs — but its executed path uses a non-clustered local `Vertx` (`getLocalMap`), so Hazelcast may never initialise and the flags may be unnecessary. Add them only if a run throws `InaccessibleObjectException`. — Pinned in Task 3.
- **Filesystem ObjectStore pollution / non-reproducibility.** The STM examples write a Narayana ObjectStore to disk; point it under `${project.build.directory}` so `clean` resets it and parallel/repeat builds don't collide. — Pinned in Tasks 3–5.
- **`module` failsafe integration test still runs.** The `vertx.mods`-driven failsafe execution must still fire under `install` and pass (the test is a no-op but must not error). — Pinned in Task 2.

---

### Task 1: Create the `vertx` reactor parent, wire it into the root, carry `docs`

**Files:**
- Create: `vertx/pom.xml`
- Create: `vertx/README.md` (overview; prose carried from Narayana, Narayana-internal notes removed)
- Create: `vertx/docs/README.md` (the STM guide, copied verbatim)
- Modify: `pom.xml` (root — add `<module>vertx</module>` to `<modules>`)

**Interfaces:**
- Produces: a reactor parent `org.jboss.narayana.quickstart:narayana-quickstart-vertx` (packaging `pom`, parent `narayana-quickstarts-all`) whose `<dependencyManagement>` pins Vert.x 5.2.2 (`vertx-core`, `vertx-core` test-jar classifier `tests`, `vertx-hazelcast`, `vertx-unit`) and the published `stm`; and whose `<modules>` starts empty (each later task appends its own module). Sub-module poms set `<parent>` to this artifact.

- [ ] **Step 1: Write `vertx/pom.xml`** — parent `narayana-quickstarts-all`; packaging `pom`; empty `<modules/>`; `<dependencyManagement>` declaring Vert.x 5.2.2 coordinates and `org.jboss.narayana.stm:stm:${version.org.jboss.narayana}`; include the `jboss-public-repository-group` `<repositories>` block (copy from `STM/optimistic/pom.xml`).

- [ ] **Step 2: Add `<module>vertx</module>`** to the root `pom.xml` `<modules>` list.

- [ ] **Step 3: Copy `docs/` and write `vertx/README.md`** from Narayana's `vertx/README.md` and `vertx/docs/README.md`, deleting Narayana-internal lines (e.g. "will be removed in a subsequent commit").

- [ ] **Step 4: Verify the parent resolves**

Run: `./build.sh -B -pl vertx -N install`
Expected: BUILD SUCCESS — the `vertx` parent pom installs. (Note: the quickstart `build.sh` only treats `clean`/`test`/`install`/`deploy`/`site`/`help` as Maven goals; any other token is passed through to Maven as an extra argument, and `install` is appended only when no recognised goal is present — so `validate` would run `mvn install validate`. Use `install`.)

- [ ] **Step 5: Commit**

```bash
git add vertx/pom.xml vertx/README.md vertx/docs pom.xml
git commit -m "Add vertx quickstart reactor parent and wire into root"
```

---

### Task 2: Migrate the `module` sub-module (STM-as-Vert.x-module + its tests)

**Files:**
- Create: `vertx/module/` — copy from Narayana `vertx/module/` (`src/main/java/org/jboss/stm/STMVerticle.java`, `src/main/resources/{mod.json,jbossts-properties.xml}`, `src/main/assembly/mod.xml`, `src/main/README.txt`, `src/main/platform_lib/`, `vertx_classpath.txt`, all `src/test/java/unit/*` and `src/test/java/integration/java/ModuleIntegrationTest.java`)
- Create: `vertx/module/pom.xml` (adapted from Narayana `vertx/module/pom.xml`)
- Modify: `vertx/pom.xml` (append `<module>module</module>`)

**Interfaces:**
- Consumes: the `vertx` parent + its dependency management (Task 1).
- Produces: artifact `mod-stm` (packaging `jar`). No exec wiring — its tests run under surefire/failsafe.

- [ ] **Step 1: Copy sources and adapt the pom.** Set `<parent>` to the Task 1 `vertx` artifact; depend on published `stm`, `vertx-core`/`vertx-hazelcast` (`provided`), and the test deps (`junit`, `vertx-core` `test-jar`, `vertx-unit`), `jboss-logging`. Keep the `maven-resources-plugin` / `maven-dependency-plugin` / `maven-assembly-plugin` (`mod.xml`) / `maven-failsafe-plugin` (with the `vertx.mods` system property and `**/integration/**` include) configuration from the Narayana pom. Remove the hardcoded `<source>1.8</source>/<target>1.8</target>` compiler config so it inherits release 17.

- [ ] **Step 2: Append `<module>module</module>`** to `vertx/pom.xml`.

- [ ] **Step 3: Build and run tests**

Run: `./build.sh -B -pl vertx/module -am clean install`
Expected: the unit tests (`BasicUnitTest`, `SampleUnitTest`, `STMUnitTest`) and the failsafe `ModuleIntegrationTest` run and pass; the mod assembly is produced; BUILD SUCCESS.

- [ ] **Step 4: Commit**

```bash
git add vertx/module vertx/pom.xml
git commit -m "Migrate vertx 'module' quickstart (STM as a Vert.x module)"
```

---

### Task 3: Migrate the `shared` sub-module (run the sharing example in CI)

**Files:**
- Create: `vertx/shared/` — copy `src/main/java/*.java` (`Sample.java`, `SampleLockable.java`, `ClientVerticle.java`, `SampleVerticle1.java`, `SampleVerticle2.java`) and `src/main/resources/jbossts-properties.xml`
- Create: `vertx/shared/pom.xml`
- Modify: `vertx/pom.xml` (append `<module>shared</module>`)

**Interfaces:**
- Consumes: the `vertx` parent (Task 1). Entry point: `ClientVerticle.main` (default package), which deploys `ClientVerticle` → `SampleVerticle1` and `INSTANCE_CNT` copies of `SampleVerticle2`. Keep `INSTANCE_CNT = 0` (the deterministic, single-verticle path).
- Produces: a module that, during `integration-test`, runs the example and exits 0 on success.

- [ ] **Step 1: Copy sources; make the example terminate deterministically.** After `SampleVerticle1` reports success, the example must close Vert.x and exit the JVM with status 0 (and non-zero if a verticle reports failure). The success marker printed today is `SampleVerticle1 SUCCEEDED!`, written to **`System.err`** (`SampleVerticle1.java:56`), not stdout — any output-based assertion must read stderr (or move the marker to stdout). `SampleVerticle1` already prints `SampleVerticle1 FAILED!` on the failure branch without a non-zero exit, so the exit-code logic is new code. Keep `INSTANCE_CNT = 0`.

- [ ] **Step 2: Point the ObjectStore under the build dir.** Configure Narayana's object store location (via `jbossts-properties.xml` and/or `-Dcom.arjuna.ats.arjuna.common.propertiesFile=...`) so state is written under `${project.build.directory}/exec-working-directory`, not the source tree.

- [ ] **Step 3: Write the pom.** Parent = `vertx`; reproduce Narayana `shared/pom.xml`'s deps — `vertx-core` + `vertx-hazelcast` + `stm` + `jboss-logging` (compile scope). Add `exec-maven-plugin` 3.6.4, execution bound to `<phase>integration-test</phase>`, goal `exec`, `executable=java`, arguments: `-classpath <classpath/>`, the properties-file system property, and main class `ClientVerticle`; `workingDirectory=${project.build.directory}/exec-working-directory`. Add JDK-25 `--add-opens` arguments **only if** Step 5 shows an `InaccessibleObjectException` (the example uses a non-clustered local `Vertx`, so Hazelcast may never initialise). Model the phase-bound exec on `XTS/ssl/pom.xml` or `rts/at/annotation/pom.xml` — **not** `rts/at/simple`, whose exec is *not* phase-bound and would never run under `clean install`.

- [ ] **Step 4: Append `<module>shared</module>`** to `vertx/pom.xml`.

- [ ] **Step 5: Run it (expect it to exercise the example and finish)**

Run: `./build.sh -B -pl vertx/shared -am clean install`
Expected: the example runs during `integration-test`, prints `SampleVerticle1 SUCCEEDED!`, the JVM exits 0, and BUILD SUCCESS. If the build hangs, the termination in Step 1 is incomplete; if it throws `InaccessibleObjectException`, the `--add-opens` args in Step 3 are incomplete.

- [ ] **Step 6: Commit**

```bash
git add vertx/shared vertx/pom.xml
git commit -m "Migrate vertx 'shared' quickstart and run it in CI"
```

---

### Task 4: Migrate the `echo` sub-module (restructure, fix `Pump`, orchestrate + assert)

**Files:**
- Create: `vertx/echo/src/main/java/EchoServer.java`, `vertx/echo/src/main/java/EchoClient.java` (moved from Narayana's loose `vertx/echo/*.java` into a proper source root), `vertx/echo/src/main/resources/jbossts-properties.xml`
- Create: `vertx/echo/pom.xml`
- Modify: `vertx/pom.xml` (append `<module>echo</module>`)

**Interfaces:**
- Consumes: the `vertx` parent (Task 1). `EchoServer` opens a `NetServer` on port 1234 and echoes bytes back; `EchoClient` connects and sends **and uses Narayana STM** (`org.jboss.stm.*`, `com.arjuna.ats.arjuna.*`), so `echo` depends on `stm` and its `jbossts-properties.xml` is load-bearing, not vestigial. Both verticles currently `extend AbstractVerticle` with `void start()`.
- Produces: a module that, during `integration-test`, starts the server, sends a known payload from the client, asserts the echo equals the payload, then closes and exits 0 (non-zero on mismatch/timeout).

- [ ] **Step 1: Move the loose files under `src/main/java`** (keep or add a package consistently across both files) and ensure both compile.

- [ ] **Step 2: Fix the removed `Pump` API.** In `EchoServer.java` replace `Pump.pump(sock, sock).start();` with the Vert.x 5 idiom `sock.pipeTo(sock)` and remove the `io.vertx.core.streams.Pump` import.

- [ ] **Step 3: Make the example self-contained, asserting, and terminating.** The executed entry point must: start the server, connect a client, send a fixed payload (e.g. `"hello-stm"`), read the echo, assert it equals the payload, then close the server/clients/Vert.x and exit 0 — or exit non-zero (and print the mismatch) on failure or a short timeout. Choose a free port deterministically or keep 1234 within the isolated exec JVM.

- [ ] **Step 4: Write the pom.** Parent = `vertx`; reproduce Narayana `echo/pom.xml`'s deps — `vertx-core`, `org.jboss.narayana.stm:stm`, `jboss-logging` (all compile scope). Add the `exec-maven-plugin` execution bound to `<phase>integration-test</phase>` running the orchestrating main class, `workingDirectory=${project.build.directory}/exec-working-directory` (model on `XTS/ssl`). Append `<module>echo</module>` to `vertx/pom.xml`.

- [ ] **Step 5: Run it**

Run: `./build.sh -B -pl vertx/echo -am clean install`
Expected: compiles (no `Pump`), the exec run exchanges the payload, asserts the echo, exits 0; BUILD SUCCESS. A hang means the verticles/Vert.x were not closed (Step 3).

- [ ] **Step 6: Commit**

```bash
git add vertx/echo vertx/pom.xml
git commit -m "Migrate vertx 'echo' quickstart: port Pump, run and assert in CI"
```

---

### Task 5: Migrate the `raw` sub-module (run the TxOJ demo cleanly in CI)

**Files:**
- Create: `vertx/raw/src/main/java/*.java` (`SharedHammer.java`, `AtomicObject.java`, `HammerThreadedObject.java`, `TestException.java`)
- Create: `vertx/raw/pom.xml`
- Modify: `vertx/pom.xml` (append `<module>raw</module>`)

**Interfaces:**
- Consumes: the `vertx` parent (Task 1). Entry point: `SharedHammer.main` (a TxOJ locking demonstrator; per its README it currently completes without contention errors).
- Produces: a module that runs the demo during `integration-test` and exits 0.

- [ ] **Step 1: Copy sources; ensure clean termination and exit 0.** `SharedHammer.main` must complete, write its ObjectStore under `${project.build.directory}/exec-working-directory`, and exit 0 on success (non-zero if it throws). Drop/replace the old hand-run `build.sh`.

- [ ] **Step 2: Write the pom.** Parent = `vertx`; reproduce Narayana `raw/pom.xml`'s deps — `org.jboss.narayana.stm:stm` (which brings ArjunaCore/TxOJ transitively; the sources use `com.arjuna.ats.txoj.*` and `com.arjuna.ats.arjuna.*`), `io.vertx:vertx-core`, `jboss-logging`. (`vertx-core` is in the existing `raw/pom.xml` but unused — no `raw` source imports `io.vertx` — so it may be dropped.) Add the `exec-maven-plugin` execution bound to `<phase>integration-test</phase>` running `SharedHammer` (model on `XTS/ssl` / `rts/at/annotation`, not `rts/at/simple`), `workingDirectory=${project.build.directory}/exec-working-directory`. Append `<module>raw</module>` to `vertx/pom.xml`.

- [ ] **Step 3: Run it**

Run: `./build.sh -B -pl vertx/raw -am clean install`
Expected: the demo runs during `integration-test` and exits 0; BUILD SUCCESS.

- [ ] **Step 4: Commit**

```bash
git add vertx/raw vertx/pom.xml
git commit -m "Migrate vertx 'raw' TxOJ demo and run it in CI"
```

---

### Task 6: Remove the superseded stub, finalize READMEs, verify the whole flow

**Files:**
- Delete: `STM/vertx/` (the stub `README.adoc` pointing to the conferences repo)
- Modify: `vertx/README.md` and the four sub-module READMEs (format + prose cleanup)
- Modify (if needed): `.github/` per-module lists

**Interfaces:**
- Consumes: Tasks 1–5.
- Produces: the finished `vertx` quickstart, green under the full CI flow.

- [ ] **Step 1: Delete the stub** `STM/vertx/`.

- [ ] **Step 2: Finalize READMEs.** Pick the format matching the prevailing quickstart convention for a new quickstart; update each example's README to describe the `mvn clean install` run (no manual Vert.x install, no hand-run `build.sh`) and remove Narayana-internal notes (e.g. `raw`'s "does not belong here").

- [ ] **Step 3: Check `.github/` for per-module registration.** Grep `.github/` for any explicit module/quickstart list that a new top-level module must be added to beyond the root pom `<modules>`; update if found.

- [ ] **Step 4: Full-flow verification on JDK 25.** Reproduce what CI does — build/install the (companion-PR-or-upstream) Narayana first so `stm` is available, then:

Run: `./build.sh -B clean install -fae`
Expected: BUILD SUCCESS including `vertx` and all four sub-modules, with `shared`, `echo`, and `raw` actually executing during `integration-test` and `module`'s tests passing — on a JDK 25 JVM.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "Remove superseded STM/vertx stub; finalize vertx quickstart docs"
```
