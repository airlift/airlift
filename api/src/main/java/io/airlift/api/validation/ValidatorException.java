package io.airlift.api.validation;

import com.google.common.collect.ImmutableList;

import java.util.List;

public class ValidatorException
        extends RuntimeException
{
    private final List<String> messages;

    public ValidatorException()
    {
        this(ImmutableList.of());
    }

    public ValidatorException(String message)
    {
        this(ImmutableList.of(message));
    }

    public ValidatorException(String message, Throwable cause)
    {
        this(ImmutableList.of(message), cause);
    }

    public ValidatorException(List<String> messages)
    {
        super(messages.toString());
        this.messages = ImmutableList.copyOf(messages);
    }

    public ValidatorException(List<String> messages, Throwable cause)
    {
        super(messages.toString(), cause);
        this.messages = ImmutableList.copyOf(messages);
    }

    public List<String> messages()
    {
        return messages;
    }
}
