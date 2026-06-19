package io.airlift.openmetrics;

import com.google.inject.Binding;
import com.google.inject.BindingAnnotation;
import com.google.inject.Key;
import com.google.inject.TypeLiteral;
import com.google.inject.spi.Element;
import com.google.inject.spi.Elements;
import io.airlift.metrics.MetricsCollector;
import io.airlift.metrics.MetricsConfig;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Retention;
import java.util.List;
import java.util.Set;

import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static org.assertj.core.api.Assertions.assertThat;

public class TestJmxOpenMetricsModule
{
    @Test
    public void testMetricsResourceCanBeBoundToQualifiedJaxrsServer()
    {
        List<Element> elements = Elements.getElements(new JmxOpenMetricsModule(ForTesting.class));

        assertThat(hasBinding(elements, Key.get(MetricsConfig.class))).isTrue();
        assertThat(hasBinding(elements, Key.get(MetricsCollector.class))).isTrue();
        assertThat(hasBinding(elements, Key.get(OpenMetricsCollector.class))).isTrue();
        assertThat(hasBinding(elements, Key.get(new TypeLiteral<Set<Object>>() {}))).isFalse();
        assertThat(hasBinding(elements, Key.get(new TypeLiteral<Set<Object>>() {}, ForTesting.class))).isTrue();
    }

    private static boolean hasBinding(List<Element> elements, Key<?> key)
    {
        return elements.stream()
                .filter(Binding.class::isInstance)
                .map(Binding.class::cast)
                .map(Binding::getKey)
                .anyMatch(key::equals);
    }

    @Retention(RUNTIME)
    @BindingAnnotation
    private @interface ForTesting {}
}
