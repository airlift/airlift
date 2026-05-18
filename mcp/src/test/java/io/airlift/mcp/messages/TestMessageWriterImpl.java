package io.airlift.mcp.messages;

import io.airlift.mcp.messages.MessageWriterImpl.ResponseFacade;
import io.airlift.mcp.messages.SentMessages.SentMessage;
import org.junit.jupiter.api.Test;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class TestMessageWriterImpl
{
    @Test
    public void testWriteBeforeUpgradeOutputsPlainHttp()
    {
        StringWriter output = new StringWriter();
        ResponseFacade response = buildFacade(output);
        MessageWriterImpl writer = new MessageWriterImpl(response, false);

        writer.write("{\"jsonrpc\":\"2.0\"}");

        assertThat(output.toString()).isEqualTo("{\"jsonrpc\":\"2.0\"}");
        assertThat(writer.hasBeenUpgraded()).isFalse();
    }

    @Test
    public void testWriteMessageUpgradesToSse()
    {
        StringWriter output = new StringWriter();
        TestingResponseFacade response = new TestingResponseFacade(output);
        MessageWriterImpl writer = new MessageWriterImpl(response, false);

        writer.writeMessage("hello");

        assertThat(writer.hasBeenUpgraded()).isTrue();
        assertThat(response.contentType).isEqualTo("text/event-stream");
        assertThat(output.toString()).startsWith("id: ");
        assertThat(output.toString()).contains("data: hello\n\n");
    }

    @Test
    public void testWriteAfterUpgradeOutputsAsSse()
    {
        StringWriter output = new StringWriter();
        TestingResponseFacade response = new TestingResponseFacade(output);
        MessageWriterImpl writer = new MessageWriterImpl(response, false);

        writer.writeMessage("first");
        output.getBuffer().setLength(0);

        writer.write("second");

        assertThat(output.toString()).startsWith("id: ");
        assertThat(output.toString()).contains("data: second\n\n");
    }

    @Test
    public void testWriteMessageAfterPlainHttpThrows()
    {
        StringWriter output = new StringWriter();
        ResponseFacade response = buildFacade(output);
        MessageWriterImpl writer = new MessageWriterImpl(response, false);

        writer.write("plain");

        assertThatThrownBy(() -> writer.writeMessage("sse"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    public void testTakeSentMessagesWhenResumable()
    {
        StringWriter output = new StringWriter();
        ResponseFacade response = buildFacade(output);
        MessageWriterImpl writer = new MessageWriterImpl(response, true);

        writer.writeMessage("a");
        writer.writeMessage("b");

        List<SentMessage> messages = writer.takeSentMessages();
        assertThat(messages).hasSize(2);
        assertThat(messages.get(0).data()).isEqualTo("a");
        assertThat(messages.get(1).data()).isEqualTo("b");

        // subsequent take returns empty
        assertThat(writer.takeSentMessages()).isEmpty();
    }

    @Test
    public void testTakeSentMessagesWhenNotResumable()
    {
        StringWriter output = new StringWriter();
        ResponseFacade response = buildFacade(output);
        MessageWriterImpl writer = new MessageWriterImpl(response, false);

        writer.writeMessage("a");

        assertThat(writer.takeSentMessages()).isEmpty();
    }

    @Test
    public void testWriteSentMessagesReplaysAfterLastEventId()
    {
        StringWriter output = new StringWriter();
        ResponseFacade response = buildFacade(output);
        MessageWriterImpl writer = new MessageWriterImpl(response, true);

        SentMessages sentMessages = new SentMessages(List.of(
                new SentMessage("id-1", "first"),
                new SentMessage("id-2", "second"),
                new SentMessage("id-3", "third")));

        boolean found = writer.writeSentMessages(sentMessages, "id-1");

        assertThat(found).isTrue();
        assertThat(writer.hasBeenUpgraded()).isTrue();
        assertThat(output.toString()).contains("data: second\n");
        assertThat(output.toString()).contains("data: third\n");
        assertThat(output.toString()).doesNotContain("data: first\n");
    }

    @Test
    public void testWriteSentMessagesReturnsFalseForMissingId()
    {
        StringWriter output = new StringWriter();
        ResponseFacade response = buildFacade(output);
        MessageWriterImpl writer = new MessageWriterImpl(response, false);

        SentMessages sentMessages = new SentMessages(List.of(
                new SentMessage("id-1", "first")));

        boolean found = writer.writeSentMessages(sentMessages, "nonexistent");

        assertThat(found).isFalse();
    }

    @Test
    public void testNewlineEncodingInSseMessages()
    {
        StringWriter output = new StringWriter();
        ResponseFacade response = buildFacade(output);
        MessageWriterImpl writer = new MessageWriterImpl(response, false);

        writer.writeMessage("line1\nline2\rline3");

        assertThat(output.toString()).contains("data: line1\\nline2\\rline3\n\n");
    }

    private static ResponseFacade buildFacade(StringWriter output)
    {
        return new TestingResponseFacade(output);
    }

    private static class TestingResponseFacade
            implements ResponseFacade
    {
        private final PrintWriter writer;
        private String contentType;

        TestingResponseFacade(StringWriter output)
        {
            this.writer = new PrintWriter(output);
        }

        @Override
        public PrintWriter getWriter()
        {
            return writer;
        }

        @Override
        public void setContentType(String type)
        {
            this.contentType = type;
        }
    }
}
