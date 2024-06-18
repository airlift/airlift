package io.airlift.openmetrics;

import org.junit.jupiter.api.Test;

import static io.airlift.openmetrics.MetricsResource.sanitizeMetricName;
import static org.assertj.core.api.Assertions.assertThat;

public class TestMetricsResource
{
    @Test
    public void testSanitizeMetricName()
    {
        verifySanitized(
                "com.zaxxer.hikari:type=Pool (HikariPool-1),ThreadsAwaitingConnection",
                "com_zaxxer_hikari_type_Pool__HikariPool_1__ThreadsAwaitingConnection");

        verifySanitized(
                "strange**%^AA!@#)/$ł",
                "strange____AA_______");
    }

    private static void verifySanitized(String original, String expected)
    {
        assertThat(sanitizeMetricName(original)).isEqualTo(expected);
    }
}
