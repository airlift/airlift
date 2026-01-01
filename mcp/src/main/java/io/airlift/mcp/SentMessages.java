package io.airlift.mcp;

import com.google.common.collect.ImmutableList;

import java.util.List;

import static java.util.Objects.requireNonNull;

public record SentMessages(List<SentMessage> messages)
{
    public SentMessages
    {
        messages = ImmutableList.copyOf(messages);
    }

    public SentMessages()
    {
        this(ImmutableList.of());
    }

    public record SentMessage(String id, String data)
    {
        public SentMessage
        {
            requireNonNull(id, "id is null");
            requireNonNull(data, "data is null");
        }
    }

    public SentMessages withAdditionalMessages(List<SentMessage> additionalMessages, int maxMessages)
    {
        if (additionalMessages.isEmpty()) {
            return this;
        }

        ImmutableList.Builder<SentMessage> builder = ImmutableList.builder();

        int skipQty = Math.max(0, (messages().size() + additionalMessages.size()) - maxMessages);

        messages().stream()
                .skip(skipQty)
                .forEach(builder::add);

        builder.addAll(additionalMessages);

        return new SentMessages(builder.build());
    }
}
