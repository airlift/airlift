package com.proofpoint.event.client;

import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.Futures;
import com.google.inject.Inject;
import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.ListenableFuture;
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
import java.util.concurrent.Executor;
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
                String uriString = uri.toString();
                Request request = new RequestBuilder("POST")
                        .setUrl(uriString)
                        .setHeader("Content-Type", "application/json")
                        .setBody(new JsonEntityWriter<T>(eventWriter, eventGenerator))
                        .build();
                return new FutureResponse(client.prepareRequest(request).execute(), uriString);
            }
            catch (Exception e) {
                // todo not noisy enough
                log.debug(e, "Posting event failed");
            }
        }

        log.debug("Event(s) not posted");
        return Futures.immediateFuture(null);
    }

    private static class FutureResponse implements Future<Void>, Runnable
    {
        private static final Executor statusLoggerExecutor = new Executor()
        {
            @Override
            public void execute(Runnable command)
            {
                command.run();
            }
        };
        
        private final ListenableFuture<Response> delegate;
        private final String uri;

        public FutureResponse(ListenableFuture<Response> delegate, String uri)
        {
            this.delegate = delegate;
            this.uri = uri;
            delegate.addListener(this, statusLoggerExecutor);
        }

        @Override
        public Void get()
                throws InterruptedException, ExecutionException
        {
            delegate.get();
            return null;
        }

        @Override
        public Void get(long timeout, TimeUnit unit)
                throws InterruptedException, ExecutionException, TimeoutException
        {
            delegate.get(timeout, unit);
            return null;
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
        
        //Invoked as a result of ListenableFuture.addListener(this, ...)
        //Expected to execute quickly on a Future<Response> that has completed.
        @Override
        public void run()
        {
            try {
                Response response = delegate.get();
                int statusCode = response.getStatusCode();
                if (statusCode != HttpServletResponse.SC_OK && statusCode != HttpServletResponse.SC_ACCEPTED) {
                    try {
                        log.debug("Posting event to %s failed: status_code=%d status_line=%s body=%s", uri, statusCode, response.getStatusText(), response.getResponseBody());
                    }
                    catch (IOException bodyError) {
                        log.debug("Posting event to %s failed: status_code=%d status_line=%s error=%s", uri, statusCode, response.getStatusText(), bodyError.getMessage());
                    }
                }
            }
            catch (Exception unexpectedError) {
                log.debug(unexpectedError, "Posting event to %s failed", uri);
            }
        }

        @Override
        public String toString()
        {
            return "Event post to " + uri + (isDone() ? " (done)" : "");
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
