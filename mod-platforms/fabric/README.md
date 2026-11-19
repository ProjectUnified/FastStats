# FastStats Fabric/Quilt

Stonecutter project producing the FastStats Fabric/Quilt platform for several Minecraft
versions. The source is a single fork-owned wrapper around the FastStats core — there is no
compatibility layer, so the same `FastStatsFabric` class is compiled for every version.

## Build

Install the Maven artifacts first so Gradle can resolve `faststats-core` and `faststats-config`
from `mavenLocal()`:

```sh
mvn -B -DskipTests install
cd mod-platforms/fabric
./gradlew build
```

The Stonecutter versions are configured in [`stonecutter.properties.toml`](./stonecutter.properties.toml)
and [`settings.gradle.kts`](./settings.gradle.kts). Artifacts are written to `versions/<minecraft>/build/libs/`:

```text
io.github.projectunified:faststats-fabric:0.33.0+1.21.11
io.github.projectunified:faststats-fabric:0.33.0+26.1.2
io.github.projectunified:faststats-fabric:0.33.0+26.2
io.github.projectunified:faststats-fabric:0.33.0+26.3
```

## Usage

```java
private final FastStatsContext context = FastStatsFabric.builder(server, "example-mod")
        .metrics(Metrics.Factory::create)
        .service(ErrorTrackerService.create(ErrorTracker.contextAware()))
        .build();
```

Build the context when the server starts. It is made ready and submission starts automatically,
except on the first run. Call `context.shutdown()` when the server stops.
