package io.airlift.log;

import org.junit.jupiter.api.Test;

import java.io.PrintWriter;
import java.io.StringWriter;

import static io.airlift.log.TerminalColors.Color.RED;
import static org.assertj.core.api.Assertions.assertThat;

public class TestTerminalColors
{
    private static final TerminalColors COLORED = new TerminalColors(true, true);
    private static final TerminalColors NOT_INTERACTIVE = new TerminalColors(false, true);
    private static final TerminalColors NOT_SUPPORTED = new TerminalColors(true, false);

    @Test
    public void testColored()
    {
        assertThat(COLORED.colored("message", RED)).isEqualTo("\033[31mmessage\033[0m");
        assertThat(NOT_INTERACTIVE.colored("message", RED)).isEqualTo("message");
        assertThat(NOT_SUPPORTED.colored("message", RED)).isEqualTo("message");
    }

    @Test
    public void testColoredWriterColorsEveryLine()
    {
        String stackTrace = printStackTrace(COLORED);

        // every line is colored, and reset before the line break so the color does not bleed
        assertThat(stackTrace.lines())
                .isNotEmpty()
                .allSatisfy(line -> assertThat(line).startsWith("\033[31m").endsWith("\033[0m"));
        assertThat(stackTrace).contains("\033[31mjava.lang.RuntimeException: boom\033[0m");
    }

    @Test
    public void testColoredWriterColorsEmbeddedLineBreaks()
    {
        StringWriter out = new StringWriter();
        PrintWriter writer = COLORED.coloredWriter(new PrintWriter(out), RED);
        writer.print("first\nsecond\r\nthird");

        assertThat(out.toString()).isEqualTo("\033[31mfirst\033[0m\n\033[31msecond\033[0m\r\n\033[31mthird\033[0m");
    }

    @Test
    public void testColoredWriterColorsMultiLineExceptionMessage()
    {
        StringWriter out = new StringWriter();
        new RuntimeException("boom\nsecond line")
                .printStackTrace(COLORED.coloredWriter(new PrintWriter(out), RED));

        assertThat(out.toString().lines())
                .allSatisfy(line -> assertThat(line).startsWith("\033[31m").endsWith("\033[0m"));
    }

    @Test
    public void testColoredWriterDoesNotColorWhenDisabled()
    {
        assertThat(printStackTrace(NOT_INTERACTIVE)).doesNotContain("\033");
        assertThat(printStackTrace(NOT_SUPPORTED)).doesNotContain("\033");
    }

    private static String printStackTrace(TerminalColors colors)
    {
        StringWriter out = new StringWriter();
        new RuntimeException("boom").printStackTrace(colors.coloredWriter(new PrintWriter(out), RED));
        return out.toString();
    }
}
