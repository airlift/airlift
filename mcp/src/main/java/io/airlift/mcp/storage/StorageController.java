package io.airlift.mcp.storage;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.UnaryOperator;

public interface StorageController
{
    default StorageGroupId randomGroupId()
    {
        return new StorageGroupId(UUID.randomUUID().toString());
    }

    void createGroup(StorageGroupId groupId, Duration ttl);

    boolean validateGroup(StorageGroupId groupId);

    void deleteGroup(StorageGroupId groupId);

    List<StorageGroupId> listGroups(Optional<StorageGroupId> cursor);

    List<StorageKeyId> listGroupKeys(StorageGroupId groupId, Optional<StorageKeyId> cursor);

    Optional<String> getValue(StorageGroupId groupId, StorageKeyId keyId);

    boolean setValue(StorageGroupId groupId, StorageKeyId keyId, String value);

    boolean deleteValue(StorageGroupId groupId, StorageKeyId keyId);

    Optional<String> computeValue(StorageGroupId groupId, StorageKeyId keyId, UnaryOperator<Optional<String>> updater);

    boolean await(StorageGroupId groupId, Duration timeout)
            throws InterruptedException;
}
