/*
 * Copyright 2010 Proofpoint, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.airlift.event.client;

import com.google.common.collect.ImmutableMap;
import com.google.common.io.CharStreams;
import com.google.common.net.MediaType;
import com.google.common.util.concurrent.ListenableFuture;
import io.airlift.discovery.client.HttpServiceSelector;
import io.airlift.discovery.client.ServiceType;
import io.airlift.http.client.BodyGenerator;
import io.airlift.http.client.HttpClient;
import io.airlift.http.client.Request;
import io.airlift.http.client.RequestStats;
import io.airlift.http.client.Response;
import io.airlift.http.client.ResponseHandler;
import io.airlift.log.Logger;
import io.airlift.node.NodeInfo;
import org.weakref.jmx.Flatten;
import org.weakref.jmx.Managed;

import javax.inject.Inject;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URI;
import java.util.Arrays;
import java.util.List;

import static com.google.common.util.concurrent.Futures.immediateFailedFuture;
import static io.airlift.http.client.Request.Builder.preparePost;
import static java.util.Objects.requireNonNull;

public class HttpEventClient
        implements EventClient
{
    private static final Logger log = Logger.get(HttpEventClient.class);
    private static final MediaType MEDIA_TYPE_JSON = MediaType.create("application", "json");

    private final HttpServiceSelector serviceSelector;
    private final JsonEventWriter eventWriter;
    private final HttpClient httpClient;
    private final NodeInfo nodeInfo;

    @Inject
    public HttpEventClient(
            @ServiceType("collector") HttpServiceSelector serviceSelector,
            JsonEventWriter eventWriter,
            NodeInfo nodeInfo,
            @ForEventClient HttpClient httpClient)
    {
        this.serviceSelector = requireNonNull(serviceSelector, "serviceSelector is null");
        this.eventWriter = requireNonNull(eventWriter, "eventWriter is null");
        this.nodeInfo = requireNonNull(nodeInfo, "nodeInfo is null");
        this.httpClient = requireNonNull(httpClient, "httpClient is null");
    }

    @Flatten
    @Managed
    public RequestStats getStats()
    {
        return httpClient.getStats();
    }

    @SafeVarargs
    @Override
    public final <T> ListenableFuture<Void> post(T... event)
            throws IllegalArgumentException
    {
        requireNonNull(event, "event is null");
        return post(Arrays.asList(event));
    }

    @Override
    public <T> ListenableFuture<Void> post(final Iterable<T> events)
            throws IllegalArgumentException
    {
        requireNonNull(events, "eventsSupplier is null");
        return post((EventGenerator<T>) eventPoster -> {
            for (T event : events) {
                eventPoster.post(event);
            }
        });
    }

    @Override
    public <T> ListenableFuture<Void> post(EventGenerator<T> eventGenerator)
    {
        requireNonNull(eventGenerator, "eventGenerator is null");

        List<URI> uris = serviceSelector.selectHttpService();

        if (uris.isEmpty()) {
            return immediateFailedFuture(new ServiceUnavailableException(serviceSelector.getType(), serviceSelector.getPool()));
        }

        // todo this doesn't really work due to returning the future which can fail without being retried
        Request request = preparePost()
                .setUri(uris.get(0).resolve("/v2/event"))
                .setHeader("User-Agent", nodeInfo.getNodeId())
                .setHeader("Content-Type", MEDIA_TYPE_JSON.toString())
                .setBodyGenerator(new JsonEntityWriter<>(eventWriter, eventGenerator))
                .build();
        return httpClient.executeAsync(request, new EventResponseHandler(serviceSelector.getType(), serviceSelector.getPool()));
    }

    private static class JsonEntityWriter<T>
            implements BodyGenerator
    {
        private final JsonEventWriter eventWriter;
        private final EventGenerator<T> events;

        public JsonEntityWriter(JsonEventWriter eventWriter, EventGenerator<T> events)
        {
            this.eventWriter = requireNonNull(eventWriter, "eventWriter is null");
            this.events = requireNonNull(events, "events is null");
        }

        @Override
        public void write(OutputStream out)
                throws Exception
        {
            eventWriter.writeEvents(events, out);
        }
    }

    private static class EventResponseHandler
            implements ResponseHandler<Void, RuntimeException>
    {
        private final String type;
        private final String pool;

        public EventResponseHandler(String type, String pool)
        {
            this.type = requireNonNull(type, "type is null");
            this.pool = requireNonNull(pool, "pool is null");
        }

        @Override
        public Void handleException(Request request, Exception exception)
        {
            log.debug("Posting event to %s failed", request.getUri());
            throw new EventSubmissionFailedException(type, pool, ImmutableMap.of(request.getUri(), exception));
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
