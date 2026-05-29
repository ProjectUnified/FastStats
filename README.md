# FastStats

A client implementation for [FastStats](https://faststats.dev)

## Modules

| Module         | Artifact               | Description                                                                                                               |
|----------------|------------------------|---------------------------------------------------------------------------------------------------------------------------|
| **core**       | `faststats-core`       | Core interfaces and classes: `Metrics`, `Platform`, `Config`, `Metric`, `JsonSerializer`, `HttpExecutor`, `TaskScheduler` |
| **gson**       | `faststats-gson`       | `JsonSerializer` implementation using [Google Gson](https://github.com/google/gson)                                       |
| **net**        | `faststats-net`        | `HttpExecutor` implementation using `java.net.HttpURLConnection`                                                          |
| **httpclient** | `faststats-httpclient` | `HttpExecutor` implementation using standard `java.net.http.HttpClient` (Requires Java 11+)                               |
| **bukkit**     | `faststats-bukkit`     | `Platform` implementation for Bukkit/Spigot/Paper servers                                                                 |
| **bom**        | `faststats-bom`        | Bill of Materials (BOM) to manage versions of all FastStats modules                                                        |

## Requirements

- Java 8 or higher (except for the `httpclient` module which requires Java 11 or higher)

## Installation

It is recommended to import `faststats-bom` in your `<dependencyManagement>` section to manage version configurations of FastStats modules:

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

<!-- HTTP executor (Java 8+) -->
<dependency>
    <groupId>io.github.projectunified</groupId>
    <artifactId>faststats-net</artifactId>
</dependency>

<!-- HTTP executor (Java 11+) -->
<dependency>
    <groupId>io.github.projectunified</groupId>
    <artifactId>faststats-httpclient</artifactId>
</dependency>

<!-- Platform -->
<dependency>
    <groupId>io.github.projectunified</groupId>
    <artifactId>faststats-bukkit</artifactId>
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
