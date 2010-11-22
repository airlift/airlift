package com.proofpoint.dbpool;

import static com.proofpoint.dbpool.Duration.nanosSince;
import com.proofpoint.dbpool.MockConnectionPoolDataSource.MockConnection;
import static com.proofpoint.testing.Assertions.assertBetweenInclusive;
import static com.proofpoint.testing.Assertions.assertGreaterThan;
import static com.proofpoint.testing.Assertions.assertInstanceof;
import org.testng.Assert;
import static org.testng.AssertJUnit.assertNotNull;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;
import org.testng.annotations.Test;

import javax.sql.ConnectionPoolDataSource;
import javax.sql.DataSource;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.sql.Connection;
import java.sql.SQLException;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.Queue;
import java.util.LinkedList;

public class ManagedDataSourceTest
{
    @Test
    public void testBasic()
            throws Exception
    {
        ManagedDataSource dataSource = new ManagedDataSource(new MockConnectionPoolDataSource(), 1);
        assertEquals(dataSource.getConnectionsActive(), 0);
        assertEquals(dataSource.getStats().getCheckout().getCount(), 0);
        assertEquals(dataSource.getStats().getCreate().getCount(), 0);
        assertEquals(dataSource.getStats().getHeld().getCount(), 0);
        assertEquals(dataSource.getStats().getConnectionErrorCount(), 0);

        Connection connection = dataSource.getConnection();
        Assert.assertNotNull(connection);
        Assert.assertTrue(connection instanceof MockConnection);
        assertEquals(dataSource.getConnectionsActive(), 1);
        assertEquals(dataSource.getStats().getCheckout().getCount(), 1);
        assertEquals(dataSource.getStats().getCreate().getCount(), 1);
        assertEquals(dataSource.getStats().getHeld().getCount(), 0);
        assertEquals(dataSource.getStats().getConnectionErrorCount(), 0);

        connection.close();
        assertEquals(dataSource.getConnectionsActive(), 0);
        assertEquals(dataSource.getStats().getCheckout().getCount(), 1);
        assertEquals(dataSource.getStats().getCreate().getCount(), 1);
        assertEquals(dataSource.getStats().getHeld().getCount(), 1);
        assertEquals(dataSource.getStats().getConnectionErrorCount(), 0);
    }

    @Test
    public void testMaxConnectionWaitMillis()
            throws Exception
    {
        // datasource server to 1 connection and only wait 1 ms for a connection
        ManagedDataSource dataSource = new ManagedDataSource(new MockConnectionPoolDataSource(), 1);
        dataSource.setMaxConnectionWaitMillis(10);
        assertEquals(dataSource.getMaxConnectionWaitMillis(), 10);

        // checkout a connection
        Connection connection = dataSource.getConnection();
        assertEquals(dataSource.getConnectionsActive(), 1);

        // try to get another one which will timeout
        long start = System.nanoTime();
        try {
            dataSource.getConnection();
            Assert.fail("Expected SQLExcpetion from timeout");
        }
        catch (SQLException expected) {
        }
        Duration duration = nanosSince(start);
        assertGreaterThan(duration, new Duration(10, MILLISECONDS));
        assertEquals(dataSource.getConnectionsActive(), 1);

        // try with a different timeout
        dataSource.setMaxConnectionWaitMillis(50);
        assertEquals(dataSource.getMaxConnectionWaitMillis(), 50);
        start = System.nanoTime();
        try {
            dataSource.getConnection();
            Assert.fail("Expected SQLExcpetion from timeout");
        }
        catch (SQLException expected) {
        }
        duration = nanosSince(start);
        assertGreaterThan(duration, new Duration(50, MILLISECONDS));
        assertEquals(dataSource.getConnectionsActive(), 1);

        // verify proper handeling of illegal values
        try {
            dataSource.setMaxConnectionWaitMillis(0);
            fail("Excpected IllegalArgumentException");
        }
        catch (IllegalArgumentException e) {
        }
        assertEquals(dataSource.getMaxConnectionWaitMillis(), 50);
        try {
            dataSource.setMaxConnectionWaitMillis(-1);
            fail("Excpected IllegalArgumentException");
        }
        catch (IllegalArgumentException e) {
        }
        assertEquals(dataSource.getMaxConnectionWaitMillis(), 50);

        connection.close();
        assertEquals(dataSource.getConnectionsActive(), 0);
    }

