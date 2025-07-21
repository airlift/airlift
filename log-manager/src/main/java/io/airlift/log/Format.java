package io.airlift.log;

import java.util.Map;
import java.util.logging.Formatter;

public enum Format
{
    JSON {
        @Override
        public Formatter createFormatter(Map<String, String> logAnnotations, boolean interactive)
        {
            return new JsonFormatter(logAnnotations);
        }
    },
    TEXT {
        @Override
        public Formatter createFormatter(Map<String, String> logAnnotations, boolean interactive)
        {
            return new StaticFormatter(logAnnotations, interactive);
        }
    };

    public abstract Formatter createFormatter(Map<String, String> logAnnotations, boolean interactive);

    public Formatter createFormatter(Map<String, String> logAnnotations)
    {
        return createFormatter(logAnnotations, false);
    }
}
