package com.proofpoint.log;

import com.proofpoint.configuration.Config;

public class LoggingConfiguration
{
    @Config("log.enable-console")
    public boolean isConsoleEnabled()
    {
        return true;
    }
    
    @Config("log.output-file")
    public String getLogPath()
    {
        return null;
    }

    @Config("log.max-size-in-bytes")
    public long getMaxSegmentSizeInBytes()
    {
        return 100 * 1024 * 1024; // 100 MB
    }

    @Config("log.max-history")
    public int getMaxHistory()
    {
        return 30;
    }

    @Config("log.levels-file")
    public String getLevelsFile()
    {
        return null;
    }
}

