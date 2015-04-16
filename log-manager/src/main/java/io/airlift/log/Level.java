package io.airlift.log;

public enum Level
{
    OFF(java.util.logging.Level.OFF),
    DEBUG(java.util.logging.Level.FINE),
    INFO(java.util.logging.Level.INFO),
    WARN(java.util.logging.Level.WARNING),
    ERROR(java.util.logging.Level.SEVERE);

    private final java.util.logging.Level julLevel;

    Level(java.util.logging.Level julLevel)
    {
        this.julLevel = julLevel;
    }

    java.util.logging.Level toJulLevel()
    {
        return julLevel;
    }

    static Level fromJulLevel(java.util.logging.Level level)
    {
        // Convert any implementation of Level based on the int value
        int levelValue = level.intValue();
        if (levelValue == java.util.logging.Level.OFF.intValue()) {
            return OFF;
        }
        if (levelValue >= java.util.logging.Level.SEVERE.intValue()) {
            return ERROR;
        }
        if (levelValue >= java.util.logging.Level.WARNING.intValue()) {
            return WARN;
        }
        if (levelValue >= java.util.logging.Level.INFO.intValue()) {
            return INFO;
        }
        return DEBUG;
    }
}
