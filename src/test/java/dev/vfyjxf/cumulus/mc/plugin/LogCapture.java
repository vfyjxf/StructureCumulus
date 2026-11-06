package dev.vfyjxf.cumulus.mc.plugin;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;

import java.util.ArrayList;
import java.util.List;

/** Captures the formatted messages a named logger emits while attached. */
public final class LogCapture implements AutoCloseable {

    private final Logger logger;
    private final CapturingAppender appender;

    private LogCapture(Logger logger, CapturingAppender appender) {
        this.logger = logger;
        this.appender = appender;
    }

    public static LogCapture attach(Class<?> loggerOwner) {
        return attach(loggerOwner.getName());
    }

    public static LogCapture attach(String loggerName) {
        LoggerContext context = (LoggerContext) LogManager.getContext(false);
        Logger logger = context.getLogger(loggerName);
        CapturingAppender appender = new CapturingAppender();
        appender.start();
        logger.addAppender(appender);
        return new LogCapture(logger, appender);
    }

    public List<String> messages() {
        return appender.messages;
    }

    @Override
    public void close() {
        logger.removeAppender(appender);
        appender.stop();
    }

    private static final class CapturingAppender extends AbstractAppender {

        private final List<String> messages = new ArrayList<>();

        CapturingAppender() {
            super("log-capture", null, null, true, Property.EMPTY_ARRAY);
        }

        @Override
        public void append(LogEvent event) {
            messages.add(event.getMessage().getFormattedMessage());
        }
    }
}
