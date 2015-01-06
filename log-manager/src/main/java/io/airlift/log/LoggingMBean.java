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

import org.weakref.jmx.Managed;

import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.LogManager;
import java.util.logging.Logger;

public class LoggingMBean
{
    private static final String ROOT_LOGGER_NAME = "";

    @Managed
    @SuppressWarnings("MethodMayBeStatic")
    public String getLevel(String loggerName)
    {
        return getEffectiveLevel(getLogger(loggerName)).toString();
    }

    @Managed
    @SuppressWarnings("MethodMayBeStatic")
    public void setLevel(String loggerName, String newLevel)
    {
        getLogger(loggerName).setLevel(Level.valueOf(newLevel.toUpperCase(Locale.US)).toJulLevel());
    }

    @Managed
    public String getRootLevel()
    {
        return getLevel(ROOT_LOGGER_NAME);
    }

    @Managed
    public void setRootLevel(String newLevel)
    {
        setLevel(ROOT_LOGGER_NAME, newLevel);
    }

    @Managed
    @SuppressWarnings("MethodMayBeStatic")
    public Map<String, String> getAllLevels()
    {
        Map<String, String> levels = new TreeMap<>();
        for (String loggerName : Collections.list(LogManager.getLogManager().getLoggerNames())) {
            java.util.logging.Level level = getLogger(loggerName).getLevel();
            if (level != null) {
                levels.put(loggerName, Level.fromJulLevel(level).toString());
            }
        }
        return levels;
    }

    private static Level getEffectiveLevel(Logger logger)
    {
        java.util.logging.Level level = logger.getLevel();
        if (level == null) {
            Logger parent = logger.getParent();
            if (parent != null) {
                return getEffectiveLevel(parent);
            }
        }
        if (level == null) {
            return Level.OFF;
        }
        return Level.fromJulLevel(level);
    }

    private static java.util.logging.Logger getLogger(String name)
    {
        return java.util.logging.Logger.getLogger(name);
    }
}
