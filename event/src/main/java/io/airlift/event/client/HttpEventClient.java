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

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.CharStreams;
import com.google.common.util.concurrent.CheckedFuture;
import com.google.common.util.concurrent.Futures;
import com.google.inject.Inject;
import io.airlift.discovery.client.HttpServiceSelector;
import io.airlift.discovery.client.ServiceType;
import io.airlift.http.client.AsyncHttpClient;
import io.airlift.http.client.BodyGenerator;
import io.airlift.http.client.Request;
import io.airlift.http.client.RequestStats;
import io.airlift.http.client.Response;
import io.airlift.http.client.ResponseHandler;
import io.airlift.log.Logger;
import io.airlift.node.NodeInfo;
import org.weakref.jmx.Flatten;
import org.weakref.jmx.Managed;

import javax.ws.rs.core.MediaType;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URI;
import java.util.Arrays;
import java.util.List;

import static io.airlift.http.client.Request.Builder.preparePost;

public class HttpEventClient
        implements EventClient
{
    private static final Logger log = Logger.get(HttpEventClient.class);

    private final HttpServiceSelector serviceSelector;
    private final JsonEventWriter eventWriter;
    private final int version;
    private final AsyncHttpClient httpClient;
    private final NodeInfo nodeInfo;

    @Inject
    public HttpEventClient(
            @ServiceType("event") HttpServiceSelector v1ServiceSelector,
            @ServiceType("collector") HttpServiceSelector serviceSelector,
            JsonEventWriter eventWriter,
            NodeInfo nodeInfo,
            HttpEventClientConfig config,
            @ForEventClient AsyncHttpClient httpClient)
    {
        Preconditions.checkNotNull(serviceSelector, "serviceSelector is null");
        Preconditions.checkNotNull(v1ServiceSelector, "v1ServiceSelector is null");
        Preconditions.checkNotNull(nodeInfo, "nodeInfo is null");
        Preconditions.checkNotNull(httpClient, "httpClient is null");

        this.eventWriter = eventWriter;
        this.version = config.getJsonVersion();
        this.nodeInfo = nodeInfo;
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
    }

    @Flatten
    @Managed
    public RequestStats getStats()
    {
        return httpClient.getStats();
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
                .setHeader("User-Agent", nodeInfo.getNodeId())
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
