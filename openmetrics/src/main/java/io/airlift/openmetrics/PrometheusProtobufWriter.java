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

import io.airlift.openmetrics.types.BigCounter;
import io.airlift.openmetrics.types.CompositeMetric;
import io.airlift.openmetrics.types.Counter;
import io.airlift.openmetrics.types.ExponentialHistogramMetric;
import io.airlift.openmetrics.types.Gauge;
import io.airlift.openmetrics.types.Info;
import io.airlift.openmetrics.types.Metric;
import io.airlift.openmetrics.types.Summary;
import io.airlift.stats.ExponentialHistogram.Buckets;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.List;
import java.util.Map;

import static java.nio.ByteOrder.LITTLE_ENDIAN;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Comparator.comparing;

final class PrometheusProtobufWriter
{
    private static final int METRIC_TYPE_COUNTER = 0;
    private static final int METRIC_TYPE_GAUGE = 1;
    private static final int METRIC_TYPE_SUMMARY = 2;
    private static final int METRIC_TYPE_HISTOGRAM = 4;

    private PrometheusProtobufWriter() {}

    public static void writeMetricFamilies(OutputStream output, Map<String, List<Metric>> metricFamilies)
            throws IOException
    {
        for (List<Metric> metricFamily : metricFamilies.values()) {
            ProtoOutput family = writeMetricFamily(metricFamily);
            writeVarint(output, family.size());
            family.writeTo(output);
        }
    }

    private static ProtoOutput writeMetricFamily(List<Metric> metrics)
    {
        Metric first = metrics.getFirst();
        ProtoOutput family = new ProtoOutput();
        family.writeString(1, first.metricName());
        if (first.help() != null && !first.help().isEmpty()) {
            family.writeString(2, first.help());
        }
        family.writeUInt(3, metricType(first));
        for (Metric metric : metrics) {
            family.writeMessage(4, writeMetric(metric));
        }
        if (first instanceof ExponentialHistogramMetric histogram && histogram.unit() != null) {
            family.writeString(5, histogram.unit());
        }
        return family;
    }

    private static ProtoOutput writeMetric(Metric metric)
    {
        ProtoOutput output = new ProtoOutput();
        metric.labels().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> output.writeMessage(1, writeLabel(entry)));

