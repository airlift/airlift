package com.proofpoint.event.client;

import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.Futures;
import com.google.inject.Inject;
import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.Request;
import com.ning.http.client.Request.EntityWriter;
import com.ning.http.client.RequestBuilder;
import com.ning.http.client.Response;
import com.proofpoint.discovery.client.HttpServiceSelector;
import com.proofpoint.discovery.client.ServiceType;
import com.proofpoint.log.Logger;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class HttpEventClient
        implements EventClient
{
    private static final Logger log = Logger.get(HttpEventClient.class);

    private final HttpServiceSelector serviceSelector;
    private final AsyncHttpClient client;
    private final JsonEventWriter eventWriter;

    @Inject
    public HttpEventClient(@ServiceType("event") HttpServiceSelector serviceSelector,
            @ForEventClient AsyncHttpClient client,
            JsonEventWriter eventWriter)
    {
        Preconditions.checkNotNull(serviceSelector, "serviceSelector is null");
        Preconditions.checkNotNull(client, "client is null");

        this.serviceSelector = serviceSelector;
        this.client = client;
        this.eventWriter = eventWriter;
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

        // todo this doesn't really work due to returning the future which can fail without being retried
        // also this code tries all servers instead of a fixed number
        for (URI uri : serviceSelector.selectHttpService()) {
            try {
                Request request = new RequestBuilder("POST")
                        .setUrl(uri.toString())
                        .setHeader("Content-Type", "application/json")
                        .setBody(new JsonEntityWriter<T>(eventWriter, eventGenerator))
                        .build();
                return new FutureResponse(client.prepareRequest(request).execute());
            }
            catch (Exception e) {
                // todo not noisy enough
                log.debug(e, "Posting event failed");
            }
        }

        log.debug("Event(s) not posted");
        return Futures.immediateFuture(null);
    }

    private static class FutureResponse
            implements Future<Void>
    {
        private final Future<Response> delegate;

        public FutureResponse(Future<Response> delegate)
        {
            this.delegate = delegate;
        }

        @Override
        public Void get()
                throws InterruptedException, ExecutionException
        {
            Response response = delegate.get();
            handleHttpResponse(response);
            return null;
        }

        @Override
        public Void get(long timeout, TimeUnit unit)
                throws InterruptedException, ExecutionException, TimeoutException
        {
            Response response = delegate.get(timeout, unit);
            handleHttpResponse(response);
            return null;
        }

        private void handleHttpResponse(Response response)
        {
            int statusCode = response.getStatusCode();
            if (statusCode != HttpServletResponse.SC_OK && statusCode != HttpServletResponse.SC_ACCEPTED) {
                String body = "<empty>";
                try {
                    body = response.getResponseBody();
                }
                catch (IOException ignored) {
                }
                log.error("Posting event failed: status_code=%d status_line=%s body=%s", statusCode, response.getStatusText(), body);
            }
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning)
        {
            return delegate.cancel(mayInterruptIfRunning);
        }

        @Override
        public boolean isCancelled()
        {
            return delegate.isCancelled();
        }

        @Override
        public boolean isDone()
        {
            return delegate.isDone();
        }
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
