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
package io.airlift.http.server;

import static io.airlift.configuration.testing.ConfigAssertions.assertFullMapping;
import static io.airlift.configuration.testing.ConfigAssertions.assertRecordedDefaults;
import static io.airlift.configuration.testing.ConfigAssertions.recordDefaults;
import static io.airlift.http.server.HttpServerConfig.ProcessForwardedMode.IGNORE;
import static io.airlift.http.server.HttpServerConfig.ProcessForwardedMode.REJECT;
import static io.airlift.units.DataSize.Unit.GIGABYTE;
import static io.airlift.units.DataSize.Unit.KILOBYTE;
import static io.airlift.units.DataSize.Unit.MEGABYTE;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;

import com.google.common.collect.ImmutableMap;
import io.airlift.units.DataSize;
import io.airlift.units.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class TestHttpServerConfig {
    @Test
    public void testDefaults() {
        assertRecordedDefaults(recordDefaults(HttpServerConfig.class)
                .setHttpEnabled(true)
                .setHttpPort(8080)
                .setHttpAcceptQueueSize(8000)
                .setHttpsEnabled(false)
                .setProcessForwarded(REJECT)
                .setLogPath("var/log/http-request.log")
                .setLogEnabled(true)
                .setLogMaxFileSize(DataSize.of(100, MEGABYTE))
                .setLogHistory(15)
                .setLogQueueSize(10_000)
                .setLogCompressionEnabled(true)
                .setLogImmediateFlush(false)
                .setHttpAcceptorThreads(null)
                .setHttpSelectorThreads(null)
                .setHttpsAcceptorThreads(null)
                .setHttpsSelectorThreads(null)
                .setMinThreads(2)
                .setMaxThreads(200)
                .setThreadMaxIdleTime(new Duration(1, MINUTES))
                .setNetworkMaxIdleTime(new Duration(200, SECONDS))
                .setStopTimeout(new Duration(0, SECONDS))
                .setMaxRequestHeaderSize(null)
                .setMaxResponseHeaderSize(null)
                .setOutputBufferSize(null)
                .setHttp2MaxConcurrentStreams(16384)
                .setShowStackTrace(true)
                .setHttp2InitialSessionReceiveWindowSize(DataSize.of(16, MEGABYTE))
                .setHttp2InputBufferSize(DataSize.of(8, KILOBYTE))
                .setHttp2InitialStreamReceiveWindowSize(DataSize.of(16, MEGABYTE))
                .setHttp2StreamIdleTimeout(new Duration(15, SECONDS))
                .setCompressionEnabled(true)
                .setMaxHeapMemory(null)
                .setMaxDirectMemory(null)
                .setNotifyRemoteAsyncErrors(false)
                .setTrackMemoryAllocations(false));
    }

    @Test
    public void testExplicitPropertyMappings() {
        Map<String, String> properties = new ImmutableMap.Builder<String, String>()
                .put("http-server.http.enabled", "false")
                .put("http-server.http.port", "1")
                .put("http-server.accept-queue-size", "1024")
                .put("http-server.https.enabled", "true")
                .put("http-server.process-forwarded", "ignore")
                .put("http-server.log.path", "/log")
                .put("http-server.log.enabled", "false")
                .put("http-server.log.max-size", "1GB")
                .put("http-server.log.max-history", "1")
                .put("http-server.log.queue-size", "1")
                .put("http-server.log.compression.enabled", "false")
                .put("http-server.log.immediate-flush", "true")
                .put("http-server.http.acceptor-threads", "10")
                .put("http-server.http.selector-threads", "11")
                .put("http-server.https.acceptor-threads", "12")
                .put("http-server.https.selector-threads", "13")
                .put("http-server.threads.min", "100")
                .put("http-server.threads.max", "500")
                .put("http-server.threads.max-idle-time", "10m")
                .put("http-server.net.max-idle-time", "20m")
                .put("http-server.stop-timeout", "30s")
                .put("http-server.max-request-header-size", "32kB")
                .put("http-server.max-response-header-size", "57kB")
                .put("http-server.output-buffer-size", "1MB")
                .put("http-server.http2.max-concurrent-streams", "1234")
                .put("http-server.show-stack-trace", "false")
                .put("http-server.http2.session-receive-window-size", "4MB")
                .put("http-server.http2.stream-receive-window-size", "4MB")
                .put("http-server.http2.input-buffer-size", "4MB")
                .put("http-server.http2.stream-idle-timeout", "23s")
                .put("http-server.compression.enabled", "false")
                .put("http-server.max-heap-memory", "127GB")
                .put("http-server.max-direct-memory", "129GB")
                .put("http-server.notify-remote-async-errors", "true")
                .put("http-server.track-memory-allocations", "true")
                .build();

        HttpServerConfig expected = new HttpServerConfig()
                .setHttpEnabled(false)
                .setHttpPort(1)
                .setHttpAcceptQueueSize(1024)
                .setHttpsEnabled(true)
                .setProcessForwarded(IGNORE)
                .setLogPath("/log")
                .setLogEnabled(false)
                .setLogMaxFileSize(DataSize.of(1, GIGABYTE))
                .setLogHistory(1)
                .setLogQueueSize(1)
                .setLogCompressionEnabled(false)
                .setLogImmediateFlush(true)
                .setHttpAcceptorThreads(10)
                .setHttpSelectorThreads(11)
                .setHttpsAcceptorThreads(12)
                .setHttpsSelectorThreads(13)
                .setMinThreads(100)
                .setMaxThreads(500)
                .setThreadMaxIdleTime(new Duration(10, MINUTES))
                .setNetworkMaxIdleTime(new Duration(20, MINUTES))
                .setStopTimeout(new Duration(30, SECONDS))
                .setMaxRequestHeaderSize(DataSize.of(32, KILOBYTE))
                .setMaxResponseHeaderSize(DataSize.of(57, KILOBYTE))
                .setOutputBufferSize(DataSize.of(1, MEGABYTE))
                .setHttp2MaxConcurrentStreams(1234)
                .setShowStackTrace(false)
                .setHttp2InitialSessionReceiveWindowSize(DataSize.of(4, MEGABYTE))
                .setHttp2InitialStreamReceiveWindowSize(DataSize.of(4, MEGABYTE))
                .setHttp2InputBufferSize(DataSize.of(4, MEGABYTE))
                .setHttp2StreamIdleTimeout(new Duration(23, SECONDS))
                .setCompressionEnabled(false)
                .setMaxHeapMemory(DataSize.of(127, GIGABYTE))
                .setMaxDirectMemory(DataSize.of(129, GIGABYTE))
                .setNotifyRemoteAsyncErrors(true)
                .setTrackMemoryAllocations(true);

        assertFullMapping(properties, expected);
    }
}
