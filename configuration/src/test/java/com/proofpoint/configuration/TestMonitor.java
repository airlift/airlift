package com.proofpoint.configuration;

import com.google.common.base.Joiner;
import com.google.inject.spi.Message;
import org.testng.Assert;

import java.util.ArrayList;
import java.util.List;

class TestMonitor implements Problems.Monitor
{
    private List<Message> errors = new ArrayList<Message>();
    private List<Message> warnings = new ArrayList<Message>();

    @Override
    public void onError(Message error)
    {
        errors.add(error);
    }

    @Override
    public void onWarning(Message warning)
    {
        warnings.add(warning);
    }

    public void assertNumberOfErrors(int expected)
    {
        Assert.assertEquals(errors.size(), expected, String.format("Number of errors is incorrect, actual errors: %s", errorsString()));
    }

    public void assertNumberOfWarnings(int expected)
    {
        Assert.assertEquals(warnings.size(), expected, String.format("Number of warnings is incorrect, actual warnings: %s", warningsString()));
    }

    public void assertMatchingWarningRecorded(String... parts)
    {
        for (Message warning : warnings) {
            boolean matched = true;
            for (String part : parts) {
                if (!warning.getMessage().contains(part)) {
                    matched = false;
                }
            }
            if (matched) {
                return;
            }
        }

        Assert.fail(String.format("Expected message (%s) not found in monitor warning list. Warnings: %s", Joiner.on(", ").join(parts), warningsString()));
    }

    public void assertMatchingErrorRecorded(String... parts)
    {
        for (Message error : errors) {
            boolean matched = true;
            for (String part : parts) {
                if (!error.getMessage().contains(part)) {
                    matched = false;
                }
            }
            if (matched) {
                return;
            }
        }

        Assert.fail(String.format("Expected message (%s) not found in monitor error list. Errors: %s", Joiner.on(", ").join(parts), errorsString()));
    }

    private String errorsString()
    {
        return messageListAsString(errors);
    }

    private String warningsString()
    {
        return messageListAsString(warnings);
    }

    private String messageListAsString(List<Message> list)
    {
        StringBuilder builder = new StringBuilder();
        for (Message message : list) {
            builder.append(message.getMessage()).append(", ");
        }
        return builder.toString();
    }

}

