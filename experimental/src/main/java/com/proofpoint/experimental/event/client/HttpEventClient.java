package com.proofpoint.experimental.event.client;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.Futures;
import com.google.inject.Inject;
import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.Request;
import com.ning.http.client.Request.EntityWriter;
import com.ning.http.client.RequestBuilder;
import com.ning.http.client.Response;
import com.proofpoint.log.Logger;
import org.codehaus.jackson.JsonEncoding;
import org.codehaus.jackson.JsonFactory;
import org.codehaus.jackson.JsonGenerator;
import org.codehaus.jackson.Version;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.module.SimpleModule;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class HttpEventClient implements EventClient
{
    private static final Logger log = Logger.get(HttpEventClient.class);

    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final AsyncHttpClient client;
    private final Set<Class<?>> registeredTypes;

    @Inject
    public HttpEventClient(HttpEventClientConfig config,
            ObjectMapper objectMapper,
            @ForEventClient AsyncHttpClient client,
            List<EventTypeMetadata<?>> eventTypes)
    {
        Preconditions.checkNotNull(config, "config is null");
        Preconditions.checkNotNull(objectMapper, "objectMapper is null");
        Preconditions.checkNotNull(client, "client is null");
        Preconditions.checkNotNull(eventTypes, "types is null");
        Preconditions.checkArgument(!eventTypes.isEmpty(), "types is empty");

        this.objectMapper = objectMapper;
        this.client = client;
        this.baseUrl = config.getBaseUrl();

        ImmutableSet.Builder<Class<?>> typeRegistrations = ImmutableSet.builder();

        SimpleModule eventModule = new SimpleModule("MyModule", new Version(1, 0, 0, null));
        for (EventTypeMetadata<?> eventType : eventTypes) {
            eventModule.addSerializer(EventJsonSerializer.createEventJsonSerializer(eventType, config.getJsonVersion()));
            typeRegistrations.add(eventType.getEventClass());
        }
        objectMapper.registerModule(eventModule);
        this.registeredTypes = typeRegistrations.build();
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
            throws IllegalArgumentException
    {
        Preconditions.checkNotNull(eventGenerator, "eventGenerator is null");
        Request request = new RequestBuilder("POST")
                .setUrl(baseUrl)
                .setHeader("Content-Type", "application/json")
                .setBody(new JsonEntityWriter<T>(objectMapper, registeredTypes, eventGenerator))
                .build();

        try {
            return new FutureResponse(client.prepareRequest(request).execute());
        }
        catch (IOException e) {
            log.error(e, "Posting event failed");
            return Futures.immediateFuture(null);
        }
    }

    private static class FutureResponse implements Future<Void>
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
                catch(IOException ignored) {
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

    private static class JsonEntityWriter<T> implements EntityWriter
    {
        private final ObjectMapper objectMapper;
        private final Set<Class<?>> registeredTypes;
        private final EventGenerator<T> events;

        public JsonEntityWriter(ObjectMapper objectMapper, Set<Class<?>> registeredTypes, EventGenerator<T> events)
        {
            Preconditions.checkNotNull(objectMapper, "objectMapper is null");
            Preconditions.checkNotNull(registeredTypes, "registeredTypes is null");
            Preconditions.checkNotNull(events, "events is null");
            this.objectMapper = objectMapper;
            this.registeredTypes = registeredTypes;
            this.events = events;
        }

        @Override
        public void writeEntity(final OutputStream out)
                throws IOException
        {
            JsonFactory jsonFactory = objectMapper.getJsonFactory();
            final JsonGenerator jsonGenerator = jsonFactory.createJsonGenerator(out, JsonEncoding.UTF8);

            jsonGenerator.writeStartArray();

            events.generate(new EventPoster<T>()
            {

                @Override
                public void post(T event)
                        throws IOException
                {
                    if (!registeredTypes.contains(event.getClass())) {
                        throw new RuntimeException(
                                String.format("Event type %s has not been registered as an event",
                                        event.getClass().getSimpleName()));
                    }
                    jsonGenerator.writeObject(event);
                }
            });

            jsonGenerator.writeEndArray();
            jsonGenerator.flush();
        }
    }
}
