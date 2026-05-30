# FastStats

A client implementation for [FastStats](https://faststats.dev)

## Differences from [`faststats-java`](https://github.com/faststats-dev/faststats-java)

- **Modular Sub-modules**: Key features, serialization engines, network submitters, and server platforms are split into individual sub-modules. Users can import only what is required for their specific use cases instead of importing monolithic dependencies.
- **Java 8 Support**: The library targets Java 8 compatibility across all modules (except `faststats-httpclient` and some platforms that require newer Java versions).
- **Separated Error Tracker**: The error tracking mechanism is isolated as a standalone, optional feature module (`faststats-error-tracker`) instead of being bundled directly in core.

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
