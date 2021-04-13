/*
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
package io.airlift.security.jwks;

import com.google.common.io.Closer;
import io.airlift.http.client.HttpClient;
import io.airlift.http.client.Request;
import io.airlift.http.client.StringResponseHandler.StringResponse;
import io.airlift.log.Logger;
import io.airlift.units.Duration;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.security.PublicKey;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static io.airlift.concurrent.Threads.daemonThreadsNamed;
import static io.airlift.http.client.Request.Builder.prepareGet;
import static io.airlift.http.client.StringResponseHandler.createStringResponseHandler;
import static io.airlift.security.jwks.JwksDecoder.decodeKeys;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.Executors.newSingleThreadScheduledExecutor;

public final class JwksService
{
    private static final Logger log = Logger.get(JwksService.class);

    private final URI address;
    private final HttpClient httpClient;
    private final Duration refreshDelay;
    private final AtomicReference<Map<String, PublicKey>> keys;

    private Closer closer;

    public JwksService(URI address, HttpClient httpClient, Duration refreshDelay)
    {
        this.address = requireNonNull(address, "address is null");
        this.httpClient = requireNonNull(httpClient, "httpClient is null");
        this.refreshDelay = requireNonNull(refreshDelay, "refreshDelay is null");

        this.keys = new AtomicReference<>(fetchKeys());
    }

    @PostConstruct
    public synchronized void start()
    {
        if (closer != null) {
            return;
        }
        closer = Closer.create();

        ScheduledExecutorService executorService = newSingleThreadScheduledExecutor(daemonThreadsNamed("JWKS loader"));
        closer.register(executorService::shutdownNow);

        ScheduledFuture<?> refreshJob = executorService.scheduleWithFixedDelay(
                () -> {
                    try {
                        refreshKeys();
                    }
                    catch (Throwable e) {
                        log.error(e, "Error fetching JWKS keys");
                    }
                },
                refreshDelay.toMillis(),
                refreshDelay.toMillis(),
                TimeUnit.MILLISECONDS);
        closer.register(() -> refreshJob.cancel(true));
    }

    @PreDestroy
    public synchronized void stop()
    {
        if (closer == null) {
            return;
        }
        try {
            closer.close();
        }
        catch (IOException e) {
            throw new UncheckedIOException("Error stopping JWKS service", e);
        }
        finally {
            closer = null;
        }
    }

    public Map<String, PublicKey> getKeys()
    {
        return keys.get();
    }

    public Optional<PublicKey> getKey(String keyId)
    {
        return Optional.ofNullable(keys.get().get(keyId));
    }

    public void refreshKeys()
    {
        keys.set(fetchKeys());
    }

    private Map<String, PublicKey> fetchKeys()
    {
        Request request = prepareGet().setUri(address).build();
        StringResponse response;
        try {
            response = httpClient.execute(request, createStringResponseHandler());
        }
        catch (RuntimeException e) {
            throw new RuntimeException("Error reading JWKS keys from " + address, e);
        }
        if (response.getStatusCode() != 200) {
            throw new RuntimeException("Unexpected response code " + response.getStatusCode() + " from JWKS service at " + address);
        }
        try {
            return decodeKeys(response.getBody());
        }
        catch (RuntimeException e) {
            throw new RuntimeException("Unable to decode JWKS response from " + address, e);
        }
    }
}
