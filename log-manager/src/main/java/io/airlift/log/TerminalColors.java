package io.airlift.log;

import java.io.Console;
import java.io.PrintWriter;

import static io.airlift.log.TerminalColors.Color.BLUE;
import static io.airlift.log.TerminalColors.Color.GREEN;
import static io.airlift.log.TerminalColors.Color.RED;
import static io.airlift.log.TerminalColors.Color.WHITE;
import static io.airlift.log.TerminalColors.Color.YELLOW;

public class TerminalColors
{
    private static final boolean isColorSupported = isColorSupported();
    private static final String ANSI_RESET = "\033[0m";

    private final boolean interactive;

    public TerminalColors(boolean interactive)
    {
        this.interactive = interactive;
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
        BRIGHT_BLACK("\033[90m"),;

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
        if (!isColorSupported) {
            return text;
        }
        if (!interactive) {
            return text;
        }
        return color.getCode() + text + ANSI_RESET;
    }

    public String colored(String text, Level level)
    {
        return switch (level) {
            case OFF -> text;
            case TRACE -> colored(text, WHITE);
            case DEBUG -> colored(text, BLUE);
            case INFO -> colored(text, GREEN);
            case WARN -> colored(text, YELLOW);
            case ERROR -> colored(text, RED);
        };
    }

    public static PrintWriter coloredWriter(PrintWriter writer, Color color)
    {
        return new PrintWriter(writer)
        {
            @Override
            public void write(char[] buffer, int off, int len)
            {
                writer.write(color.getCode());
                writer.write(buffer, off, len);
                writer.write(ANSI_RESET);
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

    private static boolean isColorSupported()
    {
        Console console = System.console();

        // https://github.com/openjdk/jdk/pull/26273 changed the behavior of System.console()
        // to return null if there is no console attached, even if the output is redirected to
        // a terminal.
        if (console == null && Runtime.version().feature() < 25) {
            return false;
        }

        // No terminal at all
        String term = System.getenv("TERM");
        if (term == null) {
            return false;
        }

        // Dumb terminal
        if (term.equalsIgnoreCase("dumb")) {
            return false;
        }

        // https://no-color.org/
        return System.getenv("NO_COLOR") == null;
    }
}