    /**
     * Verify adjustment of connection count limits.
     * </p> 1) Test initial limit
     * </p> 2) Test limit increase
     * </p> 3) Test decrease below current checkout
     * </p> 4) Verify handeling of illegal values
     */
    @Test
    public void testMaxConnections()
            throws Exception
    {
        // datasource server to 1 connection and only wait 1 ms for a connection
        ManagedDataSource dataSource = new ManagedDataSource(new MockConnectionPoolDataSource(), 1);
        assertEquals(dataSource.getMaxConnections(), 1);
        dataSource.setMaxConnectionWaitMillis(1);

        // checkout a connection
        Queue<Connection> connections = new LinkedList<Connection>();
        connections.add(dataSource.getConnection());
        assertEquals(dataSource.getConnectionsActive(), 1);

        // verify that we can't another connection
        try {
            dataSource.getConnection();
            Assert.fail("Expected SQLExcpetion from timeout");
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
            Assert.fail("Expected SQLExcpetion from timeout");
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
            Assert.fail("Expected SQLExcpetion from timeout");
        }
        catch (SQLException expected) {
        }
        assertEquals(dataSource.getConnectionsActive(), 3);

        // now release one and verify we still can't get another one
        connections.remove().close();
        assertEquals(dataSource.getConnectionsActive(), 2);
        try {
            dataSource.getConnection();
            Assert.fail("Expected SQLExcpetion from timeout");
        }
        catch (SQLException expected) {
        }
        assertEquals(dataSource.getConnectionsActive(), 2);

        // finally close another one and verify we can reopen it
        connections.remove().close();
        connections.add(dataSource.getConnection());
        assertEquals(dataSource.getConnectionsActive(), 2);


        // verify proper handeling of illegal values
        try {
            dataSource.setMaxConnectionWaitMillis(0);
            fail("Excpected IllegalArgumentException");
        }
        catch (IllegalArgumentException e) {
        }
        assertEquals(dataSource.getMaxConnections(), 2);
        try {
            dataSource.setMaxConnectionWaitMillis(-1);
            fail("Excpected IllegalArgumentException");
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
    public void testAquirePermitInterrupted()
            throws Exception
    {
        // datasource server to 1 connection and only wait 1 ms for a connection
        final ManagedDataSource dataSource = new ManagedDataSource(new MockConnectionPoolDataSource(), 1);
        dataSource.setMaxConnectionWaitMillis(5000);
        assertEquals(dataSource.getMaxConnectionWaitMillis(), 5000);

        // checkout a connection
        Connection connection = dataSource.getConnection();
        assertEquals(dataSource.getConnectionsActive(), 1);

        // checkout in another thread which we are going to interrupe
        final CountDownLatch startLatch = new CountDownLatch(1);
        final CountDownLatch endLatch = new CountDownLatch(1);
        final AtomicBoolean wasInterrupted = new AtomicBoolean();
        final AtomicReference<SQLException> exception = new AtomicReference<SQLException>();
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
                } finally {
                    wasInterrupted.set(isInterrupted());
                    endLatch.countDown();
                }
            }
        };
        createThread.start();

        // wait for thread to acually start
        startLatch.await();
        // inerrupt the createThread
        createThread.interrupt();
        // wait for the thread to end
        endLatch.await();

        // verify that the thread is still in the interrupted state
        assertTrue(wasInterrupted.get(), "createThread.isInterrupted()");
        SQLException sqlException = exception.get();
        assertNotNull(sqlException);
        assertInstanceof(sqlException.getCause(), InterruptedException.class);

