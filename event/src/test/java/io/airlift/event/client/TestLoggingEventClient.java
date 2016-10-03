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

import ch.qos.logback.core.LayoutBase;
import ch.qos.logback.core.rolling.RollingFileAppender;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.testng.Assert.assertEquals;

public class TestLoggingEventClient
{
    private File temp;

    @BeforeClass
    public void setup() throws IOException
    {
        temp = File.createTempFile("event-logging-tests", ".log");
    }

    @AfterClass
    public void tearDown()
    {
        temp.delete();
    }

    @Test
    public void testInitWithDefaults() throws IOException {
        EventConfig config = new EventConfig();
        MockRollingFileAppender appender = new MockRollingFileAppender();
        LoggingEventClient client = createLoggingEventClient(config, appender);
        client.postEvent("test event");
        assertEquals(appender.getLogs().size(), 0, "client shouldn't log any events");
    }

    @Test
    public void testEventLogging() throws IOException
    {
        EventConfig config = new EventConfig();
        config.setLogEnabled(true);
        config.setLogLayoutClass(MockLayout.class.getName());
        config.setLogPath(temp.getAbsolutePath());
        config.setEventTypeToLog("MockEvent");
        MockRollingFileAppender appender = new MockRollingFileAppender();
        LoggingEventClient client = createLoggingEventClient(config, appender);
        client.postEvent(new MockEvent());
        assertEquals(appender.getLogs().size(), 1, "client should log a single event");
        assertEquals(appender.getLogs().get(0).getClass(), MockEvent.class, "client should log a single event");
    }

    @Test(expectedExceptions = RuntimeException.class, expectedExceptionsMessageRegExp = "java.lang.ClassNotFoundException: nonexistent_class.*")
    public void testNonexistentLogLayoutClass()
    {
        EventConfig config = new EventConfig();
        config.setLogEnabled(true);
        config.setLogPath(temp.getAbsolutePath());
        config.setEventTypeToLog("MockEvent");
        config.setLogLayoutClass("nonexistent_class");
        MockRollingFileAppender appender = new MockRollingFileAppender();
        createLoggingEventClient(config, appender);
    }

    @Test(expectedExceptions = NullPointerException.class, expectedExceptionsMessageRegExp = "event type to log is null")
    public void testNullEventType()
    {
        EventConfig config = new EventConfig();
        config.setLogEnabled(true);
        config.setLogPath(temp.getAbsolutePath());
        config.setEventTypeToLog(null);
        config.setLogLayoutClass(MockLayout.class.getName());
        MockRollingFileAppender appender = new MockRollingFileAppender();
        createLoggingEventClient(config, appender);
    }

    @Test(expectedExceptions = NullPointerException.class, expectedExceptionsMessageRegExp = "event log layout class is null")
    public void testNullLogLayout()
    {
        EventConfig config = new EventConfig();
        config.setLogEnabled(true);
        config.setLogPath(temp.getAbsolutePath());
        config.setEventTypeToLog("MockEvent");
        config.setLogLayoutClass(null);
        MockRollingFileAppender appender = new MockRollingFileAppender();
        createLoggingEventClient(config, appender);
    }

    private LoggingEventClient createLoggingEventClient(EventConfig config, MockRollingFileAppender appender)
    {
        LoggingEventClient client = new LoggingEventClient(config);
        client.setFileAppender(appender);
        return client;
    }
}

@EventType("MockEvent")
class MockEvent
{
}

class MockRollingFileAppender extends RollingFileAppender
{
    private final List<Object> logs;

    public MockRollingFileAppender()
    {
        logs = new ArrayList<>();
    }

    @Override
    public void doAppend(Object eventObject) {
        logs.add(eventObject);
    }

    public List<Object> getLogs()
    {
        return logs;
    }
}

class MockLayout extends LayoutBase
{
    @Override
    public String doLayout(Object event) {
        return event.toString();
    }
}
