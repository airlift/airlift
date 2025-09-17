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

import io.airlift.configuration.Config;
import io.airlift.configuration.ConfigDescription;
import io.airlift.configuration.ConfigHidden;
import io.airlift.configuration.DefunctConfig;
import io.airlift.units.DataSize;
import io.airlift.units.Duration;
import io.airlift.units.MaxDataSize;
import io.airlift.units.MinDataSize;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.Optional;

import static io.airlift.http.server.HttpServerConfig.ProcessForwardedMode.ACCEPT;
import static io.airlift.http.server.HttpServerConfig.ProcessForwardedMode.REJECT;
import static io.airlift.units.DataSize.Unit.KILOBYTE;
import static io.airlift.units.DataSize.Unit.MEGABYTE;
import static java.util.Locale.ENGLISH;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;

@DefunctConfig({
        "jetty.http.enabled",
        "jetty.http.port",
        "jetty.https.enabled",
        "jetty.https.port",
        "jetty.https.keystore.path",
        "jetty.https.keystore.password",
        "jetty.log.path",
        "jetty.log.retain-days",
        "jetty.threads.min",
        "jetty.threads.max",
        "jetty.threads.max-idle-time-ms",
        "jetty.net.max-idle-time-ms",
        "jetty.auth.users-file",
        "http-server.https.keystore.password",
        "http-server.log.retention-time",
        "http-server.admin.enabled",
        "http-server.admin.port",
        "http-server.admin.threads.min",
        "http-server.admin.threads.max",
        "http-server.auth.users-file",
        "http-server.buffer-pool-type",
})
public class HttpServerConfig
{
    private boolean httpEnabled = true;
    private int httpPort = 8080;
    private int httpAcceptQueueSize = 8000;

    private boolean httpsEnabled;

    private String logPath = "var/log/http-request.log";
    private boolean logEnabled = true;
    private int logHistory = 15;
    private int logQueueSize = 10_000;
    private DataSize logMaxFileSize = DataSize.of(100, MEGABYTE);
    private boolean logCompressionEnabled = true;
    private boolean logImmediateFlush;

    private ProcessForwardedMode processForwarded = REJECT;

    private Integer httpAcceptorThreads;
    private Integer httpSelectorThreads;
    private Integer httpsAcceptorThreads;
    private Integer httpsSelectorThreads;

    private int minThreads = 2;
    private int maxThreads = 200;
    private Duration threadMaxIdleTime = new Duration(1, MINUTES);
    private Duration networkMaxIdleTime = new Duration(200, SECONDS);
    private DataSize maxRequestHeaderSize;
    private DataSize maxResponseHeaderSize;
    private DataSize outputBufferSize;
    private int http2MaxConcurrentStreams = 16384;
    private DataSize http2InitialSessionReceiveWindowSize = DataSize.of(16, MEGABYTE);
    private DataSize http2InitialStreamReceiveWindowSize = DataSize.of(16, MEGABYTE);
    private DataSize http2InputBufferSize = DataSize.of(8, KILOBYTE);
    private Duration http2StreamIdleTimeout = new Duration(15, SECONDS);

    private boolean showStackTrace = true;

    private boolean compressionEnabled;

    private Optional<DataSize> maxHeapMemory = Optional.empty();
    private Optional<DataSize> maxDirectMemory = Optional.empty();
    private boolean notifyRemoteAsyncErrors = true;

    public boolean isHttpEnabled()
    {
        return httpEnabled;
    }

    @Config("http-server.http.enabled")
    public HttpServerConfig setHttpEnabled(boolean httpEnabled)
    {
        this.httpEnabled = httpEnabled;
        return this;
    }

    public int getHttpPort()
    {
        return httpPort;
    }

    @Config("http-server.accept-queue-size")
    public HttpServerConfig setHttpAcceptQueueSize(int httpAcceptQueueSize)
    {
        this.httpAcceptQueueSize = httpAcceptQueueSize;
        return this;
    }

