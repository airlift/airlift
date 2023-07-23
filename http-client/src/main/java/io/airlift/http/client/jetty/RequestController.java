package io.airlift.http.client.jetty;

import com.google.common.collect.ImmutableList;
import io.airlift.http.client.BodyGenerator;
import io.airlift.http.client.ByteBufferBodyGenerator;
import io.airlift.http.client.FileBodyGenerator;
import io.airlift.http.client.HttpRequestFilter;
import io.airlift.http.client.HttpStatusListener;
import io.airlift.http.client.Request;
import io.airlift.http.client.StaticBodyGenerator;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.HttpRequest;
import org.eclipse.jetty.client.api.Response;
import org.eclipse.jetty.client.util.ByteBufferRequestContent;
import org.eclipse.jetty.client.util.BytesRequestContent;
import org.eclipse.jetty.client.util.PathRequestContent;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeoutException;

import static com.google.common.base.Throwables.throwIfUnchecked;
import static io.airlift.http.client.jetty.AuthorizationPreservingHttpClient.setPreserveAuthorization;
import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static java.util.Locale.ENGLISH;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.eclipse.jetty.client.HttpClient.normalizePort;

class RequestController
{
    static final String STATS_KEY = "airlift_stats";
    private static final AttributeKey<String> CLIENT_NAME = stringKey("airlift.http.client_name");

    private final HttpClient httpClient;
    private final String name;
    private final Tracer tracer;
    private final List<HttpRequestFilter> requestFilters;
    private final List<HttpStatusListener> httpStatusListeners;
    private final TextMapPropagator propagator;
    private final JettyClientDiagnostics clientDiagnostics;
    private final long requestTimeoutMillis;
    private final long idleTimeoutMillis;
    private final boolean logEnabled;
    private final HttpClientLogger requestLogger;

    RequestController(
            HttpClient httpClient,
            String name,
            Tracer tracer,
            List<HttpRequestFilter> requestFilters,
            List<HttpStatusListener> httpStatusListeners,
            TextMapPropagator propagator,
            JettyClientDiagnostics clientDiagnostics,
            HttpClientLogger requestLogger,
            long requestTimeoutMillis,
            long idleTimeoutMillis,
            boolean logEnabled)
    {
        this.httpClient = requireNonNull(httpClient, "httpClient is null");
        this.name = requireNonNull(name, "name is null");
        this.tracer = requireNonNull(tracer, "tracer is null");
        this.propagator = requireNonNull(propagator, "propagator is null");
        this.clientDiagnostics = requireNonNull(clientDiagnostics, "clientDiagnostics is null");
        this.requestLogger = requireNonNull(requestLogger, "requestLogger is null");
        this.requestTimeoutMillis = requestTimeoutMillis;
        this.idleTimeoutMillis = idleTimeoutMillis;
        this.logEnabled = logEnabled;

        this.requestFilters = ImmutableList.copyOf(requestFilters);
        this.httpStatusListeners = ImmutableList.copyOf(httpStatusListeners);
    }

    record PreparedRequest(Request request, Span span)
    {
        PreparedRequest
        {
            requireNonNull(request, "request is null");
            requireNonNull(span, "span is null");
        }
    }

    PreparedRequest prepareRequest(Request request)
    {
        for (HttpRequestFilter requestFilter : requestFilters) {
            request = requestFilter.filterRequest(request);
        }

        Span span = startSpan(request);
        request = injectTracing(request, span);

        return new PreparedRequest(request, span);
    }

    HttpRequest buildJettyRequest(Request finalRequest)
    {
        JettyRequestListener listener = new JettyRequestListener(finalRequest.getUri());
        HttpRequest jettyRequest = (HttpRequest) httpClient.newRequest(finalRequest.getUri());
        jettyRequest.onRequestBegin(request -> listener.onRequestBegin());
        jettyRequest.onRequestSuccess(request -> listener.onRequestEnd());
        jettyRequest.onResponseBegin(response -> listener.onResponseBegin());
        jettyRequest.onComplete(result -> listener.onFinish());
        jettyRequest.onComplete(result -> {
            if (result.isFailed() && result.getFailure() instanceof TimeoutException) {
                clientDiagnostics.logDiagnosticsInfo(httpClient);
            }
        });

        jettyRequest.attribute(STATS_KEY, listener);

        jettyRequest.method(finalRequest.getMethod());

        jettyRequest.headers(headers -> finalRequest.getHeaders().forEach(headers::add));

        BodyGenerator bodyGenerator = finalRequest.getBodyGenerator();
        if (bodyGenerator != null) {
            if (bodyGenerator instanceof StaticBodyGenerator generator) {
                jettyRequest.body(new BytesRequestContent(generator.getBody()));
            }
            else if (bodyGenerator instanceof ByteBufferBodyGenerator generator) {
                jettyRequest.body(new ByteBufferRequestContent(generator.getByteBuffers()));
            }
            else if (bodyGenerator instanceof FileBodyGenerator generator) {
                jettyRequest.body(fileContent(generator.getPath()));
            }
            else {
                jettyRequest.body(new BytesRequestContent(generateBody(bodyGenerator)));
            }
        }

        jettyRequest.followRedirects(finalRequest.isFollowRedirects());

        setPreserveAuthorization(jettyRequest, finalRequest.isPreserveAuthorizationOnRedirect());

        // timeouts
        jettyRequest.timeout(requestTimeoutMillis, MILLISECONDS);
        jettyRequest.idleTimeout(idleTimeoutMillis, MILLISECONDS);

        return jettyRequest;
    }

    void callHttpStatusListeners(Response response)
    {
        httpStatusListeners.forEach(listener -> {
            try {
                listener.statusReceived(response.getStatus());
            }
            catch (Exception e) {
                response.abort(e);
            }
        });
    }

    void addLoggingListener(HttpRequest jettyRequest, long requestTimestamp)
    {
        if (logEnabled) {
            HttpClientLoggingListener loggingListener = new HttpClientLoggingListener(jettyRequest, requestTimestamp, requestLogger);
            jettyRequest.listener(loggingListener);
            jettyRequest.onResponseBegin(loggingListener);
            jettyRequest.onComplete(loggingListener);
        }
    }

    private Span startSpan(Request request)
    {
        String method = request.getMethod().toUpperCase(ENGLISH);
        int port = normalizePort(request.getUri().getScheme(), request.getUri().getPort());
        return request.getSpanBuilder()
                .orElseGet(() -> tracer.spanBuilder(name + " " + method))
                .setSpanKind(SpanKind.CLIENT)
                .setAttribute(CLIENT_NAME, name)
                .setAttribute(SemanticAttributes.HTTP_URL, request.getUri().toString())
                .setAttribute(SemanticAttributes.HTTP_METHOD, method)
                .setAttribute(SemanticAttributes.NET_PEER_NAME, request.getUri().getHost())
                .setAttribute(SemanticAttributes.NET_PEER_PORT, (long) port)
                .startSpan();
    }

    @SuppressWarnings("DataFlowIssue")
    private Request injectTracing(Request request, Span span)
    {
        Context context = Context.current().with(span);
        Request.Builder builder = Request.Builder.fromRequest(request);
        propagator.inject(context, builder, Request.Builder::addHeader);
        return builder.build();
    }

    private static PathRequestContent fileContent(Path path)
    {
        try {
            return new PathRequestContent(path);
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @SuppressWarnings("deprecation")
    private static byte[] generateBody(BodyGenerator generator)
    {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            generator.write(out);
        }
        catch (Exception e) {
            throwIfUnchecked(e);
            throw new RuntimeException(e);
        }
        return out.toByteArray();
    }
}
