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

    // Field numbers are defined by the Prometheus client model schema:
    // https://github.com/prometheus/client_model/blob/master/io/prometheus/client/metrics.proto
    private static final int LABEL_PAIR_NAME_FIELD = 1;
    private static final int LABEL_PAIR_VALUE_FIELD = 2;

    private static final int GAUGE_VALUE_FIELD = 1;
    private static final int COUNTER_VALUE_FIELD = 1;

    private static final int QUANTILE_QUANTILE_FIELD = 1;
    private static final int QUANTILE_VALUE_FIELD = 2;

    private static final int SUMMARY_SAMPLE_COUNT_FIELD = 1;
    private static final int SUMMARY_SAMPLE_SUM_FIELD = 2;
    private static final int SUMMARY_QUANTILE_FIELD = 3;
    private static final int SUMMARY_CREATED_TIMESTAMP_FIELD = 4;

    private static final int HISTOGRAM_SAMPLE_COUNT_FIELD = 1;
    private static final int HISTOGRAM_SAMPLE_SUM_FIELD = 2;
    private static final int HISTOGRAM_SCHEMA_FIELD = 5;
    private static final int HISTOGRAM_ZERO_THRESHOLD_FIELD = 6;
    private static final int HISTOGRAM_ZERO_COUNT_FIELD = 7;
    private static final int HISTOGRAM_NEGATIVE_SPAN_FIELD = 9;
    private static final int HISTOGRAM_NEGATIVE_DELTA_FIELD = 10;
    private static final int HISTOGRAM_POSITIVE_SPAN_FIELD = 12;
    private static final int HISTOGRAM_POSITIVE_DELTA_FIELD = 13;

    private static final int BUCKET_SPAN_OFFSET_FIELD = 1;
    private static final int BUCKET_SPAN_LENGTH_FIELD = 2;

    private static final int METRIC_LABEL_FIELD = 1;
    private static final int METRIC_GAUGE_FIELD = 2;
    private static final int METRIC_COUNTER_FIELD = 3;
    private static final int METRIC_SUMMARY_FIELD = 4;
    private static final int METRIC_HISTOGRAM_FIELD = 7;

    private static final int METRIC_FAMILY_NAME_FIELD = 1;
    private static final int METRIC_FAMILY_HELP_FIELD = 2;
    private static final int METRIC_FAMILY_TYPE_FIELD = 3;
    private static final int METRIC_FAMILY_METRIC_FIELD = 4;
    private static final int METRIC_FAMILY_UNIT_FIELD = 5;

    private static final int TIMESTAMP_SECONDS_FIELD = 1;
    private static final int TIMESTAMP_NANOS_FIELD = 2;

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
        family.writeString(METRIC_FAMILY_NAME_FIELD, first.metricName());
        if (first.help() != null && !first.help().isEmpty()) {
            family.writeString(METRIC_FAMILY_HELP_FIELD, first.help());
        }
        family.writeUInt(METRIC_FAMILY_TYPE_FIELD, metricType(first));
        for (Metric metric : metrics) {
            family.writeMessage(METRIC_FAMILY_METRIC_FIELD, writeMetric(metric));
        }
        if (first instanceof ExponentialHistogramMetric histogram && histogram.unit() != null) {
            family.writeString(METRIC_FAMILY_UNIT_FIELD, histogram.unit());
        }
        return family;
    }

    private static ProtoOutput writeMetric(Metric metric)
    {
        ProtoOutput output = new ProtoOutput();
        metric.labels().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> output.writeMessage(METRIC_LABEL_FIELD, writeLabel(entry)));

        switch (metric) {
            case BigCounter counter -> output.writeMessage(
                    METRIC_COUNTER_FIELD,
                    writeDoubleValue(COUNTER_VALUE_FIELD, counter.value().doubleValue()));
            case Counter counter -> output.writeMessage(
                    METRIC_COUNTER_FIELD,
                    writeDoubleValue(COUNTER_VALUE_FIELD, counter.value()));
            case ExponentialHistogramMetric histogram -> output.writeMessage(
                    METRIC_HISTOGRAM_FIELD,
                    writeHistogram(histogram));
            case Gauge gauge -> output.writeMessage(
                    METRIC_GAUGE_FIELD,
                    writeDoubleValue(GAUGE_VALUE_FIELD, gauge.value()));
            case Info _ -> output.writeMessage(METRIC_GAUGE_FIELD, writeDoubleValue(GAUGE_VALUE_FIELD, 1));
            case Summary summary -> output.writeMessage(METRIC_SUMMARY_FIELD, writeSummary(summary));
            case CompositeMetric _ -> throw compositeMetricNotFlattened();
        }
        return output;
    }

    private static ProtoOutput writeLabel(Map.Entry<String, String> entry)
    {
        ProtoOutput label = new ProtoOutput();
        label.writeString(LABEL_PAIR_NAME_FIELD, entry.getKey());
        label.writeString(LABEL_PAIR_VALUE_FIELD, entry.getValue());
        return label;
    }

    private static ProtoOutput writeDoubleValue(int fieldNumber, double value)
    {
        ProtoOutput output = new ProtoOutput();
        output.writeDouble(fieldNumber, value);
        return output;
    }

    private static ProtoOutput writeSummary(Summary summary)
    {
        ProtoOutput output = new ProtoOutput();
        if (summary.count() != null) {
            output.writeUInt(SUMMARY_SAMPLE_COUNT_FIELD, summary.count());
        }
        if (summary.sum() != null) {
            output.writeDouble(SUMMARY_SAMPLE_SUM_FIELD, summary.sum());
        }
        if (summary.quantiles() != null) {
            summary.quantiles().entrySet().stream()
                    .sorted(comparing(Map.Entry::getKey))
                    .forEach(entry -> {
                        ProtoOutput quantile = new ProtoOutput();
                        quantile.writeDouble(QUANTILE_QUANTILE_FIELD, entry.getKey());
                        quantile.writeDouble(QUANTILE_VALUE_FIELD, entry.getValue());
                        output.writeMessage(SUMMARY_QUANTILE_FIELD, quantile);
                    });
        }
        if (summary.created() != null) {
            output.writeMessage(SUMMARY_CREATED_TIMESTAMP_FIELD, writeTimestamp(summary.created()));
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
        timestamp.writeInt(TIMESTAMP_SECONDS_FIELD, seconds);
        if (nanos != 0) {
            timestamp.writeInt(TIMESTAMP_NANOS_FIELD, nanos);
        }
        return timestamp;
    }

    private static ProtoOutput writeHistogram(ExponentialHistogramMetric metric)
    {
        ProtoOutput histogram = new ProtoOutput();
        histogram.writeUInt(HISTOGRAM_SAMPLE_COUNT_FIELD, metric.snapshot().count());
        histogram.writeDouble(HISTOGRAM_SAMPLE_SUM_FIELD, metric.snapshot().sum());
        histogram.writeSInt(HISTOGRAM_SCHEMA_FIELD, metric.snapshot().scale());
        histogram.writeDouble(HISTOGRAM_ZERO_THRESHOLD_FIELD, 0);
        histogram.writeUInt(HISTOGRAM_ZERO_COUNT_FIELD, metric.snapshot().zeroCount());
        writeBuckets(
                histogram,
                HISTOGRAM_NEGATIVE_SPAN_FIELD,
                HISTOGRAM_NEGATIVE_DELTA_FIELD,
                metric.snapshot().negativeBuckets());
        writeBuckets(
                histogram,
                HISTOGRAM_POSITIVE_SPAN_FIELD,
                HISTOGRAM_POSITIVE_DELTA_FIELD,
                metric.snapshot().positiveBuckets());

        // An empty histogram needs a no-op span to distinguish it from a classic histogram.
        if (metric.snapshot().zeroCount() == 0 &&
                metric.snapshot().negativeBuckets().isEmpty() &&
                metric.snapshot().positiveBuckets().isEmpty()) {
            histogram.writeMessage(HISTOGRAM_POSITIVE_SPAN_FIELD, new ProtoOutput());
        }
        return histogram;
    }

    private static void writeBuckets(ProtoOutput histogram, int spanFieldNumber, int deltaFieldNumber, Buckets buckets)
    {
        long[] counts = buckets.counts();
        if (counts.length == 0) {
            return;
        }

        ProtoOutput span = new ProtoOutput();
        // OpenTelemetry index 0 is (1, base], while Prometheus index 0 is (base^-1, 1].
        span.writeSInt(BUCKET_SPAN_OFFSET_FIELD, (long) buckets.offset() + 1);
        span.writeUInt(BUCKET_SPAN_LENGTH_FIELD, counts.length);
        histogram.writeMessage(spanFieldNumber, span);

        long previous = 0;
        for (long count : counts) {
            histogram.writeSInt(deltaFieldNumber, count - previous);
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
        private static final int VARINT_WIRE_TYPE = 0;
        private static final int FIXED_64_WIRE_TYPE = 1;
        private static final int LENGTH_DELIMITED_WIRE_TYPE = 2;

        private static final VarHandle DOUBLE_HANDLE = MethodHandles.byteArrayViewVarHandle(double[].class, LITTLE_ENDIAN);

        private final byte[] doubleBuffer = new byte[Double.BYTES];

        public void writeString(int fieldNumber, String value)
        {
            writeBytes(fieldNumber, value.getBytes(UTF_8));
        }

        public void writeMessage(int fieldNumber, ProtoOutput value)
        {
            writeTag(fieldNumber, LENGTH_DELIMITED_WIRE_TYPE);
            writeVarint(value.count);
            write(value.buf, 0, value.count);
        }

        public void writeDouble(int fieldNumber, double value)
        {
            writeTag(fieldNumber, FIXED_64_WIRE_TYPE);
            DOUBLE_HANDLE.set(doubleBuffer, 0, value);
            writeBytes(doubleBuffer);
        }

        public void writeInt(int fieldNumber, long value)
        {
            writeTag(fieldNumber, VARINT_WIRE_TYPE);
            writeVarint(value);
        }

        public void writeUInt(int fieldNumber, long value)
        {
            writeTag(fieldNumber, VARINT_WIRE_TYPE);
            writeVarint(value);
        }

        public void writeSInt(int fieldNumber, long value)
        {
            writeTag(fieldNumber, VARINT_WIRE_TYPE);
            writeVarint((value << 1) ^ (value >> 63));
        }

        private void writeBytes(int fieldNumber, byte[] value)
        {
            writeTag(fieldNumber, LENGTH_DELIMITED_WIRE_TYPE);
            writeVarint(value.length);
            writeBytes(value);
        }

        private void writeTag(int fieldNumber, int wireType)
        {
            writeVarint(((long) fieldNumber << 3) | wireType);
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
