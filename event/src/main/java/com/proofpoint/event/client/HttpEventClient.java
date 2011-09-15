package com.proofpoint.event.client;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.inject.Inject;
import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.Request;
import com.ning.http.client.Request.EntityWriter;
import com.ning.http.client.RequestBuilder;
import com.ning.http.client.Response;
import com.proofpoint.discovery.client.HttpServiceSelector;
import com.proofpoint.discovery.client.ServiceType;
import com.proofpoint.log.Logger;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static java.lang.String.format;

public class HttpEventClient
        implements EventClient
{
    private static final Logger log = Logger.get(HttpEventClient.class);

    private final HttpServiceSelector serviceSelector;
    private final AsyncHttpClient client;
    private final JsonEventWriter eventWriter;
    private final int version;

    @Inject
    public HttpEventClient(
            @ServiceType("event") HttpServiceSelector v1ServiceSelector,
            @ServiceType("collector") HttpServiceSelector serviceSelector,
            @ForEventClient AsyncHttpClient client,
            JsonEventWriter eventWriter,
            HttpEventClientConfig config)
    {
        Preconditions.checkNotNull(serviceSelector, "serviceSelector is null");
        Preconditions.checkNotNull(v1ServiceSelector, "v1ServiceSelector is null");
        Preconditions.checkNotNull(client, "client is null");

        this.client = client;
        this.eventWriter = eventWriter;
        this.version = config.getJsonVersion();

        if (version == 1) {
            this.serviceSelector = v1ServiceSelector;
        }
        else {
            this.serviceSelector = serviceSelector;
        }
    }

    @Override
    public <T> Future<Void> post(T... event)
            throws IllegalArgumentException
    {
        Preconditions.checkNotNull(event, "event is null");
        return post(Arrays.asList(event));
    }

    @Override
    public <T> Future<Void> post(final Iterable<T> events)
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
    public <T> Future<Void> post(EventGenerator<T> eventGenerator)
    {
        Preconditions.checkNotNull(eventGenerator, "eventGenerator is null");

        List<URI> uris = serviceSelector.selectHttpService();

        if (uris.isEmpty()) {
            return Futures.immediateFailedFuture(new ServiceUnavailableException(serviceSelector.getType(), serviceSelector.getPool()));
        }

        ImmutableMap.Builder<URI, Exception> exceptions = ImmutableMap.builder();

        // todo this doesn't really work due to returning the future which can fail without being retried
        // also this code tries all servers instead of a fixed number
        for (final URI uri : uris) {
            try {
                String uriString = resolveUri(uri).toString();
                Request request = new RequestBuilder("POST")
                        .setUrl(uriString)
                        .setHeader("Content-Type", "application/json")
                        .setBody(new JsonEntityWriter<T>(eventWriter, eventGenerator))
                        .build();

                ListenableFuture<Response> future = toGuavaListenableFuture(client.prepareRequest(request).execute());

                return Futures.transform(future, new Function<Response, Void>()
                {
                    public Void apply(Response response)
                    {
                        int statusCode = response.getStatusCode();
                        if (statusCode < 200 || statusCode > 299) {
                            try {
                                log.debug("Posting event to %s failed: status_code=%d status_line=%s body=%s", uri, statusCode, response.getStatusText(), response.getResponseBody());
                            }
                            catch (IOException bodyError) {
                                log.debug("Posting event to %s failed: status_code=%d status_line=%s error=%s", uri, statusCode, response.getStatusText(), bodyError.getMessage());
                            }
                        }
                        return null;
                    }
                });
            }
            catch (Exception e) {
                exceptions.put(uri, e);

                // todo not noisy enough
                log.debug(e, "Posting event failed");
            }
        }

        log.debug("Event(s) not posted");
        return Futures.immediateFailedFuture(new EventSubmissionFailedException(serviceSelector.getType(), serviceSelector.getPool(), exceptions.build()));
    }

    private URI resolveUri(URI uri)
    {
        if (version == 1) {
            return uri;
        }

        return uri.resolve("/v2/event");
    }

    // TODO: copied from com.proofpoint.discovery.client.HttpDiscoveryClient -- factor out?
    private static <T> com.google.common.util.concurrent.ListenableFuture<T> toGuavaListenableFuture(final com.ning.http.client.ListenableFuture<T> asyncClientFuture)
    {
        return new com.google.common.util.concurrent.ListenableFuture<T>()
        {
            @Override
            public boolean cancel(boolean mayInterruptIfRunning)
            {
                return asyncClientFuture.cancel(mayInterruptIfRunning);
            }

            @Override
            public void addListener(Runnable listener, Executor executor)
            {
                asyncClientFuture.addListener(listener, executor);
            }

            @Override
            public boolean isCancelled()
            {
                return asyncClientFuture.isCancelled();
            }

            @Override
            public boolean isDone()
            {
                return asyncClientFuture.isDone();
            }

            @Override
            public T get()
                    throws InterruptedException, ExecutionException
            {
                return asyncClientFuture.get();
            }

            @Override
            public T get(long timeout, TimeUnit timeUnit)
                    throws InterruptedException, ExecutionException, TimeoutException
            {
                return asyncClientFuture.get(timeout, timeUnit);
            }
        };
    }

    private static class JsonEntityWriter<T>
            implements EntityWriter
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
        public void writeEntity(final OutputStream out)
                throws IOException
        {
            eventWriter.writeEvents(events, out);
        }
    }
}
