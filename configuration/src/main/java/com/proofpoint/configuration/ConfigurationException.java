package com.proofpoint.configuration;

import java.util.Collections;
import java.util.List;

public class ConfigurationException
    extends RuntimeException
{
    private final Object partial;
    private final List<String> errors;

    public ConfigurationException(Object partial, List<String> errors)
    {
        this.partial = partial;
        this.errors = Collections.unmodifiableList(errors);
    }

    public Object getPartial()
    {
        return partial;
    }

    public List<String> getErrors()
    {
        return errors;
    }
}
