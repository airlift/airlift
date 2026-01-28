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
package io.airlift.http.client.jetty;

import ch.qos.logback.core.AsyncAppenderBase;
import ch.qos.logback.core.ContextBase;
import ch.qos.logback.core.rolling.RollingFileAppender;
import ch.qos.logback.core.rolling.SizeAndTimeBasedFileNamingAndTriggeringPolicy;
import ch.qos.logback.core.rolling.TimeBasedRollingPolicy;
import ch.qos.logback.core.status.ErrorStatus;
import ch.qos.logback.core.util.FileSize;
import com.google.common.math.LongMath;
import io.airlift.log.Logger;
import io.airlift.units.DataSize;
import io.airlift.units.Duration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicLong;

import static io.airlift.http.client.jetty.HttpRequestEvent.createHttpRequestEvent;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

class DefaultHttpClientLogger
        implements HttpClientLogger
{
    private static final Logger LOG = Logger.get(DefaultHttpClientLogger.class);
    private static final String TEMP_FILE_EXTENSION = ".tmp";
    private static final String LOG_FILE_EXTENSION = ".log";

    private final AsyncAppenderBase<HttpRequestEvent> asyncAppender;

    DefaultHttpClientLogger(
            String filename,
            int maxHistory,
            int queueSize,
            DataSize bufferSize,
            Duration flushInterval,
            long maxFileSizeInBytes,
            boolean compressionEnabled)
    {
        ContextBase context = new ContextBase();
        HttpClientLogLayout httpLogLayout = new HttpClientLogLayout();

        recoverTempFiles(filename);

        FlushingFileAppender<HttpRequestEvent> fileAppender = new FlushingFileAppender<>(flushInterval);
        SizeAndTimeBasedFileNamingAndTriggeringPolicy<HttpRequestEvent> triggeringPolicy = new SizeAndTimeBasedFileNamingAndTriggeringPolicy<>();
        TimeBasedRollingPolicy<HttpRequestEvent> rollingPolicy = new TimeBasedRollingPolicy<>();

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
        rollingPolicy.setTotalSizeCap(new FileSize(LongMath.saturatedMultiply(maxFileSizeInBytes, maxHistory)));

        triggeringPolicy.setContext(context);
        triggeringPolicy.setTimeBasedRollingPolicy(rollingPolicy);
        triggeringPolicy.setMaxFileSize(new FileSize(maxFileSizeInBytes));

        fileAppender.setContext(context);
        fileAppender.setFile(filename);
        fileAppender.setAppend(true);
        fileAppender.setBufferSize(new FileSize(bufferSize.toBytes()));
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

    @Override
    public void log(RequestInfo requestInfo, ResponseInfo responseInfo)
    {
        asyncAppender.doAppend(createHttpRequestEvent(requestInfo, responseInfo));
    }

    @Override
    public void close()
    {
        asyncAppender.stop();
    }

    @Override
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
        File[] tempFiles = logPathFile.listFiles((directory, name) -> name.endsWith(TEMP_FILE_EXTENSION));

        if (tempFiles != null) {
            for (File tempFile : tempFiles) {
                String newName = tempFile.getName().substring(0, tempFile.getName().length() - TEMP_FILE_EXTENSION.length());
                File newFile = Path.of(tempFile.getParent(), newName + LOG_FILE_EXTENSION).toFile();
                if (tempFile.renameTo(newFile)) {
                    LOG.info("Recovered temp file: %s", tempFile);
                }
                else {
                    LOG.warn("Could not rename temp file [%s] to [%s]", tempFile, newFile);
                }
            }
        }
    }

    private static class FlushingFileAppender<T>
            extends RollingFileAppender<T>
    {
        private final AtomicLong lastFlushed = new AtomicLong(System.nanoTime());
        private final long flushIntervalNanos;

        private FlushingFileAppender(Duration flushInterval)
        {
            this.flushIntervalNanos = flushInterval.roundTo(NANOSECONDS);
        }

        @Override
        protected void subAppend(T event)
        {
            super.subAppend(event);

            long now = System.nanoTime();
            long last = lastFlushed.get();
            if (((now - last) > flushIntervalNanos) && lastFlushed.compareAndSet(last, now)) {
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
