package io.airlift.openmetrics;

import org.junit.jupiter.api.Test;

import static io.airlift.openmetrics.MetricsResource.sanitizeMetricName;
import static org.assertj.core.api.Assertions.assertThat;

public class TestMetricsResource
{
    @Test
    public void testSanitizeMetricName()
    {
        String original = "com.zaxxer.hikari:type=Pool (HikariPool-1),ThreadsAwaitingConnection";
        String expected = "com_zaxxer_hikari_type_Pool_HikariPool_1_ThreadsAwaitingConnection";
        assertThat(sanitizeMetricName(original)).isEqualTo(expected);
    }
}
