package io.airlift.mcp.messages;

import java.util.List;

public interface ResumableMessageWriter
        extends MessageWriter
{
    List<SentMessages.SentMessage> takeSentMessages();

    boolean writeSentMessages(SentMessages sentMessages, String lastEventId);
}
