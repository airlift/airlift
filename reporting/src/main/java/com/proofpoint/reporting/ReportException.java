package com.proofpoint.reporting;

public class ReportException
        extends RuntimeException
{
    public enum Reason
    {
        MALFORMED_OBJECT_NAME,
        INSTANCE_ALREADY_EXISTS,
        INSTANCE_NOT_FOUND,
    }

    private final Reason reason;

    ReportException(final Reason reason, final String message)
    {
        super(message);
        this.reason = reason;
    }

    public Reason getReason()
    {
        return reason;
    }
}
