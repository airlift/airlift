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
package io.airlift.dbpool;

import io.airlift.dbpool.MockConnectionPoolDataSource.MockConnection;
import io.airlift.units.Duration;
import org.testng.annotations.Test;

import javax.sql.ConnectionPoolDataSource;
import javax.sql.DataSource;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static io.airlift.testing.Assertions.assertGreaterThan;
import static io.airlift.testing.Assertions.assertInstanceOf;
import static io.airlift.units.Duration.nanosSince;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

public class ManagedDataSourceTest
{
    @Test
    public void testBasic()
            throws Exception
    {
        ManagedDataSource dataSource = new MockManagedDataSource(1, new Duration(10, MILLISECONDS));
        assertEquals(dataSource.getConnectionsActive(), 0);
        assertEquals(dataSource.getStats().getCheckout().getAllTime().getCount(), 0.0);
        assertEquals(dataSource.getStats().getCreate().getAllTime().getCount(), 0.0);
        assertEquals(dataSource.getStats().getHeld().getAllTime().getCount(), 0.0);
        assertEquals(dataSource.getStats().getConnectionErrorCount(), 0);

        Connection connection = dataSource.getConnection();
        assertNotNull(connection);
        assertTrue(connection instanceof MockConnection);
        assertEquals(dataSource.getConnectionsActive(), 1);
        assertEquals(dataSource.getStats().getCheckout().getAllTime().getCount(), 1.0);
        assertEquals(dataSource.getStats().getCreate().getAllTime().getCount(), 1.0);
        assertEquals(dataSource.getStats().getHeld().getAllTime().getCount(), 0.0);
        assertEquals(dataSource.getStats().getConnectionErrorCount(), 0);

        connection.close();
        assertEquals(dataSource.getConnectionsActive(), 0);
        assertEquals(dataSource.getStats().getCheckout().getAllTime().getCount(), 1.0);
        assertEquals(dataSource.getStats().getCreate().getAllTime().getCount(), 1.0);
        assertEquals(dataSource.getStats().getHeld().getAllTime().getCount(), 1.0);
        assertEquals(dataSource.getStats().getConnectionErrorCount(), 0);
    }

    @Test
    public void testMaxConnectionWaitMillis()
            throws Exception
    {
        // datasource server to 1 connection and only wait 1 ms for a connection
        ManagedDataSource dataSource = new MockManagedDataSource(1, new Duration(10, MILLISECONDS));
        assertEquals(dataSource.getMaxConnectionWaitMillis(), 10);

        // checkout a connection
        Connection connection = dataSource.getConnection();
        assertEquals(dataSource.getConnectionsActive(), 1);

        // try to get another one which will timeout
        long start = System.nanoTime();
        try {
            dataSource.getConnection();
            fail("Expected SQLException from timeout");
        }
        catch (SQLException expected) {
        }
        Duration duration = nanosSince(start);
        assertGreaterThan(duration, new Duration(10, MILLISECONDS));
        assertEquals(dataSource.getConnectionsActive(), 1);

        // try with a different timeout
        dataSource.setMaxConnectionWaitMillis(new Duration(50, MILLISECONDS));
        assertEquals(dataSource.getMaxConnectionWaitMillis(), 50);
        start = System.nanoTime();
        try {
            dataSource.getConnection();
            fail("Expected SQLException from timeout");
        }
        catch (SQLException expected) {
        }
        duration = nanosSince(start);
        assertGreaterThan(duration, new Duration(50, MILLISECONDS));
        assertEquals(dataSource.getConnectionsActive(), 1);

        // verify proper handling of illegal values
        try {
            dataSource.setMaxConnectionWaitMillis(null);
            fail("NullPointerException IllegalArgumentException");
        }
        catch (NullPointerException e) {
        }
        assertEquals(dataSource.getMaxConnectionWaitMillis(), 50);
        // verify proper handling of illegal values
        try {
            dataSource.setMaxConnectionWaitMillis(new Duration(0, MILLISECONDS));
            fail("Expected IllegalArgumentException");
        }
        catch (IllegalArgumentException e) {
        }
        assertEquals(dataSource.getMaxConnectionWaitMillis(), 50);

        connection.close();
        assertEquals(dataSource.getConnectionsActive(), 0);
    }

