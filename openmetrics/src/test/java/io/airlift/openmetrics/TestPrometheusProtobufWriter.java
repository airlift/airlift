/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.airlift.openmetrics;

import com.google.common.collect.ImmutableMap;
import io.airlift.openmetrics.types.CompositeMetric;
import io.airlift.openmetrics.types.Counter;
import io.airlift.openmetrics.types.ExponentialHistogramMetric;
import io.airlift.openmetrics.types.Gauge;
import io.airlift.openmetrics.types.Metric;
import io.airlift.openmetrics.types.Summary;
import io.airlift.stats.ExponentialHistogram.Buckets;
import io.airlift.stats.ExponentialHistogram.ExponentialHistogramSnapshot;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static io.airlift.openmetrics.MetricsUtils.groupMetricFamilies;
import static io.airlift.openmetrics.PrometheusProtobufWriter.writeMetricFamilies;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class TestPrometheusProtobufWriter
{
    @Test
    public void testCompositeMetric()
            throws Exception
    {
        Metric compositeMetric = new CompositeMetric(
                "memory",
                ImmutableMap.of(),
                "Memory",
                List.of(
                        new Gauge("memory_used", 7, ImmutableMap.of(), "Memory used"),
                        new Gauge("memory_max", 9, ImmutableMap.of(), "Memory max")));

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        writeMetricFamilies(output, groupMetricFamilies(List.of(compositeMetric)));

        ProtoReader stream = new ProtoReader(output.toByteArray());
        ProtoReader usedFamily = stream.readDelimitedMessage();
        assertThat(usedFamily.readString(1)).isEqualTo("memory_used");
        assertThat(usedFamily.readString(2)).isEqualTo("Memory used");
        assertThat(usedFamily.readUInt(3)).isEqualTo(1);
        assertThat(usedFamily.readMessage(4).readMessage(2).readDouble(1)).isEqualTo(7);

        ProtoReader maxFamily = stream.readDelimitedMessage();
        assertThat(maxFamily.readString(1)).isEqualTo("memory_max");
        assertThat(maxFamily.readString(2)).isEqualTo("Memory max");
        assertThat(maxFamily.readUInt(3)).isEqualTo(1);
        assertThat(maxFamily.readMessage(4).readMessage(2).readDouble(1)).isEqualTo(9);
        assertThat(stream.isAtEnd()).isTrue();
    }

    @Test
    public void testCompositeMetricMustBeFlattened()
    {
        Metric compositeMetric = new CompositeMetric(
                "memory",
                ImmutableMap.of(),
                "Memory",
                List.of(new Gauge("memory_used", 7, ImmutableMap.of(), "Memory used")));

        assertThatThrownBy(() -> writeMetricFamilies(
                new ByteArrayOutputStream(),
                Map.of(compositeMetric.metricName(), List.of(compositeMetric))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("CompositeMetric must be flattened before Prometheus protobuf encoding");
    }

    @Test
    public void testStandardMetricTypes()
            throws Exception
    {
        Metric counter = new Counter("requests", 17, ImmutableMap.of(), "Requests");
        Metric gauge = new Gauge("threads", 4.5, ImmutableMap.of(), "Threads");
        Metric summary = new Summary("latency", 3L, 9.0, null, ImmutableMap.of(0.5, 2.5), ImmutableMap.of(), "Latency");
        Map<String, List<Metric>> metricFamilies = new LinkedHashMap<>();
        metricFamilies.put(counter.metricName(), List.of(counter));
        metricFamilies.put(gauge.metricName(), List.of(gauge));
        metricFamilies.put(summary.metricName(), List.of(summary));

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        writeMetricFamilies(output, metricFamilies);
        ProtoReader stream = new ProtoReader(output.toByteArray());

        ProtoReader counterFamily = stream.readDelimitedMessage();
        assertThat(counterFamily.readString(1)).isEqualTo("requests");
        assertThat(counterFamily.readString(2)).isEqualTo("Requests");
        assertThat(counterFamily.readUInt(3)).isZero();
        ProtoReader counterSample = counterFamily.readMessage(4);
        assertThat(counterSample.readMessage(3).readDouble(1)).isEqualTo(17);

        ProtoReader gaugeFamily = stream.readDelimitedMessage();
        assertThat(gaugeFamily.readString(1)).isEqualTo("threads");
        assertThat(gaugeFamily.readString(2)).isEqualTo("Threads");
        assertThat(gaugeFamily.readUInt(3)).isEqualTo(1);
        ProtoReader gaugeSample = gaugeFamily.readMessage(4);
        assertThat(gaugeSample.readMessage(2).readDouble(1)).isEqualTo(4.5);

        ProtoReader summaryFamily = stream.readDelimitedMessage();
        assertThat(summaryFamily.readString(1)).isEqualTo("latency");
        assertThat(summaryFamily.readString(2)).isEqualTo("Latency");
        assertThat(summaryFamily.readUInt(3)).isEqualTo(2);
        ProtoReader summarySample = summaryFamily.readMessage(4);
        ProtoReader summaryValue = summarySample.readMessage(4);
        assertThat(summaryValue.readUInt(1)).isEqualTo(3);
        assertThat(summaryValue.readDouble(2)).isEqualTo(9);
        ProtoReader quantile = summaryValue.readMessage(3);
        assertThat(quantile.readDouble(1)).isEqualTo(0.5);
        assertThat(quantile.readDouble(2)).isEqualTo(2.5);
        assertThat(stream.isAtEnd()).isTrue();
    }

    @Test
    public void testExponentialHistogram()
            throws Exception
    {
        ExponentialHistogramSnapshot snapshot = new ExponentialHistogramSnapshot(
                8,
                7,
                11,
                -4,
                6,
                1,
                new Buckets(3, new long[] {2, 0, 1}),
                new Buckets(-2, new long[] {1, 2}));
        Metric metric = new ExponentialHistogramMetric(
                "request_time",
                snapshot,
                ImmutableMap.of("node", "test"),
                "Request time",
                "ns");

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        writeMetricFamilies(output, Map.of(metric.metricName(), List.of(metric)));

        ProtoReader stream = new ProtoReader(output.toByteArray());
        ProtoReader family = stream.readDelimitedMessage();
        assertThat(stream.isAtEnd()).isTrue();
        assertThat(family.readString(1)).isEqualTo("request_time");
        assertThat(family.readString(2)).isEqualTo("Request time");
        assertThat(family.readUInt(3)).isEqualTo(4);

        ProtoReader sample = family.readMessage(4);
        ProtoReader label = sample.readMessage(1);
        assertThat(label.readString(1)).isEqualTo("node");
        assertThat(label.readString(2)).isEqualTo("test");
        assertThat(label.isAtEnd()).isTrue();

        ProtoReader histogram = sample.readMessage(7);
        assertThat(sample.isAtEnd()).isTrue();
        assertThat(histogram.readUInt(1)).isEqualTo(7);
        assertThat(histogram.readDouble(2)).isEqualTo(11);
        assertThat(histogram.readSInt(5)).isEqualTo(8);
        assertThat(histogram.readDouble(6)).isZero();
        assertThat(histogram.readUInt(7)).isEqualTo(1);

        ProtoReader negativeSpan = histogram.readMessage(9);
        assertThat(negativeSpan.readSInt(1)).isEqualTo(-1);
        assertThat(negativeSpan.readUInt(2)).isEqualTo(2);
        assertThat(negativeSpan.isAtEnd()).isTrue();
        assertThat(histogram.readSInt(10)).isEqualTo(1);
        assertThat(histogram.readSInt(10)).isEqualTo(1);

        ProtoReader positiveSpan = histogram.readMessage(12);
        assertThat(positiveSpan.readSInt(1)).isEqualTo(4);
        assertThat(positiveSpan.readUInt(2)).isEqualTo(3);
        assertThat(positiveSpan.isAtEnd()).isTrue();
        assertThat(histogram.readSInt(13)).isEqualTo(2);
        assertThat(histogram.readSInt(13)).isEqualTo(-2);
        assertThat(histogram.readSInt(13)).isEqualTo(1);
        assertThat(histogram.isAtEnd()).isTrue();

        assertThat(family.readString(5)).isEqualTo("ns");
        assertThat(family.isAtEnd()).isTrue();
    }

    @Test
    public void testHistogramDownscalesToPrometheusMaximum()
    {
        ExponentialHistogramMetric metric = new ExponentialHistogramMetric(
                "distribution",
                new ExponentialHistogramSnapshot(
                        10,
                        7,
                        14,
                        1,
                        4,
                        1,
                        new Buckets(8, new long[] {1, 2, 3}),
                        new Buckets(0, new long[0])),
                ImmutableMap.of(),
                "help",
                null);

        assertThat(metric.snapshot().scale()).isEqualTo(8);
        assertThat(metric.snapshot().positiveBuckets().offset()).isEqualTo(2);
        assertThat(metric.snapshot().positiveBuckets().counts()).containsExactly(6);
        assertThat(metric.snapshot().count()).isEqualTo(7);
        assertThat(metric.snapshot().sum()).isEqualTo(14);
    }

    @Test
    public void testEmptyHistogramHasNativeHistogramMarker()
            throws Exception
    {
        ExponentialHistogramSnapshot snapshot = new ExponentialHistogramSnapshot(
                8,
                0,
                0,
                Double.NaN,
                Double.NaN,
                0,
                new Buckets(0, new long[0]),
                new Buckets(0, new long[0]));
        Metric metric = new ExponentialHistogramMetric("empty", snapshot, ImmutableMap.of(), null, null);

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        writeMetricFamilies(output, Map.of(metric.metricName(), List.of(metric)));

        ProtoReader family = new ProtoReader(output.toByteArray()).readDelimitedMessage();
        family.readString(1);
        family.readUInt(3);
        ProtoReader sample = family.readMessage(4);
        ProtoReader histogram = sample.readMessage(7);
        histogram.readUInt(1);
        histogram.readDouble(2);
        histogram.readSInt(5);
        histogram.readDouble(6);
        histogram.readUInt(7);
        assertThat(histogram.readMessage(12).isAtEnd()).isTrue();
        assertThat(histogram.isAtEnd()).isTrue();
    }

    private static final class ProtoReader
    {
        private final byte[] data;
        private int position;

        private ProtoReader(byte[] data)
        {
            this.data = data;
        }

        public ProtoReader readDelimitedMessage()
        {
            return readBytes((int) readVarint());
        }

        public ProtoReader readMessage(int field)
        {
            readTag(field, 2);
            return readBytes((int) readVarint());
        }

        public String readString(int field)
        {
            ProtoReader value = readMessage(field);
            value.position = value.data.length;
            return new String(value.data, StandardCharsets.UTF_8);
        }

        public long readUInt(int field)
        {
            readTag(field, 0);
            return readVarint();
        }

        public long readSInt(int field)
        {
            long value = readUInt(field);
            return (value >>> 1) ^ -(value & 1);
        }

        public double readDouble(int field)
        {
            readTag(field, 1);
            long bits = 0;
            for (int index = 0; index < Long.BYTES; index++) {
                bits |= (long) (data[position++] & 0xFF) << (index * Byte.SIZE);
            }
            return Double.longBitsToDouble(bits);
        }

        public boolean isAtEnd()
        {
            return position == data.length;
        }

        private void readTag(int field, int wireType)
        {
            assertThat(readVarint()).isEqualTo(((long) field << 3) | wireType);
        }

        private ProtoReader readBytes(int length)
        {
            byte[] value = Arrays.copyOfRange(data, position, position + length);
            position += length;
            return new ProtoReader(value);
        }

        private long readVarint()
        {
            long value = 0;
            int shift = 0;
            while (true) {
                int next = data[position++] & 0xFF;
                value |= (long) (next & 0x7F) << shift;
                if ((next & 0x80) == 0) {
                    return value;
                }
                shift += 7;
            }
        }
    }
}
