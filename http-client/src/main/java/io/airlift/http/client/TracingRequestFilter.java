package io.airlift.http.client;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.semconv.HttpAttributes;
import io.opentelemetry.semconv.ServerAttributes;
import io.opentelemetry.semconv.UrlAttributes;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static java.util.Objects.requireNonNull;
import static org.eclipse.jetty.client.HttpClient.normalizePort;

public class TracingRequestFilter
        implements HttpRequestFilter
{
    private static final AttributeKey<String> CLIENT_NAME = stringKey("airlift.http.client_name");

    private final String name;
    private final Tracer tracer;
    private final TextMapPropagator propagator;

    public TracingRequestFilter(String name, OpenTelemetry telemetry, Tracer tracer)
    {
        this.name = requireNonNull(name, "name is null");
        this.tracer = requireNonNull(tracer, "tracer is null");
        this.propagator = telemetry.getPropagators().getTextMapPropagator();
    }

    @Override
    public Request filterRequest(Request request)
    {
        Span span = startSpan(request);
        Context context = Context.current().with(span);
        Request.Builder builder = Request.Builder
                .fromRequest(request)
                .setCurrentSpan(span);
        propagator.inject(context, builder, (carrier, headerName, value) -> carrier.addHeader(HeaderName.of(headerName), value));
        return builder.build();
    }

    private Span startSpan(Request request)
    {
        int port = normalizePort(request.getUri().getScheme(), request.getUri().getPort());
        return request.getSpanBuilder()
                .orElseGet(() -> tracer.spanBuilder(name + " " + request.getMethod()))
                .setSpanKind(SpanKind.CLIENT)
                .setAttribute(CLIENT_NAME, name)
                .setAttribute(UrlAttributes.URL_FULL, request.getUri().toString())
                .setAttribute(HttpAttributes.HTTP_REQUEST_METHOD, request.getMethod())
                .setAttribute(ServerAttributes.SERVER_ADDRESS, request.getUri().getHost())
                .setAttribute(ServerAttributes.SERVER_PORT, (long) port)
                .startSpan();
    }
}