    public int getHttpAcceptQueueSize()
    {
        return httpAcceptQueueSize;
    }

    @Config("http-server.http.port")
    public HttpServerConfig setHttpPort(int httpPort)
    {
        this.httpPort = httpPort;
        return this;
    }

    public boolean isHttpsEnabled()
    {
        return httpsEnabled;
    }

    @Config("http-server.https.enabled")
    public HttpServerConfig setHttpsEnabled(boolean httpsEnabled)
    {
        this.httpsEnabled = httpsEnabled;
        return this;
    }

    public String getLogPath()
    {
        return logPath;
    }

    @Config("http-server.log.path")
    public HttpServerConfig setLogPath(String logPath)
    {
        this.logPath = logPath;
        return this;
    }

    public boolean isLogEnabled()
    {
        return logEnabled;
    }

    @Config("http-server.log.enabled")
    public HttpServerConfig setLogEnabled(boolean logEnabled)
    {
        this.logEnabled = logEnabled;
        return this;
    }

    public DataSize getLogMaxFileSize()
    {
        return logMaxFileSize;
    }

    @Config("http-server.log.max-size")
    public HttpServerConfig setLogMaxFileSize(DataSize logMaxFileSize)
    {
        this.logMaxFileSize = logMaxFileSize;
        return this;
    }

    public ProcessForwardedMode getProcessForwarded()
    {
        return processForwarded;
    }

    @Config("http-server.process-forwarded")
    @ConfigDescription("Process Forwarded and X-Forwarded headers (for proxied environments)")
    public HttpServerConfig setProcessForwarded(ProcessForwardedMode processForwarded)
    {
        this.processForwarded = processForwarded;
        return this;
    }

    @Deprecated
    // temporary boolean based setter to provide binary compatibility
    // with code which used older version of Airlift before setProcessForwarded(ProcessForwardedMode)
    // was added.
    // Slated for removal in a couple releases
    public HttpServerConfig setProcessForwarded(boolean processForwareded)
    {
        return setProcessForwarded(processForwareded ? ACCEPT : REJECT);
    }

    @Min(1)
    public Integer getHttpAcceptorThreads()
    {
        return httpAcceptorThreads;
    }

    @Config("http-server.http.acceptor-threads")
    public HttpServerConfig setHttpAcceptorThreads(Integer httpAcceptorThreads)
    {
        this.httpAcceptorThreads = httpAcceptorThreads;
        return this;
    }

    @Min(1)
    public Integer getHttpSelectorThreads()
    {
        return httpSelectorThreads;
    }

    @Config("http-server.http.selector-threads")
    public HttpServerConfig setHttpSelectorThreads(Integer httpSelectorThreads)
    {
        this.httpSelectorThreads = httpSelectorThreads;
        return this;
    }

    @Min(1)
    public Integer getHttpsAcceptorThreads()
    {
        return httpsAcceptorThreads;
    }

    @Config("http-server.https.acceptor-threads")
    public HttpServerConfig setHttpsAcceptorThreads(Integer httpsAcceptorThreads)
    {
        this.httpsAcceptorThreads = httpsAcceptorThreads;
        return this;
    }

    @Min(1)
    public Integer getHttpsSelectorThreads()
    {
        return httpsSelectorThreads;
    }

    @Config("http-server.https.selector-threads")
    public HttpServerConfig setHttpsSelectorThreads(Integer httpsSelectorThreads)
    {
        this.httpsSelectorThreads = httpsSelectorThreads;
        return this;
    }

    public int getMaxThreads()
    {
        return maxThreads;
    }

    @Config("http-server.threads.max")
    public HttpServerConfig setMaxThreads(int maxThreads)
    {
        this.maxThreads = maxThreads;
        return this;
    }

    public int getMinThreads()
    {
        return minThreads;
    }

    @Config("http-server.threads.min")
    public HttpServerConfig setMinThreads(int minThreads)
    {
        this.minThreads = minThreads;
        return this;
    }

    public Duration getThreadMaxIdleTime()
    {
        return threadMaxIdleTime;
    }

