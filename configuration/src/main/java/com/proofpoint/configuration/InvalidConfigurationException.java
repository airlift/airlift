package com.proofpoint.configuration;

public class InvalidConfigurationException extends Exception
{
    public InvalidConfigurationException(String message, Object... args)
    {
        super(String.format(message, args));
    }

    public InvalidConfigurationException(Throwable cause, String message, Object... args)
    {
        super(String.format(message, args), cause);
    }
}