    /**
     * Verify adjustment of connection count limits.
     * <ol>
     * <li>Test initial limit</li>
     * <li>Test limit increase</li>
     * <li>Test decrease below current checkout</li>
     * <li>Verify handling of illegal values</li>
     * </ol>
     */
    @Test
    public void testMaxConnections()
            throws Exception
    {
        // datasource server to 1 connection and only wait 1 ms for a connection
        ManagedDataSource dataSource = new MockManagedDataSource(1, new Duration(1, MILLISECONDS));
        assertEquals(dataSource.getMaxConnections(), 1);

        // checkout a connection
        Queue<Connection> connections = new LinkedList<>();
        connections.add(dataSource.getConnection());
        assertEquals(dataSource.getConnectionsActive(), 1);

        // verify that we can't another connection
        try {
            dataSource.getConnection();
            fail("Expected SQLException from timeout");
        }
        catch (SQLException expected) {
        }
        assertEquals(dataSource.getConnectionsActive(), 1);

        // increase the max connection count to 3 and acquire two extra ones
        dataSource.setMaxConnections(3);
        assertEquals(dataSource.getMaxConnections(), 3);
        connections.add(dataSource.getConnection());
        connections.add(dataSource.getConnection());

        // verify that we can't get another connection
        try {
            dataSource.getConnection();
            fail("Expected SQLException from timeout");
        }
        catch (SQLException expected) {
        }
        assertEquals(dataSource.getConnectionsActive(), 3);

        // reduce the max connection count to 2
        dataSource.setMaxConnections(2);
        assertEquals(dataSource.getMaxConnections(), 2);
        assertEquals(dataSource.getConnectionsActive(), 3);

        // first verify that we still can't get more connections
        try {
            dataSource.getConnection();
            fail("Expected SQLException from timeout");
        }
        catch (SQLException expected) {
        }
        assertEquals(dataSource.getConnectionsActive(), 3);

        // now release one and verify we still can't get another one
        connections.remove().close();
        assertEquals(dataSource.getConnectionsActive(), 2);
        try {
            dataSource.getConnection();
            fail("Expected SQLException from timeout");
        }
        catch (SQLException expected) {
        }
        assertEquals(dataSource.getConnectionsActive(), 2);

        // finally close another one and verify we can reopen it
        connections.remove().close();
        connections.add(dataSource.getConnection());
        assertEquals(dataSource.getConnectionsActive(), 2);

        // verify proper handling of illegal values
        try {
            dataSource.setMaxConnectionWaitMillis(null);
            fail("Expected NullPointerException");
        }
        catch (NullPointerException e) {
        }
        assertEquals(dataSource.getMaxConnections(), 2);
        try {
            dataSource.setMaxConnectionWaitMillis(new Duration(0, MILLISECONDS));
            fail("Expected IllegalArgumentException");
        }
        catch (IllegalArgumentException e) {
        }
        assertEquals(dataSource.getMaxConnections(), 2);

        // clean up
        for (Connection connection : connections) {
            connection.close();
        }
        assertEquals(dataSource.getConnectionsActive(), 0);
    }

