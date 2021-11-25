package io.airlift.log;

import java.util.Map;
import java.util.logging.Formatter;

public enum Format
{
    JSON {
        public Formatter createFormatter(Map<String, String> logAnnotations)
        {
            return new JsonFormatter(logAnnotations);
        }
    },
    TEXT {
        public Formatter createFormatter(Map<String, String> logAnnotations)
        {
            return new StaticFormatter(logAnnotations);
        }
    };

    public abstract Formatter createFormatter(Map<String, String> logAnnotations);
}
