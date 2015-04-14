package io.airlift.security.exception;

public class AuthenticationException
        extends RuntimeException
{
    public AuthenticationException()
    {
        super();
    }

    public AuthenticationException(String message)
    {
        super(message);
    }

    public AuthenticationException(String message, Throwable cause)
    {
        super(message, cause);
    }

    public AuthenticationException(Throwable cause)
    {
        super(cause);
    }
}