    @Config("http-server.threads.max-idle-time")
    public HttpServerConfig setThreadMaxIdleTime(Duration threadMaxIdleTime)
    {
        this.threadMaxIdleTime = threadMaxIdleTime;
        return this;
    }

    public int getLogHistory()
    {
        return logHistory;
    }

    @Config("http-server.log.max-history")
    public HttpServerConfig setLogHistory(int logHistory)
    {
        this.logHistory = logHistory;
        return this;
    }

    @Min(1)
    public int getLogQueueSize()
    {
        return logQueueSize;
    }

    @Config("http-server.log.queue-size")
    public HttpServerConfig setLogQueueSize(int logQueueSize)
    {
        this.logQueueSize = logQueueSize;
        return this;
    }

    public boolean isLogCompressionEnabled()
    {
        return logCompressionEnabled;
    }

    @Config("http-server.log.compression.enabled")
    public HttpServerConfig setLogCompressionEnabled(boolean logCompressionEnabled)
    {
        this.logCompressionEnabled = logCompressionEnabled;
        return this;
    }

    public boolean isLogImmediateFlush()
    {
        return logImmediateFlush;
    }

    @Config("http-server.log.immediate-flush")
    public HttpServerConfig setLogImmediateFlush(boolean logImmediateFlush)
    {
        this.logImmediateFlush = logImmediateFlush;
        return this;
    }

    public Duration getNetworkMaxIdleTime()
    {
        return networkMaxIdleTime;
    }

    @Config("http-server.net.max-idle-time")
    public HttpServerConfig setNetworkMaxIdleTime(Duration networkMaxIdleTime)
    {
        this.networkMaxIdleTime = networkMaxIdleTime;
        return this;
    }

    public DataSize getMaxRequestHeaderSize()
    {
        return maxRequestHeaderSize;
    }

    @Config("http-server.max-request-header-size")
    public HttpServerConfig setMaxRequestHeaderSize(DataSize maxRequestHeaderSize)
    {
        this.maxRequestHeaderSize = maxRequestHeaderSize;
        return this;
    }

    public DataSize getMaxResponseHeaderSize()
    {
        return maxResponseHeaderSize;
    }

    @Config("http-server.max-response-header-size")
    public HttpServerConfig setMaxResponseHeaderSize(DataSize maxResponseHeaderSize)
    {
        this.maxResponseHeaderSize = maxResponseHeaderSize;
        return this;
    }

    public DataSize getOutputBufferSize()
    {
        return outputBufferSize;
    }

    @Config("http-server.output-buffer-size")
    public HttpServerConfig setOutputBufferSize(DataSize outputBufferSize)
    {
        this.outputBufferSize = outputBufferSize;
        return this;
    }

    @Min(1)
    public int getHttp2MaxConcurrentStreams()
    {
        return http2MaxConcurrentStreams;
    }

    @Config("http-server.http2.max-concurrent-streams")
    @ConfigDescription("Maximum concurrent streams per connection for HTTP/2")
    public HttpServerConfig setHttp2MaxConcurrentStreams(int http2MaxConcurrentStreams)
    {
        this.http2MaxConcurrentStreams = http2MaxConcurrentStreams;
        return this;
    }

    public boolean isShowStackTrace()
    {
        return showStackTrace;
    }

    @Config("http-server.show-stack-trace")
    @ConfigDescription("Show the stack trace when generating an error response")
    public HttpServerConfig setShowStackTrace(boolean showStackTrace)
    {
        this.showStackTrace = showStackTrace;
        return this;
    }

    @NotNull
    @MinDataSize("1kB")
    @MaxDataSize("1GB")
    public DataSize getHttp2InitialSessionReceiveWindowSize()
    {
        return http2InitialSessionReceiveWindowSize;
    }