    @Test
    public void testAcquirePermitInterrupted()
            throws Exception
    {
        // datasource server to 1 connection and only wait 1 ms for a connection
        final ManagedDataSource dataSource = new MockManagedDataSource(1, new Duration(5000, MILLISECONDS));
        assertEquals(dataSource.getMaxConnectionWaitMillis(), 5000);

        // checkout a connection
        Connection connection = dataSource.getConnection();
        assertEquals(dataSource.getConnectionsActive(), 1);

        // checkout in another thread which we are going to interrupt
        final CountDownLatch startLatch = new CountDownLatch(1);
        final CountDownLatch endLatch = new CountDownLatch(1);
        final AtomicBoolean wasInterrupted = new AtomicBoolean();
        final AtomicReference<SQLException> exception = new AtomicReference<>();
        Thread createThread = new Thread()
        {
            @Override
            public void run()
            {
                startLatch.countDown();
                try {
                    dataSource.getConnection();
                }
                catch (SQLException e) {
                    exception.set(e);
                }
                finally {
                    wasInterrupted.set(isInterrupted());
                    endLatch.countDown();
                }
            }
        };
        createThread.start();

        // wait for thread to actually start
        startLatch.await();
        // interrupt the createThread
        createThread.interrupt();
        // wait for the thread to end
        endLatch.await();

        // verify that the thread is still in the interrupted state
        assertTrue(wasInterrupted.get(), "createThread.isInterrupted()");
        SQLException sqlException = exception.get();
        assertNotNull(sqlException);
        assertInstanceOf(sqlException.getCause(), InterruptedException.class);

        connection.close();
        assertEquals(dataSource.getConnectionsActive(), 0);
    }

    @Test
    public void testIdempotentClose()
            throws Exception
    {
        ManagedDataSource dataSource = new MockManagedDataSource(10, new Duration(10, MILLISECONDS));
        List<MockConnection> connections = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            MockConnection connection = (MockConnection) dataSource.getConnection();
            assertNotNull(connection);
            connections.add(connection);
        }

        assertEquals(dataSource.getConnectionsActive(), 10);

        // close connections in a random order
        Collections.shuffle(connections);

