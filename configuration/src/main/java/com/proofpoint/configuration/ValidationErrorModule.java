package com.proofpoint.configuration;

import com.google.inject.Binder;
import com.google.inject.Module;
import com.google.inject.spi.Message;

import java.util.List;

public class ValidationErrorModule implements Module
{
    private final List<Message> messages;

    public ValidationErrorModule(List<Message> messages)
    {
        this.messages = messages;
    }

    @Override
    public void configure(Binder binder)
    {
        for (Message message : messages) {
            binder.addError(message);
        }
    }
}
