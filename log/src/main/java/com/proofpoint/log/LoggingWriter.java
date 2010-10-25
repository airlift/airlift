package com.proofpoint.log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;

public class LoggingWriter extends StringWriter
{
    private final Logger logger;
    private final Type type;

    public enum Type
    {
        DEBUG,
        INFO
    }    

    public LoggingWriter(Logger logger, Type type)
    {
        this.logger = logger;
        this.type = type;
    }

    @Override
    public void close() throws IOException
    {
        flush();
        super.close();
    }

    @Override
    public void flush()
    {
        BufferedReader  in = new BufferedReader(new StringReader(getBuffer().toString()));
        for(;;)
        {
            try
            {
                String      line = in.readLine();
                if ( line == null )
                {
                    break;
                }

                switch ( type )
                {
                    default:
                    case DEBUG:
                    {
                        if ( logger.isDebugEnabled() )
                        {
                            logger.debug(line);
                        }
                        break;
                    }

                    case INFO:
                    {
                        if ( logger.isInfoEnabled() )
                        {
                            logger.info(line);
                        }
                        break;
                    }
                }
            }
            catch ( IOException e )
            {
                throw new Error(e); // should never get here
            }
        }

        getBuffer().setLength(0);
    }

    public void printMessage(String message, Object... args) throws IOException
    {
        String      formatted = String.format(message, args) + "\n";
        write(formatted.toCharArray());
    }
}