        connection.close();
        assertEquals(dataSource.getConnectionsActive(), 0);
    }

    @Test
    public void testIdempotentClose()
            throws Exception
    {
        ManagedDataSource dataSource = new ManagedDataSource(new MockConnectionPoolDataSource(), 1);
        MockConnection connection = (MockConnection) dataSource.getConnection();
        Assert.assertNotNull(connection);
        assertEquals(dataSource.getConnectionsActive(), 1);
        for (int i = 0; i < 10; i++) {
            connection.close();
            assertEquals(dataSource.getConnectionsActive(), 0);
        }
    }

    @Test
    public void testConnectionException()
            throws Exception
    {
        ManagedDataSource dataSource = new ManagedDataSource(new MockConnectionPoolDataSource(), 1);
        MockConnection connection = (MockConnection) dataSource.getConnection();
        Assert.assertNotNull(connection);
        assertEquals(dataSource.getConnectionsActive(), 1);
        connection.errorOccured();
        assertEquals(dataSource.getConnectionsActive(), 0);
    }

    @Test
    public void testCreateException()
    {
        MockConnectionPoolDataSource mockConnectionPoolDataSource = new MockConnectionPoolDataSource();
        ManagedDataSource dataSource = new ManagedDataSource(mockConnectionPoolDataSource, 1);
        mockConnectionPoolDataSource.createException = new SQLException();

        assertEquals(dataSource.getConnectionsActive(), 0);
        try {
            dataSource.getConnection();
            Assert.fail("expected SQLException");
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
        ManagedDataSource dataSource = new ManagedDataSource(mockConnectionPoolDataSource, 1);
        mockConnectionPoolDataSource.closeException = new SQLException();

        assertEquals(dataSource.getConnectionsActive(), 0);
        MockConnection connection = (MockConnection) dataSource.getConnection();
        Assert.assertNotNull(connection);
        assertEquals(dataSource.getConnectionsActive(), 1);
        connection.close();
        assertEquals(dataSource.getConnectionsActive(), 0);

        connection = (MockConnection) dataSource.getConnection();
        Assert.assertNotNull(connection);
        assertEquals(dataSource.getConnectionsActive(), 1);
        connection.close();
        assertEquals(dataSource.getConnectionsActive(), 0);
        connection.errorOccured();
        assertEquals(dataSource.getConnectionsActive(), 0);
    }

    @Test
    public void testIdempotentCloseAndException()
            throws SQLException
    {
        MockConnectionPoolDataSource mockConnectionPoolDataSource = new MockConnectionPoolDataSource();
        ManagedDataSource dataSource = new ManagedDataSource(mockConnectionPoolDataSource, 1);
        mockConnectionPoolDataSource.closeException = new SQLException();

        assertEquals(dataSource.getConnectionsActive(), 0);
        MockConnection connection = (MockConnection) dataSource.getConnection();
        Assert.assertNotNull(connection);
        assertEquals(dataSource.getConnectionsActive(), 1);

        for (int i = 0; i < 10; i++) {
            connection.close();
            assertEquals(dataSource.getConnectionsActive(), 0);
            connection.errorOccured();
            assertEquals(dataSource.getConnectionsActive(), 0);
        }
    }

    @Test
    public void testLogWriter()
            throws SQLException
    {
        MockConnectionPoolDataSource mockConnectionPoolDataSource = new MockConnectionPoolDataSource();
        mockConnectionPoolDataSource.logWriter = new PrintWriter(new StringWriter());
        ManagedDataSource dataSource = new ManagedDataSource(mockConnectionPoolDataSource, 1);
        assertSame(dataSource.getLogWriter(), mockConnectionPoolDataSource.logWriter);
        PrintWriter newWriter = new PrintWriter(new StringWriter());
        dataSource.setLogWriter(newWriter);
        assertSame(mockConnectionPoolDataSource.logWriter, newWriter);
    }

    @Test
    public void testLoginTimeout()
            throws SQLException
    {
        MockConnectionPoolDataSource mockConnectionPoolDataSource = new MockConnectionPoolDataSource();
        mockConnectionPoolDataSource.loginTimeout = 42;
        ManagedDataSource dataSource = new ManagedDataSource(mockConnectionPoolDataSource, 1);
        assertEquals(dataSource.getLoginTimeout(), mockConnectionPoolDataSource.loginTimeout);
        int newTimeout = 12345;
        dataSource.setLoginTimeout(12345);
        assertEquals(mockConnectionPoolDataSource.loginTimeout, newTimeout);
    }

    @Test
    public void testWrapper()
            throws SQLException
    {
        MockConnectionPoolDataSource mockConnectionPoolDataSource = new MockConnectionPoolDataSource();
        ManagedDataSource dataSource = new ManagedDataSource(mockConnectionPoolDataSource, 1);
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
        MockConnectionPoolDataSource mockConnectionPoolDataSource = new MockConnectionPoolDataSource();
        ManagedDataSource dataSource = new ManagedDataSource(mockConnectionPoolDataSource, 1);
        try {
            dataSource.getConnection("username", "password");
            fail("Expected SQLException");
        }
        catch (UnsupportedOperationException expected) {
        }
    }
}
