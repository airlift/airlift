package io.airlift.event.client;

import ch.qos.logback.core.ContextBase;
import ch.qos.logback.core.Layout;
import ch.qos.logback.core.rolling.RollingFileAppender;
import ch.qos.logback.core.rolling.SizeAndTimeBasedFNATP;
import ch.qos.logback.core.rolling.TimeBasedRollingPolicy;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Throwables;

import javax.inject.Inject;
import java.io.IOException;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public class LoggingEventClient
        extends AbstractEventClient
{
    private final boolean isLogEnabled;
    private String eventType = null;
    private RollingFileAppender fileAppender = null;

    @Inject
    public LoggingEventClient(EventConfig eventConfig)
    {
        isLogEnabled = eventConfig.isLogEnabled();

        if (!isLogEnabled) {
            return;
        }

        checkNotNull(eventConfig.getEventTypeToLog(), "event type to log is null");
        checkNotNull(eventConfig.getLogLayoutClass(), "event log layout class is null");

        this.eventType = eventConfig.getEventTypeToLog();
        String logPath = eventConfig.getLogPath();
        Layout logLayout = null;
        try {
            Class<?> layoutClass = Class.forName(eventConfig.getLogLayoutClass());
            checkArgument(Layout.class.isAssignableFrom(layoutClass), "event log layout class must be a subclass of Layout");
            logLayout = (Layout<?>) layoutClass.newInstance();
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException e) {
            Throwables.propagate(e);
        }

        ContextBase context = new ContextBase();
        fileAppender = new RollingFileAppender<>();
        SizeAndTimeBasedFNATP triggeringPolicy = new SizeAndTimeBasedFNATP<>();
        TimeBasedRollingPolicy rollingPolicy = new TimeBasedRollingPolicy<>();

        rollingPolicy.setContext(context);
        rollingPolicy.setFileNamePattern(logPath + "-%d{yyyy-MM-dd}.%i.log.gz");
        rollingPolicy.setMaxHistory(eventConfig.getLogHistory());
        rollingPolicy.setTimeBasedFileNamingAndTriggeringPolicy(triggeringPolicy);
        rollingPolicy.setParent(fileAppender);
        rollingPolicy.start();

        triggeringPolicy.setContext(context);
        triggeringPolicy.setTimeBasedRollingPolicy(rollingPolicy);
        triggeringPolicy.setMaxFileSize(String.valueOf(eventConfig.getLogMaxFileSize()));
        triggeringPolicy.start();

        fileAppender.setContext(context);
        fileAppender.setFile(logPath);
        fileAppender.setAppend(true);
        fileAppender.setLayout(logLayout);
        fileAppender.setRollingPolicy(rollingPolicy);
        fileAppender.start();
    }

    @Override
    protected <T> void postEvent(T event)
            throws IOException
    {
        if (!isLogEnabled) {
            return;
        }

        EventType eventTypeAnnotation = event.getClass().getAnnotation(EventType.class);
        if (eventTypeAnnotation != null && eventTypeAnnotation.value().equals(eventType)) {
            fileAppender.doAppend(event);
        }
    }

    @VisibleForTesting
    void setFileAppender(RollingFileAppender appender)
    {
        this.fileAppender = appender;
    }
}
