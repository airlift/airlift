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

import io.airlift.configuration.Config;
import io.airlift.units.DataSize;

import static io.airlift.units.DataSize.Unit.MEGABYTE;

public class EventConfig
{
    private String logPath = "var/log/events.log";
    // for now support only a single event type for logging
    private String eventTypeToLog;
    private boolean logEnabled;
    private String logLayoutClass;
    private int logHistory = 15;
    private DataSize logMaxFileSize = new DataSize(128, MEGABYTE);

    public String getLogPath()
    {
        return logPath;
    }

    @Config("event-log.path")
    public EventConfig setLogPath(String logPath)
    {
        this.logPath = logPath;
        return this;
    }

    public String getEventTypeToLog()
    {
        return eventTypeToLog;
    }

    @Config("event-log.type")
    public EventConfig setEventTypeToLog(String eventTypeToLog)
    {
        this.eventTypeToLog = eventTypeToLog;
        return this;
    }

    public boolean isLogEnabled()
    {
        return logEnabled;
    }

    @Config("event-log.enabled")
    public EventConfig setLogEnabled(boolean logEnabled)
    {
        this.logEnabled = logEnabled;
        return this;
    }

    public String getLogLayoutClass()
    {
        return logLayoutClass;
    }

    @Config("event-log.layout")
    public EventConfig setLogLayoutClass(String logLayoutClass)
    {
        this.logLayoutClass = logLayoutClass;
        return this;
    }

    public int getLogHistory()
    {
        return logHistory;
    }

    @Config("event-log.max-history")
    public EventConfig setLogHistory(int logHistory)
    {
        this.logHistory = logHistory;
        return this;
    }

    public DataSize getLogMaxFileSize()
    {
        return logMaxFileSize;
    }

    @Config("event-log.max-size")
    public EventConfig setLogMaxFileSize(DataSize logMaxFileSize)
    {
        this.logMaxFileSize = logMaxFileSize;
        return this;
    }
}
