package io.airlift.stats;

import static io.airlift.stats.TimeDistribution.MERGE_THRESHOLD_NANOS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import io.airlift.testing.TestingTicker;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class TestTimeDistribution {
    @Test
    public void testPartialMerging() {
        TestingTicker ticker = new TestingTicker();
        TimeDistribution distribution = new TimeDistribution(ticker);
        distribution.add(SECONDS.toNanos(1));
        distribution.add(SECONDS.toNanos(3));

        assertThat(distribution.getCount()).isEqualTo(0);
        assertThat(distribution.getAvg()).isNaN();

        // This will merge on next get or snapshot
        ticker.increment(MERGE_THRESHOLD_NANOS, TimeUnit.NANOSECONDS); // force a merge

        assertThat(distribution.getCount()).isEqualTo(2);
        assertThat(distribution.getAvg()).isEqualTo(2);

        distribution.add(SECONDS.toNanos(5));
        ticker.increment(MERGE_THRESHOLD_NANOS / 2, TimeUnit.NANOSECONDS); // time not lapsed enough to merge

        assertThat(distribution.getCount()).isEqualTo(2);
        ticker.increment(MERGE_THRESHOLD_NANOS / 2, TimeUnit.NANOSECONDS); // time lapsed enough to merge

        // This will merge partials again
        assertThat(distribution.snapshot().avg()).isEqualTo(3);

        assertThat(distribution.getCount()).isEqualTo(3);
        assertThat(distribution.getAvg()).isEqualTo(3);
    }
}
