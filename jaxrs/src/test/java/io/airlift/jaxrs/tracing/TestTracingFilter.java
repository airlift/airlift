package io.airlift.jaxrs.tracing;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.semconv.CodeAttributes;
import io.opentelemetry.semconv.HttpAttributes;
import org.glassfish.jersey.internal.MapPropertiesDelegate;
import org.glassfish.jersey.server.ContainerRequest;
import org.glassfish.jersey.server.internal.routing.RoutingContext;
import org.glassfish.jersey.uri.UriTemplate;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

public class TestTracingFilter
{
    private static final Pattern PATTERN = Pattern.compile("/v1/service/(.*)");

    @Test
    public void testRoute()
    {
        // matched templates arrive in reverse matching order, most recently matched first
        assertThat(TracingFilter.route(templates())).isEmpty();
        assertThat(TracingFilter.route(templates("/"))).isEmpty();
        assertThat(TracingFilter.route(templates("/{id}", "/v1/service/"))).isEqualTo("/v1/service/{id}");
        assertThat(TracingFilter.route(templates("details", "/{id}", "/v1/service"))).isEqualTo("/v1/service/{id}/details");
    }

    @Test
    public void testSpanUpdated()
    {
        ContainerRequest request = createRequest();
        RoutingContext routingContext = (RoutingContext) request.getUriInfo();
        Matcher matcher = PATTERN.matcher("/v1/service/42");
        assertThat(matcher.matches()).isTrue();
        routingContext.pushMatchResult(matcher);
        routingContext.pushMatchResult(matcher);
        routingContext.pushTemplates(new UriTemplate("/v1/service"), new UriTemplate("/{id}"));
        TestingSpan span = new TestingSpan(true);
        request.setProperty(TracingFilter.REQUEST_SPAN, span);

        new TracingFilter("TestingResource", "testingMethod").filter(request);

        assertThat(span.name).isEqualTo("GET /v1/service/{id}");
        assertThat(span.attributes)
                .containsEntry(HttpAttributes.HTTP_ROUTE, "/v1/service/{id}")
                .containsEntry(CodeAttributes.CODE_FUNCTION_NAME, "TestingResource.testingMethod");
    }

    @Test
    public void testNonRecordingSpanIgnored()
    {
        ContainerRequest request = createRequest();
        TestingSpan span = new TestingSpan(false);
        request.setProperty(TracingFilter.REQUEST_SPAN, span);

        new TracingFilter("TestingResource", "testingMethod").filter(request);

        assertThat(span.name).isNull();
        assertThat(span.attributes).isEmpty();
    }

    @Test
    public void testMissingSpanIgnored()
    {
        // Does not throw if Span isn't passed
        new TracingFilter("TestingResource", "testingMethod").filter(createRequest());
    }

    private static ContainerRequest createRequest()
    {
        return new ContainerRequest(
                URI.create("http://testing/"),
                URI.create("http://testing/v1/service/42"),
                "GET",
                null,
                new MapPropertiesDelegate(),
                null);
    }

    private static List<UriTemplate> templates(String... templates)
    {
        return Stream.of(templates)
                .map(UriTemplate::new)
                .toList();
    }

    private static class TestingSpan
            implements Span
    {
        private final boolean recording;
        private String name;
        private final Map<AttributeKey<?>, Object> attributes = new HashMap<>();

        TestingSpan(boolean recording)
        {
            this.recording = recording;
        }

        @Override
        public <T> Span setAttribute(AttributeKey<T> key, T value)
        {
            attributes.put(key, value);
            return this;
        }

        @Override
        public Span addEvent(String name, Attributes attributes)
        {
            return this;
        }

        @Override
        public Span addEvent(String name, Attributes attributes, long timestamp, TimeUnit unit)
        {
            return this;
        }

        @Override
        public Span setStatus(StatusCode statusCode, String description)
        {
            return this;
        }

        @Override
        public Span recordException(Throwable exception, Attributes additionalAttributes)
        {
            return this;
        }

        @Override
        public Span updateName(String name)
        {
            this.name = name;
            return this;
        }

        @Override
        public void end() {}

        @Override
        public void end(long timestamp, TimeUnit unit) {}

        @Override
        public SpanContext getSpanContext()
        {
            return SpanContext.getInvalid();
        }

        @Override
        public boolean isRecording()
        {
            return recording;
        }
    }
}
