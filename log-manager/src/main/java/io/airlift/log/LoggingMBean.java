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

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import org.slf4j.LoggerFactory;
import org.weakref.jmx.Managed;

import java.util.Map;

import static ch.qos.logback.classic.Level.toLevel;
import static com.google.common.collect.Maps.newTreeMap;
import static org.slf4j.Logger.ROOT_LOGGER_NAME;

public class LoggingMBean
{
    @Managed
    public String getLevel(String loggerName)
    {
        return getLogger(loggerName).getEffectiveLevel().toString();
    }

    @Managed
    public void setLevel(String loggerName, String newLevel)
    {
        getLogger(loggerName).setLevel(toLevel(newLevel));
    }

    @Managed
    public String getRootLevel()
    {
        return getLogger(ROOT_LOGGER_NAME).getEffectiveLevel().toString();
    }

    @Managed
    public void setRootLevel(String newLevel)
    {
        getLogger(ROOT_LOGGER_NAME).setLevel(toLevel(newLevel));
    }

    @Managed
    public Map<String, String> getAllLevels()
    {
        Map<String, String> levels = newTreeMap();
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        for (Logger logger : context.getLoggerList()) {
            if (logger.getLevel() != null) {
                levels.put(logger.getName(), logger.getLevel().toString());
            }
        }
        return levels;
    }

    private static Logger getLogger(String name)
    {
        return (Logger) LoggerFactory.getLogger(name);
    }
}
