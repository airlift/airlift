package io.airlift.log;

import java.util.logging.Formatter;

public enum Format
{
    JSON {
        public Formatter getFormatter()
        {
            return new JsonFormatter();
        }
    },
    TEXT {
        public Formatter getFormatter()
        {
            return new StaticFormatter();
        }
    };

    public abstract Formatter getFormatter();
}