        int closedCount = 0;
        for (MockConnection connection : connections) {
            closedCount++;
            for (int j = 0; j < 7; j++) {
                connection.close();
                assertEquals(dataSource.getConnectionsActive(), 10 - closedCount);
            }
        }
    }

    @Test
    public void testConnectionException()
            throws Exception
    {
        ManagedDataSource dataSource = new MockManagedDataSource(1, new Duration(10, MILLISECONDS));
        MockConnection connection = (MockConnection) dataSource.getConnection();
        assertNotNull(connection);
        assertEquals(dataSource.getConnectionsActive(), 1);
        connection.errorOccurred();
        assertEquals(dataSource.getConnectionsActive(), 0);
    }

    @Test
    public void testCreateException()
    {
        MockConnectionPoolDataSource mockConnectionPoolDataSource = new MockConnectionPoolDataSource();
        ManagedDataSource dataSource = new MockManagedDataSource(mockConnectionPoolDataSource, 1, new Duration(10, MILLISECONDS));
        mockConnectionPoolDataSource.createException = new SQLException();

        assertEquals(dataSource.getConnectionsActive(), 0);
        try {
            dataSource.getConnection();
            fail("expected SQLException");
        }
        catch (SQLException e) {
            assertSame(e, mockConnectionPoolDataSource.createException);
        }
        assertEquals(dataSource.getConnectionsActive(), 0);
    }

    @Test
    public void testCloseException()
            throws SQLException
    {
        MockConnectionPoolDataSource mockConnectionPoolDataSource = new MockConnectionPoolDataSource();
        ManagedDataSource dataSource = new MockManagedDataSource(1, new Duration(10, MILLISECONDS));
        mockConnectionPoolDataSource.closeException = new SQLException();

        assertEquals(dataSource.getConnectionsActive(), 0);
        MockConnection connection = (MockConnection) dataSource.getConnection();
        assertNotNull(connection);
        assertEquals(dataSource.getConnectionsActive(), 1);
        connection.close();
        assertEquals(dataSource.getConnectionsActive(), 0);

        connection = (MockConnection) dataSource.getConnection();
        assertNotNull(connection);
        assertEquals(dataSource.getConnectionsActive(), 1);
        connection.close();
        assertEquals(dataSource.getConnectionsActive(), 0);
        connection.errorOccurred();
        assertEquals(dataSource.getConnectionsActive(), 0);
    }

    @Test
    public void testIdempotentCloseAndException()
            throws SQLException
    {
        MockConnectionPoolDataSource mockConnectionPoolDataSource = new MockConnectionPoolDataSource();
        ManagedDataSource dataSource = new MockManagedDataSource(1, new Duration(10, MILLISECONDS));
        mockConnectionPoolDataSource.closeException = new SQLException();

        assertEquals(dataSource.getConnectionsActive(), 0);
        MockConnection connection = (MockConnection) dataSource.getConnection();
        assertNotNull(connection);
        assertEquals(dataSource.getConnectionsActive(), 1);

        for (int i = 0; i < 10; i++) {
            connection.close();
            assertEquals(dataSource.getConnectionsActive(), 0);
            connection.errorOccurred();
            assertEquals(dataSource.getConnectionsActive(), 0);
        }
    }

    @Test
    public void testLogWriterIsNeverSet()
            throws SQLException
    {
        MockConnectionPoolDataSource mockConnectionPoolDataSource = new MockConnectionPoolDataSource();
        PrintWriter expectedLogWriter = new PrintWriter(new StringWriter());
        mockConnectionPoolDataSource.logWriter = expectedLogWriter;
        ManagedDataSource dataSource = new MockManagedDataSource(mockConnectionPoolDataSource, 1, new Duration(10, MILLISECONDS));

        // data source log writer should start with null
        assertNull(dataSource.getLogWriter());

        // set the writer
        PrintWriter newWriter = new PrintWriter(new StringWriter());
        dataSource.setLogWriter(newWriter);

        // data source log writer should still be null
        assertNull(dataSource.getLogWriter());
        // core data source should remain unaffected
        assertSame(mockConnectionPoolDataSource.logWriter, expectedLogWriter);
    }

    @Test
    public void testLoginTimeoutIsNeverSet()
            throws SQLException
    {
        MockConnectionPoolDataSource mockConnectionPoolDataSource = new MockConnectionPoolDataSource();
        mockConnectionPoolDataSource.loginTimeout = 42;
        ManagedDataSource dataSource = new MockManagedDataSource(mockConnectionPoolDataSource, 1, new Duration(5, SECONDS));

        // login timeout should always be max connection wait
        assertEquals(dataSource.getLoginTimeout(), 5);

        // set to a new value
        int newTimeout = 12345;
        dataSource.setLoginTimeout(newTimeout);

        // data source timeout should still be max connection wait
        assertEquals(dataSource.getLoginTimeout(), 5);

        // core data source should remain unaffected
        assertEquals(mockConnectionPoolDataSource.loginTimeout, 42);
    }

    @Test
    public void testWrapper()
            throws SQLException
    {
        ManagedDataSource dataSource = new MockManagedDataSource(1, new Duration(10, MILLISECONDS));
        assertTrue(dataSource.isWrapperFor(ManagedDataSource.class));
        assertTrue(dataSource.isWrapperFor(DataSource.class));
        assertTrue(dataSource.isWrapperFor(Object.class));
        assertFalse(dataSource.isWrapperFor(ConnectionPoolDataSource.class));
        assertFalse(dataSource.isWrapperFor(Integer.class));
        try {
            dataSource.isWrapperFor(null);
            fail("Expected SQLException");
        }
        catch (SQLException expected) {
        }
        try {
            dataSource.unwrap(null);
            fail("Expected SQLException");
        }
        catch (SQLException expected) {
        }

        assertSame(dataSource.unwrap(ManagedDataSource.class), dataSource);
        assertSame(dataSource.unwrap(DataSource.class), dataSource);
        assertSame(dataSource.unwrap(Object.class), dataSource);

        try {
            dataSource.unwrap(ConnectionPoolDataSource.class);
            fail("Expected SQLException");
        }
        catch (SQLException expected) {
        }

        try {
            dataSource.unwrap(Integer.class);
            fail("Expected SQLException");
        }
        catch (SQLException expected) {
        }
    }

    @Test
    public void testGetConnectionUsernamePassword()
            throws SQLException
    {
        ManagedDataSource dataSource = new MockManagedDataSource(1, new Duration(10, MILLISECONDS));
        try {
            dataSource.getConnection("username", "password");
            fail("Expected SQLException");
        }
        catch (UnsupportedOperationException expected) {
        }
    }
}
