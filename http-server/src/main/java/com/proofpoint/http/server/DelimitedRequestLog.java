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
package com.proofpoint.http.server;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.encoder.EncoderBase;
import com.proofpoint.log.Logging;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.RequestLog;
import org.eclipse.jetty.server.Response;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.DateTimeFormatterBuilder;
import org.joda.time.format.ISODateTimeFormat;

import java.io.IOException;

import static com.proofpoint.http.server.HttpRequestEvent.createHttpRequestEvent;

class DelimitedRequestLog
        implements RequestLog
{
    // Tab-separated
    // Time, ip, method, url, user, agent, response code, request length, response length, response time

    private final CurrentTimeMillisProvider currentTimeMillisProvider;
    private final Appender<HttpRequestEvent> appender;

    DelimitedRequestLog(String filename, int maxHistory, long maxSizeInBytes)
            throws IOException
    {
        this(filename, maxHistory, maxSizeInBytes, new SystemCurrentTimeMillisProvider());
    }

    DelimitedRequestLog(String filename,
            int maxHistory, long maxSizeInBytes,
            CurrentTimeMillisProvider currentTimeMillisProvider)
            throws IOException
    {
        this.currentTimeMillisProvider = currentTimeMillisProvider;

        appender = Logging.createFileAppender(filename, maxHistory, maxSizeInBytes, new EventEncoder(), new LoggerContext());
    }

    @Override
    public void log(Request request, Response response)
    {
        long currentTime = currentTimeMillisProvider.getCurrentTimeMillis();
        HttpRequestEvent event = createHttpRequestEvent(request, response, currentTime);

        synchronized (appender) {
            appender.doAppend(event);
        }
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
        appender.stop();
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

    private static class EventEncoder extends EncoderBase<HttpRequestEvent>
    {

        private final DateTimeFormatter isoFormatter = new DateTimeFormatterBuilder()
                        .append(ISODateTimeFormat.dateHourMinuteSecondFraction())
                        .appendTimeZoneOffset("Z", true, 2, 2)
                        .toFormatter();

        @Override
        public void doEncode(HttpRequestEvent event)
                throws IOException
        {
            StringBuilder builder = new StringBuilder();
            builder.append(isoFormatter.print(event.getTimeStamp()))
                    .append('\t')
                    .append(event.getClientAddress())
                    .append('\t')
                    .append(event.getMethod())
                    .append('\t')
                    .append(event.getRequestUri()) // TODO: escape
                    .append('\t')
                    .append(event.getUser())
                    .append('\t')
                    .append(event.getAgent()) // TODO: escape
                    .append('\t')
                    .append(event.getResponseCode())
                    .append('\t')
                    .append(event.getRequestSize())
                    .append('\t')
                    .append(event.getResponseSize())
                    .append('\t')
                    .append(event.getTimeToLastByte())
                    .append('\t')
                    .append(event.getTraceToken())
                    .append('\n');

            outputStream.write(builder.toString().getBytes("UTF-8"));
            outputStream.flush();
        }

        @Override
        public void close()
                throws IOException
        {
        }
    }
}
