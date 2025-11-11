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
package io.airlift.discovery.client;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.io.CharStreams;
import com.google.common.net.HttpHeaders;
import com.google.common.net.MediaType;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.inject.Inject;
import io.airlift.http.client.CacheControl;
import io.airlift.http.client.HttpClient;
import io.airlift.http.client.Request;
import io.airlift.http.client.Response;
import io.airlift.http.client.ResponseHandler;
import io.airlift.json.JsonCodec;
import io.airlift.log.Logger;
import io.airlift.node.NodeInfo;
import io.airlift.units.Duration;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static com.google.common.util.concurrent.Futures.immediateFailedFuture;
import static com.google.common.util.concurrent.Futures.immediateFuture;
import static io.airlift.http.client.HttpUriBuilder.uriBuilderFrom;
import static io.airlift.http.client.JsonBodyGenerator.jsonBodyGenerator;
import static io.airlift.http.client.Request.Builder.prepareDelete;
import static io.airlift.http.client.Request.Builder.preparePut;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;

public class HttpDiscoveryAnnouncementClient
        implements DiscoveryAnnouncementClient
{
    private static final MediaType MEDIA_TYPE_JSON = MediaType.create("application", "json");
    private static final Logger log = Logger.get(HttpDiscoveryAnnouncementClient.class);

    private final Supplier<URI> discoveryServiceURI;
    private final NodeInfo nodeInfo;
    private final JsonCodec<Announcement> announcementCodec;
    private final HttpClient httpClient;
    private Optional<InetAddress> lastReportedDiscoveryServiceAddress = Optional.empty();
    private boolean priorResolutionSucceeded = true;

    @Inject
    public HttpDiscoveryAnnouncementClient(
            @ForDiscoveryClient Supplier<URI> discoveryServiceURI,
            NodeInfo nodeInfo,
            JsonCodec<Announcement> announcementCodec,
            @ForDiscoveryClient HttpClient httpClient)
    {
        requireNonNull(discoveryServiceURI, "discoveryServiceURI is null");
        requireNonNull(nodeInfo, "nodeInfo is null");
        requireNonNull(announcementCodec, "announcementCodec is null");
        requireNonNull(httpClient, "httpClient is null");

        this.nodeInfo = nodeInfo;
        this.discoveryServiceURI = discoveryServiceURI;
        this.announcementCodec = announcementCodec;
        this.httpClient = httpClient;
    }

    @Override
    public ListenableFuture<Duration> announce(Set<ServiceAnnouncement> services)
    {
        requireNonNull(services, "services is null");

        URI uri = discoveryServiceURI.get();
        if (uri == null) {
            return immediateFailedFuture(new DiscoveryException("No discovery servers are available"));
        }

        reportChangedAddressResolution(uri, lastReportedDiscoveryServiceAddress, priorResolutionSucceeded).ifPresentOrElse(
                inetAddr -> {
                    lastReportedDiscoveryServiceAddress = Optional.of(inetAddr);
                    priorResolutionSucceeded = true;
                },
                () -> priorResolutionSucceeded = false);

        Announcement announcement = new Announcement(nodeInfo.getEnvironment(), nodeInfo.getNodeId(), nodeInfo.getPool(), nodeInfo.getLocation(), services);
        Request request = preparePut()
                .setUri(createAnnouncementLocation(uri, nodeInfo.getNodeId()))
                .setHeader("User-Agent", nodeInfo.getNodeId())
                .setHeader("Content-Type", MEDIA_TYPE_JSON.toString())
                .setBodyGenerator(jsonBodyGenerator(announcementCodec, announcement))
                .build();
        return httpClient.executeAsync(request, new DiscoveryResponseHandler<>("Announcement", uri)
        {
            @Override
            public Duration handle(Request request, Response response)
                    throws DiscoveryException
            {
                int statusCode = response.getStatusCode();
                if (!isSuccess(statusCode)) {
                    throw new DiscoveryException(String.format("Announcement failed with status code %s: %s", statusCode, getBodyForError(response)));
                }

                return extractMaxAge(response);
            }
        });
    }

    private static boolean isSuccess(int statusCode)
    {
        return statusCode / 100 == 2;
    }

    private static String getBodyForError(Response response)
    {
        try {
            return CharStreams.toString(new InputStreamReader(response.getInputStream(), UTF_8));
        }
        catch (IOException e) {
            return "(error getting body)";
        }
    }

    @Override
    public ListenableFuture<Void> unannounce()
    {
        URI uri = discoveryServiceURI.get();
        if (uri == null) {
            return immediateFuture(null);
        }

        Request request = prepareDelete()
                .setUri(createAnnouncementLocation(uri, nodeInfo.getNodeId()))
                .setHeader("User-Agent", nodeInfo.getNodeId())
                .build();
        return httpClient.executeAsync(request, new DiscoveryResponseHandler<>("Unannouncement", uri));
    }

    @VisibleForTesting
    static URI createAnnouncementLocation(URI baseUri, String nodeId)
    {
        return uriBuilderFrom(baseUri)
                .appendPath("/v1/announcement")
                .appendPath(nodeId)
                .build();
    }

    @VisibleForTesting
    static Optional<InetAddress> reportChangedAddressResolution(
            URI discoveryServiceURI,
            Optional<InetAddress> lastReportedDiscoveryServiceAddress,
            boolean priorResolutionSucceeded)
    {
        try {
            InetAddress resolvedDiscoveryServiceAddress = InetAddress.getByName(discoveryServiceURI.getHost());
            lastReportedDiscoveryServiceAddress.ifPresent(last -> {
                if (!last.equals(resolvedDiscoveryServiceAddress)) {
                    log.warn("Discovery service address changed from " + last + " to " + resolvedDiscoveryServiceAddress);
                }
            });
            return Optional.of(resolvedDiscoveryServiceAddress);
        }
        catch (UnknownHostException e) {
            // Avoid spamming the log
            if (priorResolutionSucceeded) {
                log.error(e, "Discovery Service location URI resolution failed");
            }
        }
        return Optional.empty();
    }

    private static Duration extractMaxAge(Response response)
    {
        String header = response.getHeader(HttpHeaders.CACHE_CONTROL);
        if (header != null) {
            CacheControl cacheControl = CacheControl.valueOf(header);
            if (cacheControl.getMaxAge() > 0) {
                return new Duration(cacheControl.getMaxAge(), TimeUnit.SECONDS);
            }
        }
        return DEFAULT_DELAY;
    }

    private static class DiscoveryResponseHandler<T>
            implements ResponseHandler<T, DiscoveryException>
    {
        private final String name;
        private final URI uri;

        protected DiscoveryResponseHandler(String name, URI uri)
        {
            this.name = name;
            this.uri = uri;
        }

        @Override
        public T handle(Request request, Response response)
        {
            return null;
        }

        @Override
        public final T handleException(Request request, Exception exception)
        {
            if (exception instanceof InterruptedException) {
                throw new DiscoveryException(name + " was interrupted for " + uri);
            }
            if (exception instanceof CancellationException) {
                throw new DiscoveryException(name + " was canceled for " + uri);
            }
            if (exception instanceof DiscoveryException) {
                throw (DiscoveryException) exception;
            }

            throw new DiscoveryException(name + " failed for " + uri, exception);
        }
    }
}
