package io.github.projectunified.faststats.paper;

import com.destroystokyo.paper.event.server.ServerExceptionEvent;
import com.destroystokyo.paper.exception.ServerPluginException;
import io.github.projectunified.faststats.core.Feature;
import io.github.projectunified.faststats.errortracker.ErrorTracker;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

/**
 * A feature that registers an event listener on Paper servers to track uncaught server plugin exceptions
 * responsible by the registered plugin, reporting them to the core {@link ErrorTracker} feature.
 */
public class PaperErrorTracker extends Feature implements Listener {
    private final Plugin plugin;

    /**
     * Constructs a new {@link PaperErrorTracker} for the specified plugin.
     *
     * @param plugin the plugin to track exceptions for
     */
    public PaperErrorTracker(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onStart() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    /**
     * Handles server exceptions to track plugin errors.
     *
     * @param event the server exception event
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onServerException(final ServerExceptionEvent event) {
        if (!(event.getException() instanceof ServerPluginException)) {
            return;
        }
        ServerPluginException exception = (ServerPluginException) event.getException();
        if (!exception.getResponsiblePlugin().equals(plugin)) {
            return;
        }
        Throwable report = exception.getCause() != null ? exception.getCause() : exception;
        getFeature(ErrorTracker.class).ifPresent(tracker -> tracker.trackError(report, false));
    }
}
