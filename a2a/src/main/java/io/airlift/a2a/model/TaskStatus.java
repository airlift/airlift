package io.airlift.a2a.model;

import java.time.Instant;
import java.util.Optional;

public record TaskStatus(TaskState state, Optional<Message> message, Optional<Instant> timestamp)
{
}
