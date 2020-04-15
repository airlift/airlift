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
import io.airlift.configuration.ConfigDescription;
import io.airlift.configuration.DefunctConfig;
import io.airlift.configuration.LegacyConfig;
import io.airlift.log.RollingFileHandler.CompressionType;
import io.airlift.units.DataSize;

import javax.validation.constraints.NotNull;

import static io.airlift.units.DataSize.Unit.GIGABYTE;
import static io.airlift.units.DataSize.Unit.MEGABYTE;

@DefunctConfig("log.max-size-in-bytes")
public class LoggingConfiguration
{
    private boolean consoleEnabled = true;
    private String logPath;
    private DataSize maxSize = new DataSize(100, MEGABYTE);
    private DataSize maxTotalSize = new DataSize(1, GIGABYTE);
    private CompressionType compression = CompressionType.GZIP;
    private int maxHistory = 30;
    private String levelsFile;
    private boolean legacyLoggerImplementationEnabled;

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

    @LegacyConfig("log.output-file")
    @Config("log.path")
    public LoggingConfiguration setLogPath(String logPath)
    {
        this.logPath = logPath;
        return this;
    }

    public DataSize getMaxSize()
    {
        return maxSize;
    }

    @Config("log.max-size")
    @ConfigDescription("Maximum size of each log file")
    public LoggingConfiguration setMaxSize(DataSize maxSize)
    {
        this.maxSize = maxSize;
        return this;
    }

    public DataSize getMaxTotalSize()
    {
        return maxTotalSize;
    }

    @Config("log.max-total-size")
    @ConfigDescription("Maximum size of all log files")
    public LoggingConfiguration setMaxTotalSize(DataSize maxTotalSize)
    {
        this.maxTotalSize = maxTotalSize;
        return this;
    }

    @NotNull
    public CompressionType getCompression()
    {
        return compression;
    }

    @Config("log.compression")
    @ConfigDescription("Compression type for log files")
    public LoggingConfiguration setCompression(CompressionType compression)
    {
        this.compression = compression;
        return this;
    }

    @Deprecated
    public int getMaxHistory()
    {
        return maxHistory;
    }

    @Deprecated
    @Config("log.max-history")
    @ConfigDescription("Maximum number of log files to retain")
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

    public boolean isLegacyLoggerImplementationEnabled()
    {
        return legacyLoggerImplementationEnabled;
    }

    @Config("log.legacy-implementation.enabled")
    public LoggingConfiguration setLegacyLoggerImplementationEnabled(boolean enabled)
    {
        this.legacyLoggerImplementationEnabled = enabled;
        return this;
    }
}
