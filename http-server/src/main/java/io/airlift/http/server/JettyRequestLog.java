package io.airlift.http.server;

import ch.qos.logback.core.AsyncAppenderBase;
import ch.qos.logback.core.ContextBase;
import ch.qos.logback.core.layout.EchoLayout;
import ch.qos.logback.core.rolling.RollingFileAppender;
import ch.qos.logback.core.rolling.SizeAndTimeBasedFileNamingAndTriggeringPolicy;
import ch.qos.logback.core.rolling.TimeBasedRollingPolicy;
import ch.qos.logback.core.status.ErrorStatus;
import ch.qos.logback.core.util.FileSize;
import io.airlift.log.Logger;
import io.airlift.units.DataSize;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.RequestLog;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.util.NanoTime;
import org.eclipse.jetty.util.component.ContainerLifeCycle;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.security.Principal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicLong;

import static com.google.common.math.LongMath.saturatedMultiply;
import static io.airlift.units.DataSize.Unit.MEGABYTE;
import static java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

public class JettyRequestLog
        extends ContainerLifeCycle
        implements RequestLog
{
    private static final DateTimeFormatter ISO_FORMATTER = ISO_OFFSET_DATE_TIME.withZone(ZoneId.systemDefault());

    private static final Logger log = Logger.get(JettyRequestLog.class);
    private static final String TEMP_FILE_EXTENSION = ".tmp";
    private static final String LOG_FILE_EXTENSION = ".log";
    private static final FileSize BUFFER_SIZE_IN_BYTES = new FileSize(DataSize.of(1, MEGABYTE).toBytes());
    private static final long FLUSH_INTERVAL_NANOS = SECONDS.toNanos(10);

    private final AsyncAppenderBase<String> asyncAppender;
    private final FlushingFileAppender<String> fileAppender;
    private final SizeAndTimeBasedFileNamingAndTriggeringPolicy<String> triggeringPolicy;
    private final TimeBasedRollingPolicy<String> rollingPolicy;

    public JettyRequestLog(String filename, int maxHistory, int queueSize, long maxFileSizeInBytes, boolean compressionEnabled, boolean immediateFlush)
    {
        ContextBase context = new ContextBase();
        recoverTempFiles(filename);

        fileAppender = new FlushingFileAppender<>();
        triggeringPolicy = new SizeAndTimeBasedFileNamingAndTriggeringPolicy<>();
        rollingPolicy = new TimeBasedRollingPolicy<>();

        rollingPolicy.setContext(context);
        rollingPolicy.setMaxHistory(maxHistory); // limits number of logging periods (i.e. days) kept
        rollingPolicy.setTimeBasedFileNamingAndTriggeringPolicy(triggeringPolicy);
        rollingPolicy.setParent(fileAppender);
        rollingPolicy.setFileNamePattern(filename + "-%d{yyyy-MM-dd}.%i.log");
        if (compressionEnabled) {
            rollingPolicy.setFileNamePattern(rollingPolicy.getFileNamePattern() + ".gz");
        }
        // Limit total log files occupancy on disk. Ideally we would keep exactly
        // `maxHistory` files (not logging periods). This is closest currently possible.
        rollingPolicy.setTotalSizeCap(new FileSize(saturatedMultiply(maxFileSizeInBytes, maxHistory)));

        triggeringPolicy.setContext(context);
        triggeringPolicy.setTimeBasedRollingPolicy(rollingPolicy);
        triggeringPolicy.setMaxFileSize(new FileSize(maxFileSizeInBytes));

        fileAppender.setContext(context);
        fileAppender.setFile(filename);
        fileAppender.setAppend(true);
        fileAppender.setBufferSize(BUFFER_SIZE_IN_BYTES);
        fileAppender.setLayout(new EchoLayout<>());
        fileAppender.setRollingPolicy(rollingPolicy);
        fileAppender.setImmediateFlush(immediateFlush);

        asyncAppender = new AsyncAppenderBase<>();
        asyncAppender.setContext(context);
        asyncAppender.setQueueSize(queueSize);
        asyncAppender.addAppender(fileAppender);
    }

    @Override
    public void log(Request request, Response response)
    {
        String user = null;
        Request.AuthenticationState authenticationState = Request.getAuthenticationState(request);
        if (authenticationState != null) {
            Principal principal = authenticationState.getUserPrincipal();
            if (principal != null) {
                user = principal.getName();
            }
        }

        String requestUri = null;
        if (request.getHttpURI() != null) {
            requestUri = request.getHttpURI().getPath();
            String parameters = request.getHttpURI().getQuery();
            if (parameters != null) {
                requestUri += "?" + parameters;
            }
        }

        String line = String.join("\t",
                ISO_FORMATTER.format(Instant.ofEpochMilli(Request.getTimeStamp(request))), // Request timeout
                Request.getRemoteAddr(request), // Client address
                request.getMethod(), // HTTP method
                requestUri, // URL path + queryString
                user, // Authenticated user
                request.getHeaders().get("User-Agent"), // User agent
                Integer.toString(response.getStatus()), // Response code
                Long.toString(Request.getContentBytesRead(request)), // Request size
                Long.toString(Response.getContentBytesWritten(response)), // Response size
                formatLatency(NanoTime.since(request.getBeginNanoTime())), // Time taken to serve in ms
                request.getConnectionMetaData().getProtocol(), // Protocol version
                formatLatency(request.getHeadersNanoTime() - request.getBeginNanoTime()), // Time to dispatch
                formatLatency(NanoTime.since(request.getHeadersNanoTime()))); // Time to completion

        asyncAppender.doAppend(line);
    }

    @Override
    protected void doStart()
    {
        rollingPolicy.start();
        triggeringPolicy.start();
        fileAppender.start();
        asyncAppender.start();
    }

    @Override
    protected void doStop()
    {
        rollingPolicy.stop();
        triggeringPolicy.stop();
        fileAppender.stop();
        asyncAppender.stop();
    }

    public int getQueueSize()
    {
        return asyncAppender.getNumberOfElementsInQueue();
    }

    private static void recoverTempFiles(String logPath)
    {
        // logback has a tendency to leave around temp files if it is interrupted
        // these .tmp files are log files that are about to be compressed.
        // This method recovers them so that they aren't orphaned

        File logPathFile = new File(logPath).getParentFile();
        File[] tempFiles = logPathFile.listFiles((dir, name) -> name.endsWith(TEMP_FILE_EXTENSION));

        if (tempFiles != null) {
            for (File tempFile : tempFiles) {
                String newName = tempFile.getName().substring(0, tempFile.getName().length() - TEMP_FILE_EXTENSION.length());
                File newFile = Path.of(tempFile.getParent(), newName + LOG_FILE_EXTENSION).toFile();
                if (tempFile.renameTo(newFile)) {
                    log.info("Recovered temp file: %s", tempFile);
                }
                else {
                    log.warn("Could not rename temp file [%s] to [%s]", tempFile, newFile);
                }
            }
        }
    }

    private static String formatLatency(long nanoTime)
    {
        return Long.toString(NANOSECONDS.toMillis(nanoTime));
    }

    private static class FlushingFileAppender<T>
            extends RollingFileAppender<T>
    {
        private final AtomicLong lastFlushed = new AtomicLong(System.nanoTime());

        @Override
        protected void subAppend(T event)
        {
            super.subAppend(event);

            long now = System.nanoTime();
            long last = lastFlushed.get();
            if (((now - last) > FLUSH_INTERVAL_NANOS) && lastFlushed.compareAndSet(last, now)) {
                flush();
            }
        }

        @SuppressWarnings("Duplicates")
        private void flush()
        {
            try {
                streamWriteLock.lock();
                try {
                    getOutputStream().flush();
                }
                finally {
                    streamWriteLock.unlock();
                }
            }
            catch (IOException e) {
                started = false;
                addStatus(new ErrorStatus("IO failure in appender", this, e));
            }
        }
    }
}
