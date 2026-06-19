package io.airlift.stats;

import io.airlift.stats.ExponentialHistogram.Buckets;
import io.airlift.stats.ExponentialHistogram.ExponentialHistogramSnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestStripedExponentialHistogram
{
    @Test
    public void testEmptySnapshot()
    {
        ExponentialHistogramSnapshot snapshot = new StripedExponentialHistogram(0, 10, 4).snapshot();

        assertThat(snapshot.scale()).isEqualTo(0);
        assertThat(snapshot.count()).isEqualTo(0);
        assertThat(snapshot.sum()).isEqualTo(0);
        assertThat(snapshot.min()).isNaN();
        assertThat(snapshot.max()).isNaN();
        assertThat(snapshot.zeroCount()).isEqualTo(0);
        assertThat(snapshot.positiveBuckets().isEmpty()).isTrue();
        assertThat(snapshot.negativeBuckets().isEmpty()).isTrue();
    }

    @Test
    public void testRecordsPositiveNegativeAndZeroValues()
    {
        StripedExponentialHistogram histogram = new StripedExponentialHistogram(0, 10, 4);

        histogram.record(0);
        histogram.record(1);
        histogram.record(1.5);
        histogram.record(2);
        histogram.record(3);
        histogram.record(-1);
        histogram.record(-2);

        ExponentialHistogramSnapshot snapshot = histogram.snapshot();

        assertThat(snapshot.scale()).isEqualTo(0);
        assertThat(snapshot.count()).isEqualTo(7);
        assertThat(snapshot.sum()).isEqualTo(4.5);
        assertThat(snapshot.min()).isEqualTo(-2);
        assertThat(snapshot.max()).isEqualTo(3);
        assertThat(snapshot.zeroCount()).isEqualTo(1);
        assertThat(snapshot.positiveBuckets()).isEqualTo(new Buckets(-1, new long[] {1, 2, 1}));
        assertThat(snapshot.negativeBuckets()).isEqualTo(new Buckets(-1, new long[] {1, 1}));
    }

    @Test
    public void testRecordsWeightedValues()
    {
        StripedExponentialHistogram histogram = new StripedExponentialHistogram(0, 10, 4);

        histogram.record(2, 3);
        histogram.record(0, 4);

        ExponentialHistogramSnapshot snapshot = histogram.snapshot();

        assertThat(snapshot.count()).isEqualTo(7);
        assertThat(snapshot.sum()).isEqualTo(6);
        assertThat(snapshot.zeroCount()).isEqualTo(4);
        assertThat(snapshot.positiveBuckets()).isEqualTo(new Buckets(0, new long[] {3}));
    }

    @Test
    public void testSnapshotDownscalesStripes()
    {
        StripedExponentialHistogram histogram = new StripedExponentialHistogram(1, 3, 4);
        histogram.record(1);
        histogram.record(4);

        ExponentialHistogramSnapshot snapshot = histogram.snapshot();
        assertThat(snapshot.scale()).isEqualTo(0);
        assertThat(snapshot.positiveBuckets()).isEqualTo(new Buckets(-1, new long[] {1, 0, 1}));

        histogram.reset();
        assertThat(histogram.snapshot().scale()).isEqualTo(1);
    }

    @Test
    public void testConcurrentRecording()
            throws Exception
    {
        StripedExponentialHistogram histogram = new StripedExponentialHistogram(0, 10, 4);
        ExecutorService executor = Executors.newFixedThreadPool(4);
        try {
            List<? extends Future<?>> futures = IntStream.range(0, 4)
                    .mapToObj(_ -> executor.submit(() -> {
                        for (int i = 0; i < 1_000; i++) {
                            histogram.record(1);
                        }
                    }))
                    .toList();

            for (Future<?> future : futures) {
                future.get();
            }
        }
        finally {
            executor.shutdownNow();
        }

        ExponentialHistogramSnapshot snapshot = histogram.snapshot();

        assertThat(snapshot.count()).isEqualTo(4_000);
        assertThat(snapshot.sum()).isEqualTo(4_000);
        assertThat(snapshot.positiveBuckets()).isEqualTo(new Buckets(-1, new long[] {4_000}));
    }

    @Test
    public void testRejectsInvalidConfiguration()
    {
        assertThatThrownBy(() -> new StripedExponentialHistogram(0, 10, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("stripes must be positive");
    }
}
