package io.airlift.mcp.session;

import com.google.common.collect.ImmutableSet;

import java.time.Duration;
import java.util.Set;

import static com.google.common.base.Preconditions.checkArgument;

public record SessionMetadata(Set<NotificationType> supportedNotifications, Duration sessionUpdateCadence, Duration maxEventsLoopDuration)
{
    public static final SessionMetadata DEFAULT = new SessionMetadata(
            ImmutableSet.copyOf(NotificationType.values()),
            Duration.ofSeconds(10),
            Duration.ofMinutes(5));

    public SessionMetadata
    {
        supportedNotifications = ImmutableSet.copyOf(supportedNotifications);
        checkArgument(sessionUpdateCadence.isPositive(), "sessionUpdateCadence must be positive");
        checkArgument(maxEventsLoopDuration.isPositive(), "maxEventsLoopDuration must be positive");

        checkArgument(maxEventsLoopDuration.compareTo(sessionUpdateCadence) > 0, "maxEventsLoopDuration must be greater than sessionUpdateCadence");
    }
}
