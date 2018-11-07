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

import javax.sql.ConnectionEvent;
import javax.sql.ConnectionEventListener;
import javax.sql.ConnectionPoolDataSource;
import javax.sql.PooledConnection;
import javax.sql.StatementEventListener;

import java.io.PrintWriter;
import java.sql.Array;
import java.sql.Blob;
import java.sql.CallableStatement;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.NClob;
import java.sql.PreparedStatement;
import java.sql.SQLClientInfoException;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.SQLWarning;
import java.sql.SQLXML;
import java.sql.Savepoint;
import java.sql.Statement;
import java.sql.Struct;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

public class MockConnectionPoolDataSource
        implements ConnectionPoolDataSource
{
    long creationSleep;
    SQLException createException;
    SQLException closeException;
    PrintWriter logWriter;
    int loginTimeout;

    @Override
    public MockPooledConnection getPooledConnection()
            throws SQLException
    {
        if (createException != null) {
            throw createException;
        }
        if (creationSleep > 0) {
            try {
                Thread.sleep(creationSleep);
            }
            catch (InterruptedException e) {
                throw new RuntimeException("Sleep interrupted", e);
            }
        }
        return new MockPooledConnection(this);
    }

    @Override
    public MockPooledConnection getPooledConnection(String user, String password)
            throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public PrintWriter getLogWriter()
            throws SQLException
    {
        return logWriter;
    }

    @Override
    public void setLogWriter(PrintWriter out)
            throws SQLException
    {
        logWriter = out;
    }

    @Override
    public Logger getParentLogger()
            throws SQLFeatureNotSupportedException
    {
        throw new SQLFeatureNotSupportedException("java.util.logging not supported");
    }

    @Override
    public int getLoginTimeout()
            throws SQLException
    {
        return loginTimeout;
    }

    @Override
    public void setLoginTimeout(int seconds)
            throws SQLException
    {
        loginTimeout = seconds;
    }

    public static class MockPooledConnection
            implements PooledConnection
    {
        private final MockConnectionPoolDataSource dataSource;
        private boolean closed;
        private List<ConnectionEventListener> connectionEventListeners = new CopyOnWriteArrayList<>();

        public MockPooledConnection(MockConnectionPoolDataSource dataSource)
        {
            this.dataSource = dataSource;
        }

        @Override
        public MockConnection getConnection()
                throws SQLException
        {
            if (closed) {
                throw new SQLException("connection has been closed");
            }
            return new MockConnection(this);
        }

        @Override
        public void close()
                throws SQLException
        {
            closed = true;
            if (dataSource.closeException != null) {
                throw dataSource.closeException;
            }
        }

        public void closeOccurred()
        {
            for (ConnectionEventListener connectionEventListener : connectionEventListeners) {
                connectionEventListener.connectionClosed(new ConnectionEvent(this));
            }
        }

        public void errorOccurred()
        {
            closed = true;
            for (ConnectionEventListener connectionEventListener : connectionEventListeners) {
                connectionEventListener.connectionErrorOccurred(new ConnectionEvent(this));
            }
        }

        @Override
        public void addConnectionEventListener(ConnectionEventListener listener)
        {
            connectionEventListeners.add(listener);
        }

        @Override
        public void removeConnectionEventListener(ConnectionEventListener listener)
        {
            connectionEventListeners.remove(listener);
        }

        @Override
        public void addStatementEventListener(StatementEventListener listener)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void removeStatementEventListener(StatementEventListener listener)
        {
            throw new UnsupportedOperationException();
        }
    }

    public static class MockConnection
            implements Connection
    {
        private final MockPooledConnection mockPooledConnection;
        private boolean closed;

        public MockConnection(MockPooledConnection mockPooledConnection)
        {
            this.mockPooledConnection = mockPooledConnection;
        }

        @Override
        public void close()
                throws SQLException
        {
            if (closed) {
                return;
            }
            closed = true;
            mockPooledConnection.closeOccurred();
        }

        public void errorOccurred()
                throws SQLException
        {
            if (closed) {
                return;
            }
            closed = true;
            mockPooledConnection.errorOccurred();
        }

        @Override
        public void setSchema(String schema)
                throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public String getSchema()
                throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public int getNetworkTimeout()
                throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setNetworkTimeout(Executor executor, int milliseconds)
                throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void abort(Executor executor)
                throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public Statement createStatement()
                throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public PreparedStatement prepareStatement(String sql)
                throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public CallableStatement prepareCall(String sql)
                throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public String nativeSQL(String sql)
                throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setAutoCommit(boolean autoCommit)
                throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean getAutoCommit()
                throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void commit()
                throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void rollback()
                throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isClosed()
                throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public DatabaseMetaData getMetaData()
                throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setReadOnly(boolean readOnly)
                throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isReadOnly()
                throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setCatalog(String catalog)
                throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public String getCatalog()
                throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setTransactionIsolation(int level)
                throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public int getTransactionIsolation()
                throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public SQLWarning getWarnings()
                throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void clearWarnings()
                throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public Statement createStatement(int resultSetType, int resultSetConcurrency)
                throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency)
                throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency)
                throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public Map<String, Class<?>> getTypeMap()
                throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setTypeMap(Map<String, Class<?>> map)
                throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setHoldability(int holdability)
                throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public int getHoldability()
                throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public Savepoint setSavepoint()
                throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public Savepoint setSavepoint(String name)
                throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void rollback(Savepoint savepoint)
                throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void releaseSavepoint(Savepoint savepoint)
                throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public Statement createStatement(int resultSetType, int resultSetConcurrency, int resultSetHoldability)
                throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability)
                throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability)
                throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public PreparedStatement prepareStatement(String sql, int autoGeneratedKeys)
                throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public PreparedStatement prepareStatement(String sql, int[] columnIndexes)
                throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public PreparedStatement prepareStatement(String sql, String[] columnNames)
                throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public Clob createClob()
                throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public Blob createBlob()
                throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public NClob createNClob()
                throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public SQLXML createSQLXML()
                throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isValid(int timeout)
                throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setClientInfo(String name, String value)
                throws SQLClientInfoException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setClientInfo(Properties properties)
                throws SQLClientInfoException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public String getClientInfo(String name)
                throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public Properties getClientInfo()
                throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public Array createArrayOf(String typeName, Object[] elements)
                throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public Struct createStruct(String typeName, Object[] attributes)
                throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> T unwrap(Class<T> iface)
                throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isWrapperFor(Class<?> iface)
                throws SQLException
        {
            throw new UnsupportedOperationException();
        }
    }
}
