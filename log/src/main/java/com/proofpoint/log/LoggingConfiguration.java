package com.proofpoint.log;

import com.proofpoint.configuration.Config;

public class LoggingConfiguration
{
    private boolean consoleEnabled = true;
    private String logPath;
    private long maxSegmentSizeInBytes;
    private int maxHistory;
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

