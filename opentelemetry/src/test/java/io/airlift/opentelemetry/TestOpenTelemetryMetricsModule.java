package io.airlift.opentelemetry;

import com.google.inject.Binding;
import com.google.inject.Key;
import com.google.inject.TypeLiteral;
import com.google.inject.spi.Element;
import com.google.inject.spi.Elements;
import com.google.inject.spi.LinkedKeyBinding;
import io.airlift.metrics.MetricsCollector;
import io.opentelemetry.sdk.metrics.export.MetricProducer;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TestOpenTelemetryMetricsModule
{
    @Test
    void testBindings()
    {
        List<Element> elements = Elements.getElements(new OpenTelemetryMetricsModule());

        assertThat(hasBinding(elements, Key.get(MetricsCollector.class))).isTrue();
        assertThat(hasBinding(elements, Key.get(OpenTelemetryMetricDataConverter.class))).isTrue();
        assertThat(hasBinding(elements, Key.get(new TypeLiteral<Set<MetricProducer>>() {}))).isTrue();
        assertThat(hasLinkedBinding(elements, MetricProducer.class, OpenTelemetryMetricProducer.class)).isTrue();
    }

    private static boolean hasBinding(List<Element> elements, Key<?> key)
    {
        return elements.stream()
                .anyMatch(element -> element instanceof Binding<?> binding && binding.getKey().equals(key));
    }

    private static boolean hasLinkedBinding(List<Element> elements, Class<?> keyType, Class<?> linkedKeyType)
    {
        return elements.stream()
                .anyMatch(element -> element instanceof LinkedKeyBinding<?> binding &&
                        binding.getKey().getTypeLiteral().getRawType().equals(keyType) &&
                        binding.getLinkedKey().getTypeLiteral().getRawType().equals(linkedKeyType));
    }
}
