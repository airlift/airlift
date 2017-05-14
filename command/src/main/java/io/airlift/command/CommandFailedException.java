package io.airlift.command;

public class CommandFailedException extends Exception
{
    private final Integer exitCode;
    private final String output;
    private final Command command;

    public CommandFailedException(Command command, String message, Throwable cause)
    {
        super(String.format("%s %s%s", command.getCommand(), message, cause == null ? "" : ": " + cause.getMessage()), cause);
        this.command = command;
        exitCode = null;
        output = null;
    }

    public CommandFailedException(Command command, int exitCode, String output)
    {
        super(String.format("%s exited with %s\n%s", command.getCommand(), exitCode, output));
        this.command = command;
        this.exitCode = exitCode;
        this.output = output;
    }

    public Command getCommand()
    {
        return command;
    }

    public boolean exited()
    {
        return exitCode != null;
    }

    public Integer getExitCode()
    {
        return exitCode;
    }

    public String getOutput()
    {
        return output;
    }
}
