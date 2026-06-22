package dev.drawethree.essdash.sse;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;

import java.util.Map;
import java.util.logging.Logger;

/**
 * Taps the server's Log4j2 root logger so every console line is mirrored to connected
 * dashboard clients over SSE. Paper/Spigot route all console output (including command
 * feedback) through Log4j2, so this captures the full live console.
 */
public class ConsoleStreamer extends AbstractAppender {

    private static final String APPENDER_NAME = "EssDashboardConsole";

    private final SseManager sse;
    private org.apache.logging.log4j.core.Logger rootLogger;
    private org.apache.logging.log4j.core.LoggerContext loggerContext;
    // Guards against feedback loops: broadcasting a line may itself emit log output
    // (e.g. from Jetty), which would re-enter append() and recurse infinitely.
    private final ThreadLocal<Boolean> broadcasting = ThreadLocal.withInitial(() -> false);

    private ConsoleStreamer(SseManager sse) {
        super(APPENDER_NAME, null, null, true, Property.EMPTY_ARRAY);
        this.sse = sse;
    }

    public static ConsoleStreamer attach(SseManager sse, Logger logger) {
        ConsoleStreamer streamer = new ConsoleStreamer(sse);
        streamer.start();

        // Preferred: register on the running LoggerContext's root LoggerConfig and refresh.
        // This is the canonical way to add a runtime appender and works even when the cast
        // used by the fallback path below isn't valid on a given server flavour.
        try {
            org.apache.logging.log4j.core.LoggerContext context =
                    (org.apache.logging.log4j.core.LoggerContext) LogManager.getContext(false);
            org.apache.logging.log4j.core.config.Configuration config = context.getConfiguration();
            config.getRootLogger().addAppender(streamer, null, null);
            context.updateLoggers();
            streamer.loggerContext = context;
            logger.info("Live console attached via LoggerContext (root level="
                    + config.getRootLogger().getLevel() + ").");
            return streamer;
        } catch (Throwable t) {
            logger.warning("LoggerContext attach failed, trying root-logger fallback: " + t.getMessage());
        }

        // Fallback: attach directly to the running root logger instance.
        try {
            org.apache.logging.log4j.core.Logger root =
                    (org.apache.logging.log4j.core.Logger) LogManager.getRootLogger();
            root.addAppender(streamer);
            streamer.rootLogger = root;
            logger.info("Live console attached to the root logger (level=" + root.getLevel() + ").");
        } catch (Throwable t) {
            logger.warning("Could not attach to the server console for the live feed; "
                    + "the console tab will stay empty: " + t.getMessage());
        }
        return streamer;
    }

    public void detach() {
        try {
            if (loggerContext != null) {
                loggerContext.getConfiguration().getRootLogger().removeAppender(APPENDER_NAME);
                loggerContext.updateLoggers();
            }
            if (rootLogger != null) rootLogger.removeAppender(this);
            stop();
        } catch (Throwable ignored) {}
    }

    @Override
    public void append(LogEvent event) {
        if (broadcasting.get()) return;
        try {
            broadcasting.set(true);
            String message = event.getMessage() != null ? event.getMessage().getFormattedMessage() : "";
            // Strip section-sign colour codes for clean web display.
            message = message.replaceAll("§[0-9a-fk-orA-FK-OR]", "");
            sse.broadcast("console-line", Map.of(
                    "level", event.getLevel() != null ? event.getLevel().name() : "INFO",
                    "logger", event.getLoggerName() != null ? event.getLoggerName() : "",
                    "message", message,
                    "timestamp", event.getTimeMillis()
            ));
        } catch (Throwable ignored) {
            // Never let logging failures cascade.
        }
    }
}
