package com.proofpoint.jmx;

import com.google.common.collect.Lists;
import com.google.inject.Inject;
import org.weakref.jmx.Managed;

import java.util.List;
import java.util.Map;

public class StackTraceMBean
{
    @Inject
    public StackTraceMBean()
    {
    }

    @Managed
    public List<String>     getStackTrace()
    {
        List<String>                            output = Lists.newArrayList();
        Map<Thread, StackTraceElement[]>        stackTraces = Thread.getAllStackTraces();
        for ( Map.Entry<Thread, StackTraceElement[]> entry : stackTraces.entrySet() )
        {
            output.add(entry.getKey().toString());
            for ( StackTraceElement element : entry.getValue() )
            {
                output.add("\t" + element.toString());
            }
            output.add("");
        }

        return output;
    }
}
