package com.proofpoint.event.client;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.CharStreams;
import com.google.common.io.Closeables;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFutureTask;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.Inject;
import com.ning.http.client.Request.EntityWriter;
import com.proofpoint.discovery.client.HttpServiceSelector;
import com.proofpoint.discovery.client.ServiceType;
import com.proofpoint.log.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class HttpEventClient
        implements EventClient
{
    private static final Logger log = Logger.get(HttpEventClient.class);

    private final HttpServiceSelector serviceSelector;
    private final JsonEventWriter eventWriter;
    private final int version;
    private ExecutorService executor;

    @Inject
    public HttpEventClient(
            @ServiceType("event") HttpServiceSelector v1ServiceSelector,
            @ServiceType("collector") HttpServiceSelector serviceSelector,
            JsonEventWriter eventWriter,
            HttpEventClientConfig config)
    {
        Preconditions.checkNotNull(serviceSelector, "serviceSelector is null");
        Preconditions.checkNotNull(v1ServiceSelector, "v1ServiceSelector is null");

        this.eventWriter = eventWriter;
        this.version = config.getJsonVersion();

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
                URL url = resolveUri(uri).toURL();
                PostEventTask postEventTask = new PostEventTask(url, new JsonEntityWriter<T>(eventWriter, eventGenerator));
                ListenableFutureTask<Void> futureTask = new ListenableFutureTask<Void>(postEventTask);
                executor.execute(futureTask);
                return futureTask;
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

    private static class PostEventTask implements Callable<Void>
    {
        private final URL url;
        private final EntityWriter eventWriter;

        private PostEventTask(URL url, EntityWriter eventWriter)
        {
            this.url = url;
            this.eventWriter = eventWriter;
        }

        @Override
        public Void call()
                throws Exception
        {
            OutputStream outputStream = null;
            InputStream inputStream = null;
            try {
                HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
                urlConnection.setRequestMethod("POST");
                urlConnection.setRequestProperty("Content-Type", "application/json");
                urlConnection.setChunkedStreamingMode(4096);
                urlConnection.setDoOutput(true);
                outputStream = urlConnection.getOutputStream();
                eventWriter.writeEntity(outputStream);
                outputStream.close();

                // Get the response
                int statusCode = urlConnection.getResponseCode();
                if (statusCode < 200 || statusCode > 299) {
                    try {
                        inputStream = urlConnection.getInputStream();
                        String responseBody = CharStreams.toString(new InputStreamReader(inputStream));
                        log.debug("Posting event to %s failed: status_code=%d status_line=%s body=%s", url, statusCode, urlConnection.getResponseMessage(), responseBody);
                    }
                    catch (IOException bodyError) {
                        log.debug("Posting event to %s failed: status_code=%d status_line=%s error=%s",
                                url,
                                statusCode,
                                urlConnection.getResponseMessage(),
                                bodyError.getMessage());
                    }
                }
            }
            finally {
                Closeables.closeQuietly(outputStream);
                Closeables.closeQuietly(inputStream);
            }
            return null;
        }
    }
}