        switch (metric) {
            case BigCounter counter -> output.writeMessage(3, writeDoubleValue(counter.value().doubleValue()));
            case Counter counter -> output.writeMessage(3, writeDoubleValue(counter.value()));
            case ExponentialHistogramMetric histogram -> output.writeMessage(7, writeHistogram(histogram));
            case Gauge gauge -> output.writeMessage(2, writeDoubleValue(gauge.value()));
            case Info _ -> output.writeMessage(2, writeDoubleValue(1));
            case Summary summary -> output.writeMessage(4, writeSummary(summary));
            case CompositeMetric _ -> throw compositeMetricNotFlattened();
        }
        return output;
    }

    private static ProtoOutput writeLabel(Map.Entry<String, String> entry)
    {
        ProtoOutput label = new ProtoOutput();
        label.writeString(1, entry.getKey());
        label.writeString(2, entry.getValue());
        return label;
    }

    private static ProtoOutput writeDoubleValue(double value)
    {
        ProtoOutput output = new ProtoOutput();
        output.writeDouble(1, value);
        return output;
    }

    private static ProtoOutput writeSummary(Summary summary)
    {
        ProtoOutput output = new ProtoOutput();
        if (summary.count() != null) {
            output.writeUInt(1, summary.count());
        }
        if (summary.sum() != null) {
            output.writeDouble(2, summary.sum());
        }
        if (summary.quantiles() != null) {
            summary.quantiles().entrySet().stream()
                    .sorted(comparing(Map.Entry::getKey))
                    .forEach(entry -> {
                        ProtoOutput quantile = new ProtoOutput();
                        quantile.writeDouble(1, entry.getKey());
                        quantile.writeDouble(2, entry.getValue());
                        output.writeMessage(3, quantile);
                    });
        }
        if (summary.created() != null) {
            output.writeMessage(4, writeTimestamp(summary.created()));
        }
        return output;
    }

    private static ProtoOutput writeTimestamp(double epochSeconds)
    {
        long seconds = (long) Math.floor(epochSeconds);
        int nanos = (int) Math.round((epochSeconds - seconds) * 1_000_000_000);
        if (nanos == 1_000_000_000) {
            seconds++;
            nanos = 0;
        }

        ProtoOutput timestamp = new ProtoOutput();
        timestamp.writeInt(1, seconds);
        if (nanos != 0) {
            timestamp.writeInt(2, nanos);
        }
        return timestamp;
    }

    private static ProtoOutput writeHistogram(ExponentialHistogramMetric metric)
    {
        ProtoOutput histogram = new ProtoOutput();
        histogram.writeUInt(1, metric.snapshot().count());
        histogram.writeDouble(2, metric.snapshot().sum());
        histogram.writeSInt(5, metric.snapshot().scale());
        histogram.writeDouble(6, 0);
        histogram.writeUInt(7, metric.snapshot().zeroCount());
        writeBuckets(histogram, 9, 10, metric.snapshot().negativeBuckets());
        writeBuckets(histogram, 12, 13, metric.snapshot().positiveBuckets());

        // An empty histogram needs a no-op span to distinguish it from a classic histogram.
        if (metric.snapshot().zeroCount() == 0 &&
                metric.snapshot().negativeBuckets().isEmpty() &&
                metric.snapshot().positiveBuckets().isEmpty()) {
            histogram.writeMessage(12, new ProtoOutput());
        }
        return histogram;
    }

    private static void writeBuckets(ProtoOutput histogram, int spanField, int deltaField, Buckets buckets)
    {
        long[] counts = buckets.counts();
        if (counts.length == 0) {
            return;
        }

        ProtoOutput span = new ProtoOutput();
        // OpenTelemetry index 0 is (1, base], while Prometheus index 0 is (base^-1, 1].
        span.writeSInt(1, (long) buckets.offset() + 1);
        span.writeUInt(2, counts.length);
        histogram.writeMessage(spanField, span);

        long previous = 0;
        for (long count : counts) {
            histogram.writeSInt(deltaField, count - previous);
            previous = count;
        }
    }

    private static int metricType(Metric metric)
    {
        return switch (metric) {
            case BigCounter _, Counter _ -> METRIC_TYPE_COUNTER;
            case Gauge _, Info _ -> METRIC_TYPE_GAUGE;
            case Summary _ -> METRIC_TYPE_SUMMARY;
            case ExponentialHistogramMetric _ -> METRIC_TYPE_HISTOGRAM;
            case CompositeMetric _ -> throw compositeMetricNotFlattened();
        };
    }

    private static IllegalArgumentException compositeMetricNotFlattened()
    {
        return new IllegalArgumentException("CompositeMetric must be flattened before Prometheus protobuf encoding");
    }

    private static void writeVarint(OutputStream output, long value)
            throws IOException
    {
        while ((value & ~0x7FL) != 0) {
            output.write(((int) value & 0x7F) | 0x80);
            value >>>= 7;
        }
        output.write((int) value);
    }

    private static final class ProtoOutput
            extends ByteArrayOutputStream
    {
        private static final VarHandle DOUBLE_HANDLE = MethodHandles.byteArrayViewVarHandle(double[].class, LITTLE_ENDIAN);

        private final byte[] doubleBuffer = new byte[Double.BYTES];

        public void writeString(int field, String value)
        {
            writeBytes(field, value.getBytes(UTF_8));
        }

        public void writeMessage(int field, ProtoOutput value)
        {
            writeBytes(field, value.toByteArray());
        }

        public void writeDouble(int field, double value)
        {
            writeTag(field, 1);
            DOUBLE_HANDLE.set(doubleBuffer, 0, value);
            writeBytes(doubleBuffer);
        }

        public void writeInt(int field, long value)
        {
            writeTag(field, 0);
            writeVarint(value);
        }

        public void writeUInt(int field, long value)
        {
            writeTag(field, 0);
            writeVarint(value);
        }

        public void writeSInt(int field, long value)
        {
            writeTag(field, 0);
            writeVarint((value << 1) ^ (value >> 63));
        }

        private void writeBytes(int field, byte[] value)
        {
            writeTag(field, 2);
            writeVarint(value.length);
            writeBytes(value);
        }

        private void writeTag(int field, int wireType)
        {
            writeVarint(((long) field << 3) | wireType);
        }

        private void writeVarint(long value)
        {
            while ((value & ~0x7FL) != 0) {
                write(((int) value & 0x7F) | 0x80);
                value >>>= 7;
            }
            write((int) value);
        }
    }
}
