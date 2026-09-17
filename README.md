# FastStats

A client implementation for [FastStats](https://faststats.dev)

## Differences from [`faststats-java`](https://github.com/faststats-dev/faststats-java)

- **Modular Sub-modules**: Key features, serialization engines, network submitters, and server platforms are split into individual sub-modules. Users can import only what is required for their specific use cases instead of importing monolithic dependencies.
- **Java 8 Support**: The library targets Java 8 compatibility across all modules (except `faststats-httpclient` and some platforms that require newer Java versions).
- **Separated Error Tracker**: The error tracking mechanism is isolated as a standalone, optional feature module (`faststats-error-tracker`) instead of being bundled directly in core.
- **Same wire format**: Endpoints, payloads and opt-out switches follow `faststats-java`, including the `project_name` field, the feature flag request bodies, and the error reporting limits and redactions.

## Installation

It is recommended to import `faststats-bom` in your `<dependencyManagement>` section to manage version configurations of
FastStats modules:

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>io.github.projectunified</groupId>
            <artifactId>faststats-bom</artifactId>
            <version>VERSION</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

Then, add the modules you need to your `pom.xml` without specifying versions:

```xml
<dependency>
    <groupId>io.github.projectunified</groupId>
    <artifactId>faststats-core</artifactId>
</dependency>

<!-- JSON serialization -->
<dependency>
    <groupId>io.github.projectunified</groupId>
    <artifactId>faststats-gson</artifactId>
</dependency>

<!-- Submitter (Java 8+) -->
<dependency>
    <groupId>io.github.projectunified</groupId>
    <artifactId>faststats-net</artifactId>
</dependency>

<!-- Submitter (Java 11+) -->
<dependency>
    <groupId>io.github.projectunified</groupId>
    <artifactId>faststats-httpclient</artifactId>
</dependency>

<!-- Error Tracker Feature -->
<dependency>
    <groupId>io.github.projectunified</groupId>
    <artifactId>faststats-error-tracker</artifactId>
</dependency>

<!-- Feature Flag Feature -->
<dependency>
    <groupId>io.github.projectunified</groupId>
    <artifactId>faststats-feature-flag</artifactId>
</dependency>

<!-- Platform -->
<dependency>
    <groupId>io.github.projectunified</groupId>
    <artifactId>faststats-bukkit</artifactId>
</dependency>
```

## Quick Start

### 1. Basic Telemetry
The core library allows tracking standard metrics periodically. Below is a basic setup for a Bukkit plugin:

```java
public class MyPlugin extends JavaPlugin {
    private Metrics metrics;

    @Override
    public void onEnable() {
        metrics = Metrics.builder()
                .platform(new BukkitPlatform(this))
                .serializer(new GsonSerializer())
                .submitter(new NetSubmitter("YOUR_TOKEN"))
                .addMetric(Metric.string("my_feature", () -> "enabled"))
                .build();
        metrics.start();
    }

    @Override
    public void onDisable() {
        metrics.shutdown();
    }
}
```

### 2. Error Tracking Feature (`ErrorTracker`)
To track uncaught exceptions and manually submit handled errors, include the `faststats-error-tracker` module and register the `ErrorTracker` feature. It automatically redacts sensitive information (like user homes, Discord webhooks, IPs, etc.) and allows ignoring specific error types or message patterns.

```java
import io.github.projectunified.faststats.errortracker.ErrorTracker;

// Inside onEnable:
ErrorTracker errorTracker = ErrorTracker.contextAware()
        .ignoreError(NullPointerException.class)
        .ignoreError("Some pattern to ignore")
        .anonymize("my-sensitive-regex", "[hidden]");

metrics = Metrics.builder()
        .platform(new BukkitPlatform(this))
        .serializer(new GsonSerializer())
        .submitter(new NetSubmitter("YOUR_TOKEN"))
        .addFeature(errorTracker)
        .build();
metrics.start();

// Tracking handled errors manually anywhere in your code:
try {
    // some code
} catch (Exception e) {
    metrics.getFeature(ErrorTracker.class).ifPresent(tracker -> tracker.trackError(e));
}
```

### 3. Paper Exception Tracking Feature (`PaperErrorTracker`)
If you are running on Paper server software, you can include the `faststats-paper` module to automatically intercept server plugin exceptions and forward them to the `ErrorTracker`.

```java
import io.github.projectunified.faststats.errortracker.ErrorTracker;
import io.github.projectunified.faststats.paper.PaperErrorTracker;

// Inside onEnable:
metrics = Metrics.builder()
        .platform(new BukkitPlatform(this))
        .serializer(new GsonSerializer())
        .submitter(new NetSubmitter("YOUR_TOKEN"))
        .addFeature(ErrorTracker.contextAware())
        .addFeature(new PaperErrorTracker(this))
        .build();
metrics.start();
```

### 4. Feature Flag Feature (`FeatureFlagManager`)
To define dynamic remote feature flags, opt-in/opt-out status, and manage values with cache TTL, include the `faststats-feature-flag` module and register the `FeatureFlagManager` feature:

```java
import io.github.projectunified.faststats.featureflag.FeatureFlag;
import io.github.projectunified.faststats.featureflag.FeatureFlagManager;

// Inside onEnable:
FeatureFlagManager flagManager = new FeatureFlagManager()
        .ttl(Duration.ofMinutes(5)); // Custom TTL for cache

// Define flags (Boolean, String, or Number)
FeatureFlag<Boolean> newFeatureFlag = flagManager.define("new_awesome_feature", false);

metrics = Metrics.builder()
        .platform(new BukkitPlatform(this))
        .serializer(new GsonSerializer())
        .submitter(new NetSubmitter("YOUR_TOKEN"))
        .addFeature(flagManager)
        .build();
metrics.start();

// Fetch feature flag asynchronously
newFeatureFlag.fetch().thenAccept(enabled -> {
    if (enabled) {
        getLogger().info("Launching the new awesome feature!");
    } else {
        getLogger().info("New feature is disabled.");
    }
});

// To opt-in or opt-out:
newFeatureFlag.optIn().thenAccept(updatedVal -> {
    getLogger().info("Successfully opted in. Updated value: " + updatedVal);
});
```

### 5. Flush Callbacks

`onFlush` runs after the metrics payload has been accepted by the server, which makes it suitable for
resetting counters that accumulate between submissions:

```java
AtomicInteger gamesPlayed = new AtomicInteger();

metrics = Metrics.builder()
        .platform(new BukkitPlatform(this))
        .serializer(new GsonSerializer())
        .submitter(new NetSubmitter("YOUR_TOKEN"))
        .addMetric(Metric.number("games_played", gamesPlayed::get))
        .onFlush(() -> gamesPlayed.set(0))
        .build();
```

## Configuration

FastStats reads `<platform data folder>/faststats/config.properties`, which is created on the first start. On that
first start submission stays disabled until the server is restarted, so the file can be reviewed before opting in.

| Key | Default | Description |
| --- | --- | --- |
| `enabled` | `true` | Master switch for all FastStats features |
| `submitMetrics` | `true` | Periodic metrics submission |
| `submitAdditionalMetrics` | `true` | Submission of metrics added through `Metrics.Builder#addMetric` |
| `submitErrors` | `true` | Error tracking, only used by the `faststats-error-tracker` module |
| `debug` | `false` | Verbose logging |
| `serverId` | random UUID | Identifier submitted with every payload |
| `project-name` | platform project name | Overrides the `project_name` field of error reports |

System properties override the configuration file without modifying it:

| Property | Description |
| --- | --- |
| `-Dfaststats.enabled=false` | Disables all FastStats features, including metrics and error tracking |
| `-Dfaststats.debug=true` | Forces debug logging |
| `-Dfaststats.initial-delay=<seconds>` | Delay before the first submission, default `30` |
| `-Dfaststats.flags-server=<url>` | Overrides the feature flag server |
| `-Dfaststats.error-tracker-server=<path or url>` | Overrides the error submission endpoint |
| `-Dfaststats.message-length=<chars>` | Error message truncation length, default `1000` |
| `-Dfaststats.stack-trace-length=<chars>` | Stack frame truncation length, default `300` |
| `-Dfaststats.stack-trace-limit=<frames>` | Stack frames kept per throwable, default `30` |

Custom platform implementations must implement `Platform#getProjectName()`. The returned name is submitted as
`project_name` alongside the metrics, which is how the service attributes submissions to a project.
