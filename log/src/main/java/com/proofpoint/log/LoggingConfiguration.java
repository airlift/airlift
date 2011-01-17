package com.proofpoint.log;

import com.proofpoint.configuration.Config;

public class LoggingConfiguration
{
    private boolean consoleEnabled = true;
    private String logPath;
    private long maxSegmentSizeInBytes;
    private int maxHistory;
    private String levelsFile;

    @Config("log.enable-console")
    public boolean isConsoleEnabled()
    {
        return consoleEnabled;
    }

    public LoggingConfiguration setConsoleEnabled(boolean consoleEnabled)
    {
        this.consoleEnabled = consoleEnabled;
        return this;
    }

    @Config("log.output-file")
    public String getLogPath()
    {
        return logPath;
    }

    public LoggingConfiguration setLogPath(String logPath)
    {
        this.logPath = logPath;
        return this;
    }

    @Config("log.max-size-in-bytes")
    public long getMaxSegmentSizeInBytes()
    {
        return maxSegmentSizeInBytes;
    }

    public LoggingConfiguration setMaxSegmentSizeInBytes(long maxSegmentSizeInBytes)
    {
        this.maxSegmentSizeInBytes = maxSegmentSizeInBytes;
        return this;
    }

    @Config("log.max-history")
    public int getMaxHistory()
    {
        return maxHistory;
    }

    public LoggingConfiguration setMaxHistory(int maxHistory)
    {
        this.maxHistory = maxHistory;
        return this;
    }

    @Config("log.levels-file")
    public String getLevelsFile()
    {
        return levelsFile;
    }

    public LoggingConfiguration setLevelsFile(String levelsFile)
    {
        this.levelsFile = levelsFile;
        return this;
    }
}

