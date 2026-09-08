package io.airlift.mcp.client;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import io.airlift.mcp.McpClientException;
import io.airlift.mcp.client.settings.LoggingConsumer;
import io.airlift.mcp.client.settings.ProgressConsumer;
import io.airlift.mcp.model.LoggingMessageNotification;
import io.airlift.mcp.model.ProgressNotification;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static io.airlift.mcp.client.McpMapper.requireLoggingMessageNotification;
import static io.airlift.mcp.client.McpMapper.requireProgressNotification;
import static io.airlift.mcp.model.Constants.NOTIFICATION_MESSAGE;
import static io.airlift.mcp.model.Constants.NOTIFICATION_PROGRESS;
import static io.airlift.mcp.model.Constants.NOTIFICATION_TOOLS_LIST_CHANGED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * These consumers run on the thread reading the notification stream, so a notification they cannot read has to be
 * skipped rather than thrown out of - throwing there ends the subscription and hides every notification after it.
 */
public class TestNotificationConsumers
{
    @Test
    public void testLoggingNotificationIsDelivered()
    {
        List<LoggingMessageNotification> received = new ArrayList<>();
        LoggingConsumer consumer = received::add;

        consumer.accept(null, NOTIFICATION_MESSAGE, Optional.of(ImmutableMap.of("level", "debug", "data", "hello")));

        assertThat(received).hasSize(1);
        assertThat(received.getFirst().data()).contains("hello");
    }

    @Test
    public void testUnreadableLoggingNotificationIsSkipped()
    {
        List<LoggingMessageNotification> received = new ArrayList<>();
        LoggingConsumer consumer = received::add;

        consumer.accept(null, NOTIFICATION_MESSAGE, Optional.empty());
        consumer.accept(null, NOTIFICATION_MESSAGE, Optional.of(ImmutableList.of("not a notification")));

        assertThat(received).isEmpty();
    }

    @Test
    public void testProgressNotificationIsDelivered()
    {
        List<ProgressNotification> received = new ArrayList<>();
        ProgressConsumer consumer = received::add;

        consumer.accept(null, NOTIFICATION_PROGRESS, Optional.of(ImmutableMap.of("message", "50%", "progress", 50)));

        assertThat(received).hasSize(1);
        assertThat(received.getFirst().message()).isEqualTo("50%");
    }

    @Test
    public void testUnreadableProgressNotificationIsSkipped()
    {
        List<ProgressNotification> received = new ArrayList<>();
        ProgressConsumer consumer = received::add;

        consumer.accept(null, NOTIFICATION_PROGRESS, Optional.empty());
        consumer.accept(null, NOTIFICATION_PROGRESS, Optional.of(ImmutableList.of("not a notification")));

        assertThat(received).isEmpty();
    }

    @Test
    public void testOtherNotificationsAreIgnored()
    {
        List<LoggingMessageNotification> logged = new ArrayList<>();
        List<ProgressNotification> progressed = new ArrayList<>();

        LoggingConsumer loggingConsumer = logged::add;
        ProgressConsumer progressConsumer = progressed::add;

        // each is a view of one notification - composing them must not cross the wires
        loggingConsumer.accept(null, NOTIFICATION_PROGRESS, Optional.of(ImmutableMap.of("message", "50%")));
        progressConsumer.accept(null, NOTIFICATION_MESSAGE, Optional.of(ImmutableMap.of("level", "debug")));
        loggingConsumer.andThen(progressConsumer).accept(null, NOTIFICATION_TOOLS_LIST_CHANGED, Optional.empty());

        assertThat(logged).isEmpty();
        assertThat(progressed).isEmpty();
    }

    @Test
    public void testTheRequireFormsStillThrowForAbsentParams()
    {
        // the require* forms are the caller-facing pair and keep throwing, as documented
        assertThatThrownBy(() -> requireLoggingMessageNotification(Optional.empty()))
                .isInstanceOf(McpClientException.class);
        assertThatThrownBy(() -> requireProgressNotification(Optional.empty()))
                .isInstanceOf(McpClientException.class);
    }
}