    @Config("http-server.http2.session-receive-window-size")
    @ConfigDescription("Initial size of session's flow control receive window for HTTP/2")
    public HttpServerConfig setHttp2InitialSessionReceiveWindowSize(DataSize http2InitialSessionReceiveWindowSize)
    {
        this.http2InitialSessionReceiveWindowSize = http2InitialSessionReceiveWindowSize;
        return this;
    }

    @NotNull
    @MinDataSize("1kB")
    @MaxDataSize("1GB")
    public DataSize getHttp2InitialStreamReceiveWindowSize()
    {
        return http2InitialStreamReceiveWindowSize;
    }

    @Config("http-server.http2.stream-receive-window-size")
    @ConfigDescription("Initial size of stream's flow control receive window for HTTP/2")
    public HttpServerConfig setHttp2InitialStreamReceiveWindowSize(DataSize http2InitialStreamReceiveWindowSize)
    {
        this.http2InitialStreamReceiveWindowSize = http2InitialStreamReceiveWindowSize;
        return this;
    }

    @NotNull
    @MinDataSize("1kB")
    @MaxDataSize("32MB")
    public DataSize getHttp2InputBufferSize()
    {
        return http2InputBufferSize;
    }

    @Config("http-server.http2.input-buffer-size")
    @ConfigDescription("Size of the buffer used to read from the network for HTTP/2")
    public HttpServerConfig setHttp2InputBufferSize(DataSize http2InputBufferSize)
    {
        this.http2InputBufferSize = http2InputBufferSize;
        return this;
    }

    public Duration getHttp2StreamIdleTimeout()
    {
        return http2StreamIdleTimeout;
    }

    @Config("http-server.http2.stream-idle-timeout")
    public HttpServerConfig setHttp2StreamIdleTimeout(Duration http2StreamIdleTimeout)
    {
        this.http2StreamIdleTimeout = http2StreamIdleTimeout;
        return this;
    }

    public boolean isCompressionEnabled()
    {
        return compressionEnabled;
    }

    @Config("http-server.compression.enabled")
    public HttpServerConfig setCompressionEnabled(boolean compressionEnabled)
    {
        this.compressionEnabled = compressionEnabled;
        return this;
    }

    public Optional<@MinDataSize("8MB") DataSize> getMaxHeapMemory()
    {
        return maxHeapMemory;
    }

    @Config("http-server.max-heap-memory")
    @ConfigHidden
    public HttpServerConfig setMaxHeapMemory(DataSize maxHeapMemory)
    {
        this.maxHeapMemory = Optional.ofNullable(maxHeapMemory);
        return this;
    }

    public Optional<@MinDataSize("8MB") DataSize> getMaxDirectMemory()
    {
        return maxDirectMemory;
    }

    @Config("http-server.max-direct-memory")
    @ConfigHidden
    public HttpServerConfig setMaxDirectMemory(DataSize maxDirectMemory)
    {
        this.maxDirectMemory = Optional.ofNullable(maxDirectMemory);
        return this;
    }

    @AssertTrue(message = "either both http-server.max-heap-memory and http-server.max-direct-memory are set or none of them")
    public boolean eitherBothMemorySettingsAreSetOrNone()
    {
        return maxHeapMemory.isPresent() == maxDirectMemory.isPresent();
    }

    public boolean isNotifyRemoteAsyncErrors()
    {
        return notifyRemoteAsyncErrors;
    }

    @Config("http-server.notify-remote-async-errors")
    @ConfigDescription("Should remote exceptions be passed to AsyncContext")
    public HttpServerConfig setNotifyRemoteAsyncErrors(boolean notifyRemoteAsyncErrors)
    {
        this.notifyRemoteAsyncErrors = notifyRemoteAsyncErrors;
        return this;
    }

    public enum ProcessForwardedMode
    {
        ACCEPT,
        REJECT,
        IGNORE;

        public static ProcessForwardedMode fromString(String value)
        {
            return switch (value.toUpperCase(ENGLISH)) {
                case "TRUE" -> ACCEPT;
                case "FALSE" -> REJECT;
                default -> valueOf(value.toUpperCase(ENGLISH));
            };
        }
    }
}
