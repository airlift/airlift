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
package io.airlift.log;

import io.airlift.configuration.Config;

public class LoggingConfiguration
{
    private boolean consoleEnabled = true;
    private String logPath;
    private long maxSegmentSizeInBytes = 100 * 1024 * 1024;  // TODO: need "size" unit similar to Duration
    private int maxHistory = 30;
    private String levelsFile;

    public boolean isConsoleEnabled()
    {
        return consoleEnabled;
    }

    @Config("log.enable-console")
    public LoggingConfiguration setConsoleEnabled(boolean consoleEnabled)
    {
        this.consoleEnabled = consoleEnabled;
        return this;
    }

    public String getLogPath()
    {
        return logPath;
    }

    @Config("log.output-file")
    public LoggingConfiguration setLogPath(String logPath)
    {
        this.logPath = logPath;
        return this;
    }

    public long getMaxSegmentSizeInBytes()
    {
        return maxSegmentSizeInBytes;
    }

    @Config("log.max-size-in-bytes")
    public LoggingConfiguration setMaxSegmentSizeInBytes(long maxSegmentSizeInBytes)
    {
        this.maxSegmentSizeInBytes = maxSegmentSizeInBytes;
        return this;
    }

    public int getMaxHistory()
    {
        return maxHistory;
    }

    @Config("log.max-history")
    public LoggingConfiguration setMaxHistory(int maxHistory)
    {
        this.maxHistory = maxHistory;
        return this;
    }

    public String getLevelsFile()
    {
        return levelsFile;
    }

    @Config("log.levels-file")
    public LoggingConfiguration setLevelsFile(String levelsFile)
    {
        this.levelsFile = levelsFile;
        return this;
    }
}

