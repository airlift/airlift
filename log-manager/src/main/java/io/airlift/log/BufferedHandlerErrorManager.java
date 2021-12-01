package io.airlift.log;

import com.google.common.base.Throwables;

import javax.annotation.concurrent.GuardedBy;

import java.io.PrintStream;
import java.util.logging.ErrorManager;

public class BufferedHandlerErrorManager
        extends ErrorManager
{
    @GuardedBy("this")
    private boolean reported;

    private final PrintStream stdErr;

    public BufferedHandlerErrorManager(PrintStream stdErr)
    {
        this.stdErr = stdErr;
    }

    public synchronized void error(String msg, Exception exception, int code)
    {
        if (!reported) {
            return;
        }
        reported = true;
        String text = ErrorManager.class.getName() + ": " + code;
        if (msg != null) {
            text = text + ": " + msg;
        }
        if (exception != null) {
            stdErr.println(text + "\n" + Throwables.getStackTraceAsString(exception));
        }
        else {
            stdErr.println(text);
        }
    }
}
