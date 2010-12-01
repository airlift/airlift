package com.proofpoint.dbpool;

import java.sql.SQLException;

public class SqlTimeoutException extends SQLException
{
    public SqlTimeoutException()
    {
    }

    public SqlTimeoutException(String reason)
    {
        super(reason);
    }

    public SqlTimeoutException(Throwable cause)
    {
        super(cause);
    }

    public SqlTimeoutException(String reason, Throwable cause)
    {
        super(reason, cause);
    }
}
