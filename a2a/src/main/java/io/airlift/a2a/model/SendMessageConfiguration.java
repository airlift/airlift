package io.airlift.a2a.model;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

public record SendMessageConfiguration(Optional<List<String>> acceptedOutputModes, Optional<TaskPushNotificationConfig> taskPushNotificationConfig, OptionalInt historyLength, Optional<Boolean> returnImmediately)
{
}
