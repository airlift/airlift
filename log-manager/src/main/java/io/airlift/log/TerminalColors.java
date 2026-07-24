package io.airlift.log;

import java.io.PrintWriter;

import static com.google.common.base.Strings.isNullOrEmpty;
import static io.airlift.log.TerminalColors.Color.BLUE;
import static io.airlift.log.TerminalColors.Color.BRIGHT_BLACK;
import static io.airlift.log.TerminalColors.Color.GREEN;
import static io.airlift.log.TerminalColors.Color.RED;
import static io.airlift.log.TerminalColors.Color.YELLOW;

public class TerminalColors
{
    private static final boolean COLOR_SUPPORTED = isColorSupported();
    private static final String ANSI_RESET = "\033[0m";

    private final boolean enabled;

    public TerminalColors(boolean interactive)
    {
        this(interactive, COLOR_SUPPORTED);
    }

    TerminalColors(boolean interactive, boolean colorSupported)
    {
        this.enabled = interactive && colorSupported;
    }

    public enum Color
    {
        WHITE("\033[37m"),
        RED("\033[31m"),
        GREEN("\033[32m"),
        YELLOW("\033[33m"),
        BLUE("\033[34m"),
        PURPLE("\033[35m"),
        CYAN("\033[36m"),
        BRIGHT_BLACK("\033[90m");

        private final String code;

        Color(String code)
        {
            this.code = code;
        }

        public String getCode()
        {
            return code;
        }
    }

    public String colored(String text, Color color)
    {
        if (!enabled) {
            return text;
        }
        return color.getCode() + text + ANSI_RESET;
    }

    public String colored(String text, Level level)
    {
        return switch (level) {
            case OFF -> text;
            case TRACE -> colored(text, BRIGHT_BLACK);
            case DEBUG -> colored(text, BLUE);
            case INFO -> colored(text, GREEN);
            case WARN -> colored(text, YELLOW);
            case ERROR -> colored(text, RED);
        };
    }

    public PrintWriter coloredWriter(PrintWriter writer, Color color)
    {
        if (!enabled) {
            return writer;
        }
        return new PrintWriter(writer)
        {
            @Override
            public void write(int c)
            {
                write(String.valueOf((char) c), 0, 1);
            }

            @Override
            public void write(char[] buffer, int off, int len)
            {
                write(new String(buffer, off, len), 0, len);
            }

            @Override
            public void write(String string, int off, int len)
            {
                // color each line separately, leaving the line terminators uncolored, so that
                // the color does not carry over into the next line of a multi line string
                int position = off;
                int end = off + len;
                while (position < end) {
                    int lineEnd = position;
                    while (lineEnd < end && !isLineTerminator(string.charAt(lineEnd))) {
                        lineEnd++;
                    }
                    if (lineEnd > position) {
                        writer.write(color.getCode());
                        writer.write(string, position, lineEnd - position);
                        writer.write(ANSI_RESET);
                    }
                    int terminatorEnd = lineEnd;
                    while (terminatorEnd < end && isLineTerminator(string.charAt(terminatorEnd))) {
                        terminatorEnd++;
                    }
                    writer.write(string, lineEnd, terminatorEnd - lineEnd);
                    position = terminatorEnd;
                }
            }

            @Override
            public void flush()
            {
                writer.flush();
            }

            @Override
            public void close()
            {
                writer.close();
            }
        };
    }

    private static boolean isLineTerminator(char c)
    {
        return c == '\n' || c == '\r';
    }

    private static boolean isColorSupported()
    {
        return isColorSupported(System.getenv("TERM"), System.getenv("NO_COLOR"));
    }

    static boolean isColorSupported(String term, String noColor)
    {
        // Color is only ever emitted on the interactive console handler (see the interactive flag),
        // whose output is routinely consumed by sinks that render ANSI but are not TTYs and do not
        // set TERM - Docker log pipes, IDE run consoles, CI. So color is enabled by default and only
        // the standard opt-outs disable it, rather than requiring an attached terminal.

        // https://no-color.org/ - honored when present and not an empty string
        if (!isNullOrEmpty(noColor)) {
            return false;
        }

        // Dumb terminal explicitly declares no capabilities
        return !"dumb".equalsIgnoreCase(term);
    }
}
