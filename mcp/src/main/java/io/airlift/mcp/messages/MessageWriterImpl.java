package io.airlift.mcp.messages;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import io.airlift.mcp.McpException;
import io.airlift.mcp.messages.SentMessages.SentMessage;
import io.airlift.mcp.model.JsonRpcErrorDetail;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

import static io.airlift.mcp.model.JsonRpcErrorCode.CONNECTION_CLOSED;
import static java.util.Objects.requireNonNull;

class MessageWriterImpl
        implements ResumableMessageWriter
{
    private final ResponseFacade response;
    private final AtomicReference<State> state = new AtomicReference<>(State.LATENT);
    private final Optional<List<SentMessage>> sentMessages;

    private enum State
    {
        LATENT,
        HTTP,
        SSE,
    }

    @VisibleForTesting
    interface ResponseFacade
    {
        PrintWriter getWriter()
                throws IOException;

        void setContentType(String type);

        @VisibleForTesting
        static ResponseFacade build(HttpServletResponse response)
        {
            return new ResponseFacade()
            {
                @Override
                public PrintWriter getWriter()
                        throws IOException
                {
                    return response.getWriter();
                }

                @Override
                public void setContentType(String type)
                {
                    response.setContentType(type);
                }
            };
        }
    }

    MessageWriterImpl(HttpServletResponse response, boolean resumable)
    {
        this(ResponseFacade.build(response), resumable);
    }

    @VisibleForTesting
    MessageWriterImpl(ResponseFacade response, boolean resumable)
    {
        this.response = requireNonNull(response, "response is null");
        this.sentMessages = resumable ? Optional.of(new CopyOnWriteArrayList<>()) : Optional.empty();
    }

    @Override
    public void write(String data)
    {
        try {
            PrintWriter writer = response.getWriter();  // ensures the response is committed
            State currentState = state.updateAndGet(current -> {
                if (current == State.SSE) {
                    return current;
                }
                return State.HTTP;
            });
            if (currentState == State.SSE) {
                writeMessage(data);
            }
            else {
                // no messages were sent, keep it as a standard JSON response
                writer.write(data);
            }
        }
        catch (IOException e) {
            throw new McpException(e, new JsonRpcErrorDetail(CONNECTION_CLOSED, "Connection appears to be closed while writing message"));
        }
    }

    @Override
    public List<SentMessage> takeSentMessages()
    {
        return sentMessages.map(messages -> {
            List<SentMessage> result = ImmutableList.copyOf(messages);
            messages.clear();
            return result;
        }).orElseGet(ImmutableList::of);
    }

    @Override
    public boolean writeSentMessages(SentMessages sentMessages, String lastEventId)
    {
        boolean found = false;
        for (SentMessages.SentMessage sentMessage : sentMessages.messages()) {
            if (found) {
                internalWriteMessage(sentMessage.id(), sentMessage.data());
            }
            else {
                found = sentMessage.id().equals(lastEventId);
            }
        }
        flush();
        return found;
    }

    @Override
    public void writeMessage(String data)
    {
        String messageId = UUID.randomUUID().toString();

        sentMessages.ifPresent(messages -> messages.add(new SentMessage(messageId, data)));

        internalWriteMessage(messageId, data);
    }

    @Override
    public void flush()
    {
        try {
            response.getWriter().flush();
        }
        catch (IOException e) {
            throw new McpException(e, new JsonRpcErrorDetail(CONNECTION_CLOSED, "Connection appears to be closed while writing message"));
        }
    }

    @Override
    public boolean hasBeenUpgraded()
    {
        return state.get() == State.SSE;
    }

    private void internalWriteMessage(String messageId, String data)
    {
        state.updateAndGet(current -> switch (current) {
            case LATENT -> {
                response.setContentType("text/event-stream");
                yield State.SSE;
            }

            case HTTP -> throw new IllegalStateException("Output is set as an HTTP output stream and cannot be upgraded to SSE");

            case SSE -> State.SSE;
        });

        try {
            PrintWriter writer = response.getWriter();
            writer.write("id: " + encode(messageId) + "\n");
            writer.write("data: " + encode(data) + "\n\n");
        }
        catch (IOException e) {
            throw new McpException(e, new JsonRpcErrorDetail(CONNECTION_CLOSED, "Connection appears to be closed while writing message"));
        }
    }

    private static String encode(String str)
    {
        // Escape newlines and carriage returns for SSE compliance
        return str.replace("\n", "\\n").replace("\r", "\\r");
    }
}
