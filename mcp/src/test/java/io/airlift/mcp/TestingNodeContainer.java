package io.airlift.mcp;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.ShellStrategy;
import org.testcontainers.utility.DockerImageName;

import java.io.Closeable;
import java.io.IOException;
import java.io.UncheckedIOException;

@SuppressWarnings("resource")
public class TestingNodeContainer
        extends GenericContainer<TestingNodeContainer>
        implements Closeable
{
    public TestingNodeContainer()
    {
        super(DockerImageName.parse("node:lts-alpine3.23"));

        withCreateContainerCmdModifier(cmd -> cmd.withTty(true));
        waitingFor(new ShellStrategy());
        start();
    }

    public String execute(String... command)
    {
        try {
            return super.execInContainer(command).getStdout();
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }

    @Override
    public void close()
    {
        super.close();
    }
}
