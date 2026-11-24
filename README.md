# FastStats

> [!NOTE]
> This is a patch-based fork of [faststats-dev/faststats-java](https://github.com/faststats-dev/faststats-java).
> The public API, wire format and module layout are upstream's. The fork only adds the patches in
> [`patches/`](./patches) and a Maven build that publishes the artifacts.

> [!IMPORTANT]
> This is a breaking rewrite from the previous `0.x` reimplementation. The fork version follows upstream
> (currently `0.33.0.1`): the artifacts expose upstream's `dev.faststats` API. See [Differences from upstream](#differences-from-upstream).

## Modules

The modules mirror upstream, minus the mod-loader and onboarding modules, plus two fork-owned
modules that upstream keeps inside core:

| Module | Maven artifact | Target |
|---|---|---|
| `core` | `io.github.projectunified:faststats-core` | Java 8 |
| `config` | `io.github.projectunified:faststats-config` | Java 8 |
| `error-tracker` | `io.github.projectunified:faststats-error-tracker` | Java 8 |
| `feature-flag` | `io.github.projectunified:faststats-feature-flag` | Java 8 |
| `bukkit` | `io.github.projectunified:faststats-bukkit` | Java 8 |
| `bungeecord` | `io.github.projectunified:faststats-bungeecord` | Java 17 |
| `hytale` | `io.github.projectunified:faststats-hytale` | Java 25 |
| `minestom` | `io.github.projectunified:faststats-minestom` | Java 25 |
| `nukkit` | `io.github.projectunified:faststats-nukkit` | Java 17 |
| `sponge` | `io.github.projectunified:faststats-sponge` | Java 17 |
| `velocity` | `io.github.projectunified:faststats-velocity` | Java 21 |

`fabric` lives in a separate Gradle project, [`mod-platforms/fabric`](./mod-platforms/fabric),
because it uses Stonecutter to produce one artifact per Minecraft version. `forge`, `neoforge` and
`onboarding` are not included for now: their multi-loader builds were too memory-hungry to keep in
this repository.

## Patch-based build

No upstream `src/` tree is checked in. Module sources are generated from the pinned upstream commit in
[`upstream.ref`](./upstream.ref) and copied into the matching Maven modules, then patched with
[GitFile](https://github.com/ProjectUnified/GitFile): one patch per file under `patches/`, mirroring
the module tree (no series, no apply order). The pristine baseline is `.vanilla/` and the pinned
upstream checkout is `.upstream/` (both gitignored). The fork-owned `error-tracker`, `feature-flag`
and `mod-platforms/fabric` sources are checked in and are not regenerated. Version mapping: the fork
version is `<upstream-version>.<patch-number>` (currently `0.33.0.1`); `fork 0.33.0.1 == upstream 0.33.0 + patches/`.

Prereqs: `git`, JDK 25, Maven, and `gitfile`. On Arch Linux build the package from the GitFile
repository (`cd packaging/arch/gitfile && makepkg -si`); elsewhere install it with
`pipx install git+https://github.com/ProjectUnified/GitFile.git`.

```sh
gitfile sync && mvn clean install
```

`mvn install` (rather than `package`) puts `faststats-core` and `faststats-config` into the local
Maven repository, which the Fabric Gradle project resolves from `mavenLocal()`.

### Fabric mod platform

The Fabric project is checked in. It is a small fork-owned Stonecutter wrapper around the core API
(a single source tree, no compatibility layer) that produces one artifact per Minecraft version:

```sh
cd mod-platforms/fabric && ./gradlew build
```

This builds `io.github.projectunified:faststats-fabric:0.33.0.1+<minecraft>` for the Stonecutter
versions in [`stonecutter.properties.toml`](./mod-platforms/fabric/stonecutter.properties.toml).

| Task | Command |
|---|---|
| Edit code | `gitfile sync`, edit any module source, then `gitfile update` |
| Add a file | create it under a module `src/`, then `gitfile update` (writes a `/dev/null` patch) |
| Delete a file | `rm` it, then `gitfile update` (writes a deletion patch) |
| Drop a change | restore the file to its pristine content, then `gitfile update` (the patch is removed) |
| Verify from scratch | `gitfile sync && mvn clean install` |
| Export a combined patch for upstream | `gitfile export` (`faststats-fork.patch`) |
| Upgrade upstream | re-resolve `git ls-remote https://github.com/faststats-dev/faststats-java.git HEAD` into `upstream.ref`, then `gitfile sync`; conflicting files get merge markers/`.rej`, resolve and run `gitfile update` |

Rules: edit the working tree, never the `.vanilla` baseline. `gitfile update` requires a successful
`gitfile sync` (the `.vanilla/.gitfile-applied` marker). `gitfile sync` is the reset hatch and
discards unrecorded working-tree edits.

## Java 8 downgrade

Upstream compiles with Java 17+ language features (sealed types, records, pattern matching,
`java.net.http`). The fork keeps those sources and downgrades the `core`, `config`, `error-tracker`,
`feature-flag` and `bukkit` jars to class file 52 with
[JvmDowngrader](https://github.com/unimined/JvmDowngrader):

- `module-info.java` is dropped while copying the sources (Maven classpath build, no JPMS).
- The Maven `package` phase only runs JvmDowngrader's syntax/bytecode downgrade. The Java 8 modules
  use Java 8 APIs directly, so no Java API stubs are bundled in the jars. Records are converted to
  final classes automatically by `build-tools/record-converter` before the downgrade, so no record
  conversion is maintained in the patches.
- `bukkit` is compiled against the modern Paper API but still emits Java 8 bytecode; modern API
  calls are already guarded by upstream. Old Spigot servers can therefore load it.
- The two fork modules use the Java 8 downgrade too, so `ErrorTracker`/`FeatureFlag` support works
  on old servers.

Other modules keep upstream's target level (17/21/25).

## Installation

Artifacts are published to Maven Central under `io.github.projectunified`:

```xml
<dependency>
    <groupId>io.github.projectunified</groupId>
    <artifactId>faststats-core</artifactId>
    <version>0.33.0.1</version>
</dependency>

<!-- Optional features (separate from core) -->
<dependency>
    <groupId>io.github.projectunified</groupId>
    <artifactId>faststats-error-tracker</artifactId>
    <version>0.33.0.1</version>
</dependency>
<dependency>
    <groupId>io.github.projectunified</groupId>
    <artifactId>faststats-feature-flag</artifactId>
    <version>0.33.0.1</version>
</dependency>

<!-- Server platforms -->
<dependency>
    <groupId>io.github.projectunified</groupId>
    <artifactId>faststats-bukkit</artifactId>
    <version>0.33.0.1</version>
</dependency>
```

Gson is a required runtime dependency of `faststats-core` and is declared transitively.

## Quick Start

The API is upstream's API (`dev.faststats`). A Bukkit plugin:

```java
import dev.faststats.FastStatsContext;
import dev.faststats.Metrics;
import dev.faststats.bukkit.FastStatsBukkit;
import dev.faststats.errortracker.ErrorTracker;
import dev.faststats.errortracker.ErrorTrackerService;

public class MyPlugin extends JavaPlugin {
    private final FastStatsContext context = FastStatsBukkit.builder(this)
            .metrics(Metrics.Factory::create)
            .service(ErrorTrackerService.create(ErrorTracker.contextAware()))
            .build();

    @Override
    public void onEnable() {
        context.ready();
    }

    @Override
    public void onDisable() {
        context.shutdown();
    }
}
```

Custom metrics and flush callbacks:

```java
import dev.faststats.data.Metric;
import java.util.concurrent.atomic.AtomicInteger;

private final AtomicInteger gamesPlayed = new AtomicInteger();

private final FastStatsContext context = FastStatsBukkit.builder(this)
        .metrics(factory -> factory
                .addMetric(Metric.number("games_played", gamesPlayed::get))
                .onFlush(() -> gamesPlayed.set(0))
                .create())
        .build();
```

Feature flags (add `faststats-feature-flag` and attach it):

```java
import dev.faststats.featureflag.FeatureFlagService;

FastStatsContext context = FastStatsBukkit.builder(this)
        .metrics(Metrics.Factory::create)
        .service(FeatureFlagService.create()) // or create(attributes, ttl)
        .build();

context.service(FeatureFlagService.class).ifPresent(service -> {
    final var flag = service.define("new_awesome_feature", false);
    flag.fetch().thenAccept(enabled -> getLogger().info("new feature: " + enabled));
});
```

Manual error tracking (add `faststats-error-tracker`):

```java
context.service(ErrorTrackerService.class).ifPresent(service ->
        service.globalErrorTracker().trackError(throwable).handled(true));
```

`context.trackError(throwable)` reports an internal error to every attached service; it is used by
the platform integrations and is a no-op when no error tracker is attached.

Platform entry points: `FastStatsBukkit.builder(plugin)`, `FastStatsBungee.builder(plugin)`,
`FastStatsHytale.builder(plugin)`, `FastStatsMinestom.builder()`, `FastStatsNukkit.builder(plugin)`,
`FastStatsSponge.builder(...)`, `FastStatsVelocity.builder(...)`.

## Configuration

`SimpleConfig` reads `<platform data folder>/faststats/config.properties`, created on first start.
On the first start submission does not start until the server is restarted, so the file can be
reviewed before opting in. To opt out, set `enabled=false`.

| Key | Default | Description |
| --- | --- | --- |
| `enabled` | `true` | Master switch for all FastStats features |
| `submitMetrics` | `true` | Periodic metrics submission |
| `submitAdditionalMetrics` | `true` | Metrics added through the metrics factory |
| `submitErrors` | `true` | Error tracking |
| `debug` | `false` | Verbose logging |
| `serverId` | random UUID | Identifier submitted with every payload |
| `configVersion` | current version | Config format version, upgraded automatically |

System properties override the configuration without modifying it:

| Property | Description |
| --- | --- |
| `-Dfaststats.enabled=false` | Disables all FastStats features |
| `-Dfaststats.debug=true` | Forces debug logging |
| `-Dfaststats.first-run` | Set by the first start to defer submission until the next restart |
| `-Dfaststats.initial-delay=<seconds>` | Delay before the first submission, default `30` |
| `-Dfaststats.metrics-server=<url>` | Overrides the metrics server |
| `-Dfaststats.error-tracker-server=<url>` | Overrides the error submission endpoint |
| `-Dfaststats.flags-server=<url>` | Overrides the feature flag server |

## Differences from upstream

- **Maven Central**: published as `io.github.projectunified:faststats-*` from a Maven reactor.
  The module directories and public API are upstream's. Gson is a regular runtime dependency
  instead of upstream's `compileOnlyApi`.
- **Java 8 `core` / `config` / `bukkit`**: bytecode is downgraded by JvmDowngrader (see above).
- **No JPMS**: `module-info.java` is removed from the generated sources, so the jars are
  classpath-only.
- **Separate error tracker and feature flags**: the implementations are fork-owned modules
  (`faststats-error-tracker`, `faststats-feature-flag`) attached through the generic
  `FastStatsContext.Builder#service(...)` / `FastStatsContext#service(Class)` API. Core no longer
  contains those classes.
- **Mod platforms**: `fabric` is a separate Stonecutter Gradle project in
  `mod-platforms/fabric` with a small fork-owned wrapper (no compatibility layer); `forge`,
  `neoforge` and `onboarding` are not included for now.
- **Wire format**: endpoints, payloads, opt-out switches and the `project_name` field follow
  upstream exactly.

## License

MIT, see [LICENSE](./LICENSE). Upstream is also MIT.
