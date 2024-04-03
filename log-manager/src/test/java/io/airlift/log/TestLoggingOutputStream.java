/*
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

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

public class TestLoggingOutputStream
{
    @Test(dataProvider = "testStripTrailingNewlineDataProvider")
    public void testStripTrailingNewline(String printed, String logged)
    {
        MockHandler handler = new MockHandler();

        java.util.logging.Logger mockLogger = java.util.logging.Logger.getAnonymousLogger();
        mockLogger.setUseParentHandlers(false);
        mockLogger.setLevel(Level.ALL);
        mockLogger.addHandler(handler);

        PrintStream stream = new PrintStream(new LoggingOutputStream(new Logger(mockLogger)), true);
        stream.println(printed);

        assertLog(handler.takeRecord(), Level.INFO, logged);
        assertTrue(handler.isEmpty());
    }

    @DataProvider
    public static Object[][] testStripTrailingNewlineDataProvider()
    {
        return new Object[][] {
                {"Greeting from Warsaw!", "Greeting from Warsaw!"},
                {"many new lines:\n\n", "many new lines:"},
                {"trailing spaces and tabs \t", "trailing spaces and tabs"},
                {"intra \t  n \n rn \r\n whitespace", "intra \t  n \n rn \r\n whitespace"},
        };
    }

    private void assertLog(LogRecord record, Level level, String message)
    {
        assertEquals(record.getLevel(), level);
        assertEquals(record.getMessage(), message);
        assertNull(record.getThrown());
    }

    private static class MockHandler
            extends Handler
    {
        private final List<LogRecord> records = new ArrayList<>();

        private MockHandler()
        {
            setLevel(Level.ALL);
        }

        @Override
        public void publish(LogRecord record)
        {
            records.add(record);
        }

        @Override
        public void flush() {}

        @Override
        public void close() {}

        public LogRecord takeRecord()
        {
            assertFalse(records.isEmpty(), "No messages logged");
            return records.removeFirst();
        }

        public boolean isEmpty()
        {
            return records.isEmpty();
        }
    }
}
