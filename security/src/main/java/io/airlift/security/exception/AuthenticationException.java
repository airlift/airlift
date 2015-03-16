package io.airlift.security.exception;

public class AuthenticationException extends RuntimeException
{
    public AuthenticationException()
    {
    }

    public AuthenticationException(String s)
    {
        super(s);
    }

    public AuthenticationException(String s, Throwable throwable)
    {
        super(s, throwable);
    }

    public AuthenticationException(Throwable throwable)
    {
        super(throwable);
    }
}
