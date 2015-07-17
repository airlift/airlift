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

import ch.qos.logback.core.ContextBase;
import ch.qos.logback.core.rolling.RollingFileAppender;
import ch.qos.logback.core.rolling.SizeAndTimeBasedFNATP;
import ch.qos.logback.core.rolling.TimeBasedRollingPolicy;
import io.airlift.event.client.EventClient;
import io.airlift.log.Logger;
import io.airlift.tracetoken.TraceTokenManager;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.RequestLog;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.util.component.LifeCycle;

import java.io.File;
import java.io.IOException;

import static io.airlift.http.server.HttpRequestEvent.createHttpRequestEvent;

class DelimitedRequestLog
        implements RequestLog, LifeCycle
{
    private static final Logger log = Logger.get(DelimitedRequestLog.class);
    private static final String TEMP_FILE_EXTENSION = ".tmp";
    private static final String LOG_FILE_EXTENSION = ".log";

    // Tab-separated
    // Time, ip, method, url, user, agent, response code, request length, response length, response time
    private final TraceTokenManager traceTokenManager;
    private final EventClient eventClient;
    private final CurrentTimeMillisProvider currentTimeMillisProvider;
    private final RollingFileAppender<HttpRequestEvent> fileAppender;

    public DelimitedRequestLog(String filename, int maxHistory, long maxFileSizeInBytes, TraceTokenManager traceTokenManager, EventClient eventClient)
            throws IOException
    {
        this(filename, maxHistory, maxFileSizeInBytes, traceTokenManager, eventClient, new SystemCurrentTimeMillisProvider());
    }

    public DelimitedRequestLog(String filename,
            int maxHistory,
            long maxFileSizeInBytes,
            TraceTokenManager traceTokenManager,
            EventClient eventClient,
            CurrentTimeMillisProvider currentTimeMillisProvider)
            throws IOException
    {
        this.traceTokenManager = traceTokenManager;
        this.eventClient = eventClient;
        this.currentTimeMillisProvider = currentTimeMillisProvider;

        ContextBase context = new ContextBase();
        HttpLogLayout httpLogLayout = new HttpLogLayout();

        recoverTempFiles(filename);

        fileAppender = new RollingFileAppender<>();
        SizeAndTimeBasedFNATP<HttpRequestEvent> triggeringPolicy = new SizeAndTimeBasedFNATP<>();
        TimeBasedRollingPolicy<HttpRequestEvent> rollingPolicy = new TimeBasedRollingPolicy<>();

        rollingPolicy.setContext(context);
        rollingPolicy.setFileNamePattern(filename + "-%d{yyyy-MM-dd}.%i.log.gz");
        rollingPolicy.setMaxHistory(maxHistory);
        rollingPolicy.setTimeBasedFileNamingAndTriggeringPolicy(triggeringPolicy);
        rollingPolicy.setParent(fileAppender);
        rollingPolicy.start();

        triggeringPolicy.setContext(context);
        triggeringPolicy.setTimeBasedRollingPolicy(rollingPolicy);
        triggeringPolicy.setMaxFileSize(String.valueOf(maxFileSizeInBytes));
        triggeringPolicy.start();

        fileAppender.setContext(context);
        fileAppender.setFile(filename);
        fileAppender.setAppend(true);
        fileAppender.setLayout(httpLogLayout);
        fileAppender.setRollingPolicy(rollingPolicy);
        fileAppender.start();
    }

    @Override
    public void log(Request request, Response response)
    {
        long currentTime = currentTimeMillisProvider.getCurrentTimeMillis();
        HttpRequestEvent event = createHttpRequestEvent(request, response, traceTokenManager, currentTime);

        fileAppender.doAppend(event);

        eventClient.post(event);
    }

    @Override
    public void start()
            throws Exception
    {
    }

    @Override
    public void stop()
            throws Exception
    {
        fileAppender.stop();
    }

    @Override
    public boolean isRunning()
    {
        return true;
    }

    @Override
    public boolean isStarted()
    {
        return true;
    }

    @Override
    public boolean isStarting()
    {
        return false;
    }

    @Override
    public boolean isStopping()
    {
        return false;
    }

    @Override
    public boolean isStopped()
    {
        return false;
    }

    @Override
    public boolean isFailed()
    {
        return false;
    }

    @Override
    public void addLifeCycleListener(Listener listener)
    {
    }

    @Override
    public void removeLifeCycleListener(Listener listener)
    {
    }

    private static void recoverTempFiles(String logPath)
    {
        // logback has a tendency to leave around temp files if it is interrupted
        // these .tmp files are log files that are about to be compressed.
        // This method recovers them so that they aren't orphaned

        File logPathFile = new File(logPath).getParentFile();
        File[] tempFiles = logPathFile.listFiles((dir, name) -> {
            return name.endsWith(TEMP_FILE_EXTENSION);
        });

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
}
