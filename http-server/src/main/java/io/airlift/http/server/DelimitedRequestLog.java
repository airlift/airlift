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

import ch.qos.logback.core.AsyncAppenderBase;
import ch.qos.logback.core.ContextBase;
import ch.qos.logback.core.rolling.RollingFileAppender;
import ch.qos.logback.core.rolling.SizeAndTimeBasedFNATP;
import ch.qos.logback.core.rolling.TimeBasedRollingPolicy;
import ch.qos.logback.core.status.ErrorStatus;
import ch.qos.logback.core.util.FileSize;
import io.airlift.event.client.EventClient;
import io.airlift.log.Logger;
import io.airlift.tracetoken.TraceTokenManager;
import io.airlift.units.DataSize;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicLong;

import static io.airlift.http.server.HttpRequestEvent.createHttpRequestEvent;
import static io.airlift.units.DataSize.Unit.MEGABYTE;
import static java.util.concurrent.TimeUnit.SECONDS;

class DelimitedRequestLog
{
    private static final Logger log = Logger.get(DelimitedRequestLog.class);
    private static final String TEMP_FILE_EXTENSION = ".tmp";
    private static final String LOG_FILE_EXTENSION = ".log";
    private static final FileSize BUFFER_SIZE_IN_BYTES = new FileSize(new DataSize(1, MEGABYTE).toBytes());
    private static final long FLUSH_INTERVAL_NANOS = SECONDS.toNanos(10);

    // Tab-separated
    // Time, ip, method, url, user, agent, response code, request length, response length, response time
    private final TraceTokenManager traceTokenManager;
    private final EventClient eventClient;
    private final CurrentTimeMillisProvider currentTimeMillisProvider;
    private final AsyncAppenderBase<HttpRequestEvent> asyncAppender;

    public DelimitedRequestLog(
            String filename,
            int maxHistory,
            int queueSize,
            long maxFileSizeInBytes,
            TraceTokenManager traceTokenManager,
            EventClient eventClient,
            boolean compressionEnabled)
    {
        this(filename, maxHistory, queueSize, maxFileSizeInBytes, traceTokenManager, eventClient, new SystemCurrentTimeMillisProvider(), compressionEnabled);
    }

    public DelimitedRequestLog(
            String filename,
            int maxHistory,
            int queueSize,
            long maxFileSizeInBytes,
            TraceTokenManager traceTokenManager,
            EventClient eventClient,
            CurrentTimeMillisProvider currentTimeMillisProvider,
            boolean compressionEnabled)
    {
        this.traceTokenManager = traceTokenManager;
        this.eventClient = eventClient;
        this.currentTimeMillisProvider = currentTimeMillisProvider;

        ContextBase context = new ContextBase();
        HttpLogLayout httpLogLayout = new HttpLogLayout();

        recoverTempFiles(filename);

        FlushingFileAppender<HttpRequestEvent> fileAppender = new FlushingFileAppender<>();
        SizeAndTimeBasedFNATP<HttpRequestEvent> triggeringPolicy = new SizeAndTimeBasedFNATP<>();
        TimeBasedRollingPolicy<HttpRequestEvent> rollingPolicy = new TimeBasedRollingPolicy<>();

        rollingPolicy.setContext(context);
        rollingPolicy.setMaxHistory(maxHistory);
        rollingPolicy.setTimeBasedFileNamingAndTriggeringPolicy(triggeringPolicy);
        rollingPolicy.setParent(fileAppender);
        rollingPolicy.setFileNamePattern(filename + "-%d{yyyy-MM-dd}.%i.log");
        if (compressionEnabled) {
            rollingPolicy.setFileNamePattern(rollingPolicy.getFileNamePattern() + ".gz");
        }

        triggeringPolicy.setContext(context);
        triggeringPolicy.setTimeBasedRollingPolicy(rollingPolicy);
        triggeringPolicy.setMaxFileSize(new FileSize(maxFileSizeInBytes));

        fileAppender.setContext(context);
        fileAppender.setFile(filename);
        fileAppender.setAppend(true);
        fileAppender.setBufferSize(BUFFER_SIZE_IN_BYTES);
        fileAppender.setLayout(httpLogLayout);
        fileAppender.setRollingPolicy(rollingPolicy);
        fileAppender.setImmediateFlush(false);

        asyncAppender = new AsyncAppenderBase<>();
        asyncAppender.setContext(context);
        asyncAppender.setQueueSize(queueSize);
        asyncAppender.addAppender(fileAppender);

        rollingPolicy.start();
        triggeringPolicy.start();
        fileAppender.start();
        asyncAppender.start();
    }

    public void log(
            Request request,
            Response response,
            long beginToDispatchMillis,
            long beginToEndMillis,
            long firstToLastContentTimeInMillis,
            DoubleSummaryStats responseContentInterarrivalStats)
    {
        HttpRequestEvent event = createHttpRequestEvent(
                request,
                response,
                traceTokenManager,
                currentTimeMillisProvider.getCurrentTimeMillis(),
                beginToDispatchMillis,
                beginToEndMillis,
                firstToLastContentTimeInMillis,
                responseContentInterarrivalStats);

        asyncAppender.doAppend(event);

        eventClient.post(event);
    }

    public void stop()
    {
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
                File newFile = new File(tempFile.getParent(), newName + LOG_FILE_EXTENSION);
                if (tempFile.renameTo(newFile)) {
                    log.info("Recovered temp file: %s", tempFile);
                }
                else {
                    log.warn("Could not rename temp file [%s] to [%s]", tempFile, newFile);
                }
            }
        }
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
                lock.lock();
                try {
                    getOutputStream().flush();
                }
                finally {
                    lock.unlock();
                }
            }
            catch (IOException e) {
                started = false;
                addStatus(new ErrorStatus("IO failure in appender", this, e));
            }
        }
    }
}
