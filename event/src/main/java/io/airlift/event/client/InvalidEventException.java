package com.proofpoint.event.client;

import java.io.IOException;

public class InvalidEventException extends IOException
{
    public InvalidEventException(String message, Object... args)
    {
        super(String.format(message, args));
    }

    public InvalidEventException(Throwable cause, String message, Object... args)
    {
        super(String.format(message, args), cause);
    }
}
