package com.proofpoint.bootstrap;

import com.google.common.base.Joiner;
import com.proofpoint.event.client.EventField;
import com.proofpoint.event.client.EventType;

import java.util.List;

@EventType("ConfigWarnings")
public class ConfigWarningsEvent
{
    private final String warnings;

    public ConfigWarningsEvent(List<String> warnings)
    {
        this.warnings = Joiner.on('\n').join(warnings);
    }

    @EventField
    public String getWarnings()
    {
        return warnings;
    }
}
