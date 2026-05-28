# FastStats

A client implementation for [FastStats](https://faststats.dev)

## Modules

| Module     | Artifact           | Description                                                                                                               |
|------------|--------------------|---------------------------------------------------------------------------------------------------------------------------|
| **core**   | `faststats-core`   | Core interfaces and classes: `Metrics`, `Platform`, `Config`, `Metric`, `JsonSerializer`, `HttpExecutor`, `TaskScheduler` |
| **gson**   | `faststats-gson`   | `JsonSerializer` implementation using [Google Gson](https://github.com/google/gson)                                       |
| **net**    | `faststats-net`    | `HttpExecutor` implementation using `java.net.HttpURLConnection`                                                          |
| **bukkit** | `faststats-bukkit` | `Platform` implementation for Bukkit/Spigot/Paper servers                                                                 |

## Requirements

- Java 8 or higher

## Installation

Add the modules you need to your `pom.xml`:

```xml
<dependency>
    <groupId>io.github.projectunified</groupId>
    <artifactId>faststats-core</artifactId>
    <version>VERSION</version>
</dependency>

<!-- JSON serialization -->
<dependency>
    <groupId>io.github.projectunified</groupId>
    <artifactId>faststats-gson</artifactId>
    <version>VERSION</version>
</dependency>

<!-- HTTP executor -->
<dependency>
    <groupId>io.github.projectunified</groupId>
    <artifactId>faststats-net</artifactId>
    <version>VERSION</version>
</dependency>

<!-- Platform -->
<dependency>
    <groupId>io.github.projectunified</groupId>
    <artifactId>faststats-bukkit</artifactId>
    <version>VERSION</version>
</dependency>
```

## Quick Start

### Bukkit Plugin

```java
public class MyPlugin extends JavaPlugin {
    private Metrics metrics;

    @Override
    public void onEnable() {
        metrics = Metrics.builder()
            .platform(new BukkitPlatform(this))
            .serializer(new GsonSerializer())
            .httpExecutor(new NetHttpExecutor("YOUR_TOKEN"))
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
