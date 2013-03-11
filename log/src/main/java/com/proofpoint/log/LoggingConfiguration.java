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
package com.proofpoint.log;

import com.proofpoint.configuration.Config;
import com.proofpoint.configuration.LegacyConfig;
import com.proofpoint.units.DataSize;
import com.proofpoint.units.DataSize.Unit;

public class LoggingConfiguration
{
    private boolean consoleEnabled = true;
    private String logPath = null;
    private DataSize maxSegmentSize = new DataSize(100, Unit.MEGABYTE);
    private int maxHistory = 30;
    private String levelsFile = null;

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

    public DataSize getMaxSegmentSize()
    {
        return maxSegmentSize;
    }

    @Config("log.max-size")
    public LoggingConfiguration setMaxSegmentSize(DataSize maxSegmentSize)
    {
        this.maxSegmentSize = maxSegmentSize;
        return this;
    }

    @LegacyConfig(value = "log.max-size-in-bytes", replacedBy = "log.max-size")
    public LoggingConfiguration setMaxSegmentSizeInBytes(long maxSegmentSizeInBytes)
    {
        this.maxSegmentSize = new DataSize(maxSegmentSizeInBytes, Unit.BYTE);
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

