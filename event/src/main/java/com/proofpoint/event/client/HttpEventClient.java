package com.proofpoint.event.client;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.CharStreams;
import com.google.common.util.concurrent.CheckedFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.Inject;
import com.proofpoint.discovery.client.HttpServiceSelector;
import com.proofpoint.discovery.client.ServiceType;
import com.proofpoint.http.client.BodyGenerator;
import com.proofpoint.http.client.HttpClient;
import com.proofpoint.http.client.Request;
import com.proofpoint.http.client.Response;
import com.proofpoint.http.client.ResponseHandler;
import com.proofpoint.log.Logger;

import javax.annotation.PreDestroy;
import javax.ws.rs.core.MediaType;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.proofpoint.http.client.RequestBuilder.preparePost;

public class HttpEventClient
        implements EventClient
{
    private static final Logger log = Logger.get(HttpEventClient.class);

    private final HttpServiceSelector serviceSelector;
    private final JsonEventWriter eventWriter;
    private final int version;
    private ExecutorService executor;
    private final HttpClient httpClient;

    @Inject
    public HttpEventClient(
            @ServiceType("event") HttpServiceSelector v1ServiceSelector,
            @ServiceType("collector") HttpServiceSelector serviceSelector,
            JsonEventWriter eventWriter,
            HttpEventClientConfig config,
            @ForEventClient HttpClient httpClient)
    {
        Preconditions.checkNotNull(serviceSelector, "serviceSelector is null");
        Preconditions.checkNotNull(v1ServiceSelector, "v1ServiceSelector is null");
        Preconditions.checkNotNull(httpClient, "httpClient is null");

        this.eventWriter = eventWriter;
        this.version = config.getJsonVersion();
        this.httpClient = httpClient;

        if (version == 1) {
            this.serviceSelector = v1ServiceSelector;
        }
        else {
            this.serviceSelector = serviceSelector;
        }

        int workerThreads = config.getMaxConnections();
        if (workerThreads <= 0) {
            workerThreads = 16;
        }
        executor = Executors.newFixedThreadPool(workerThreads, new ThreadFactoryBuilder().setNameFormat("http-event-client-%s").build());
    }

    @PreDestroy
    public void destroy() {
        executor.shutdownNow();
    }

    @Override
    public <T> CheckedFuture<Void, RuntimeException> post(T... event)
            throws IllegalArgumentException
    {
        Preconditions.checkNotNull(event, "event is null");
        return post(Arrays.asList(event));
    }

    @Override
    public <T> CheckedFuture<Void, RuntimeException> post(final Iterable<T> events)
            throws IllegalArgumentException
    {
        Preconditions.checkNotNull(events, "eventsSupplier is null");
        return post(new EventGenerator<T>()
        {
            @Override
            public void generate(EventPoster<T> eventPoster)
                    throws IOException
            {
                for (T event : events) {
                    eventPoster.post(event);
                }
            }
        });
    }

    @Override
    public <T> CheckedFuture<Void, RuntimeException> post(EventGenerator<T> eventGenerator)
    {
        Preconditions.checkNotNull(eventGenerator, "eventGenerator is null");

        List<URI> uris = serviceSelector.selectHttpService();

        if (uris.isEmpty()) {
            return Futures.<Void, RuntimeException>immediateFailedCheckedFuture(new ServiceUnavailableException(serviceSelector.getType(), serviceSelector.getPool()));
        }

        // todo this doesn't really work due to returning the future which can fail without being retried
        Request request = preparePost()
                .setUri(resolveUri(uris.get(0)))
                .setHeader("Content-Type", MediaType.APPLICATION_JSON)
                .setBodyGenerator(new JsonEntityWriter<T>(eventWriter, eventGenerator))
                .build();
        return httpClient.execute(request, new EventResponseHandler(serviceSelector.getType(), serviceSelector.getPool()));
    }

    private URI resolveUri(URI uri)
    {
        if (version == 1) {
            return uri;
        }

        return uri.resolve("/v2/event");
    }

    private static class JsonEntityWriter<T>
            implements BodyGenerator
    {
        private final JsonEventWriter eventWriter;
        private final EventGenerator<T> events;

        public JsonEntityWriter(JsonEventWriter eventWriter, EventGenerator<T> events)
        {
            Preconditions.checkNotNull(eventWriter, "eventWriter is null");
            Preconditions.checkNotNull(events, "events is null");
            this.eventWriter = eventWriter;
            this.events = events;
        }

        @Override
        public void write(OutputStream out)
                throws Exception
        {
            eventWriter.writeEvents(events, out);
        }
    }

    private static class EventResponseHandler implements ResponseHandler<Void, RuntimeException>
    {
        private final String type;
        private final String pool;

        public EventResponseHandler(String type, String pool)
        {
            Preconditions.checkNotNull(type, "type is null");
            Preconditions.checkNotNull(pool, "pool is null");

            this.type = type;
            this.pool = pool;
        }

        @Override
        public EventSubmissionFailedException handleException(Request request, Exception exception)
        {
            log.debug("Posting event to %s failed", request.getUri());
            return new EventSubmissionFailedException(type, pool, ImmutableMap.of(request.getUri(), exception));
        }

        @Override
        public Void handle(Request request, Response response)
        {
            int statusCode = response.getStatusCode();
            if (statusCode >= 200 && statusCode <= 299) {
                return null;
            }

            try {
                InputStream inputStream = response.getInputStream();
                String responseBody = CharStreams.toString(new InputStreamReader(inputStream));
                log.debug("Posting event to %s failed: status_code=%d status_line=%s body=%s", request.getUri(), statusCode, response.getStatusMessage(), responseBody);
            }
            catch (IOException bodyError) {
                log.debug("Posting event to %s failed: status_code=%d status_line=%s error=%s",
                        request.getUri(),
                        statusCode,
                        response.getStatusMessage(),
                        bodyError.getMessage());
            }
            return null;
        }
    }
}
