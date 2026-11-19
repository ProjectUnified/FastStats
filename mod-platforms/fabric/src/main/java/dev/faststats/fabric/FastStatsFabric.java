package dev.faststats.fabric;

import dev.faststats.FastStatsContext;
import dev.faststats.SimpleContext;
import dev.faststats.config.SimpleConfig;
import dev.faststats.internal.PlatformLoggerFactory;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Contract;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.Optional;

/**
 * FastStats for Fabric and Quilt mods.
 * <p>
 * Build the context when the server starts and shut it down when it stops:
 * <pre>{@code
 * private final FastStatsContext context = FastStatsFabric.builder(server, "example-mod")
 *         .metrics(Metrics.Factory::create)
 *         .service(ErrorTrackerService.create(ErrorTracker.contextAware()))
 *         .build();
 * }</pre>
 * The context is made ready and submission starts automatically, except on the first run.
 */
public final class FastStatsFabric {
    private FastStatsFabric() {
    }

    /**
     * Creates a new context builder for the given server and mod.
     * <p>
     * The shared config is read from {@code config/faststats/config.properties}.
     *
     * @param server the running Minecraft server
     * @param modId  the mod id
     * @return a new context builder
     * @throws IllegalArgumentException if the mod is not loaded
     */
    @Contract(value = "_, _ -> new", mutates = "io")
    public static FastStatsContext.Builder builder(final MinecraftServer server, final String modId) throws IllegalArgumentException {
        final FabricLoader loader = FabricLoader.getInstance();
        final ModContainer mod = loader.getModContainer(modId).orElseThrow(() ->
                new IllegalArgumentException("Mod not found: " + modId));
        final Logger logger = LoggerFactory.getLogger(modId);
        final PlatformLoggerFactory loggerFactory = new PlatformLoggerFactory((level, throwable, message) -> {
            switch (level) {
                case INFO -> {
                    if (throwable == null) logger.info(message);
                    else logger.info(message, throwable);
                }
                case WARN -> {
                    if (throwable == null) logger.warn(message);
                    else logger.warn(message, throwable);
                }
                case ERROR -> {
                    if (throwable == null) logger.error(message);
                    else logger.error(message, throwable);
                }
            }
        });
        final SimpleConfig config = SimpleConfig.read(configPath(loader), loggerFactory);

        return new SimpleContext.Builder("fabric")
                .loggerFactory(loggerFactory)
                .config(config)
                .projectName(modId)
                .tokenResource(FastStatsFabric.class.getClassLoader(), modId)
                .defaultMetrics((context, metrics) -> {
                    metrics.addProperty("game_version", server.getServerVersion());
                    metrics.addProperty("online_mode", server.usesAuthentication());
                    metrics.addProperty("player_count", server.getPlayerCount());
                    metrics.addProperty("platform_version", version("fabricloader").orElse("unknown"));
                    metrics.addProperty("plugin_version", mod.getMetadata().getVersion().getFriendlyString());
                    metrics.addProperty("server_type", "Fabric");
                })
                .onBuild((context, builder) -> {
                    context.ready();
                    if (config.preSubmissionStart(loggerFactory)) ((SimpleContext) context).startSubmission();
                });
    }

    private static Optional<String> version(final String modId) {
        return FabricLoader.getInstance()
                .getModContainer(modId)
                .map(container -> container.getMetadata().getVersion().getFriendlyString());
    }

    private static Path configPath(final FabricLoader loader) {
        return loader.getConfigDir().resolve("faststats").resolve("config.properties");
    }
}
