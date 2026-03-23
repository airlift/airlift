package io.airlift.mcp;

import io.airlift.mcp.storage.StorageController;
import io.airlift.mcp.storage.StorageGroupId;
import io.airlift.mcp.storage.StorageKeyId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import static java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor;
import static org.assertj.core.api.Assertions.assertThat;

public abstract class TestStorageController
{
    @AfterEach
    public void reset()
    {
        // also tests listGroups

        StorageController controller = storageController();

        // each test should have added at least one group
        assertThat(controller.listGroups(Optional.empty())).isNotEmpty();

        Optional<StorageGroupId> cursor = Optional.empty();
        do {
            List<StorageGroupId> storageGroupIds = controller.listGroups(cursor);
            storageGroupIds.forEach(controller::deleteGroup);
            cursor = storageGroupIds.isEmpty() ? Optional.empty() : Optional.of(storageGroupIds.getLast());
        } while (cursor.isPresent());

        assertThat(controller.listGroups(Optional.empty())).isEmpty();
    }

    @Test
    public void testAwait()
            throws InterruptedException
    {
        StorageController controller = storageController();
        StorageGroupId groupId = controller.randomGroupId();
        controller.createGroup(groupId, Duration.ofDays(1));

        StorageKeyId keyId = new StorageKeyId("keyId");

        Semaphore semaphore = new Semaphore(0);

        // wait for keyId to appear

        newVirtualThreadPerTaskExecutor().execute(() -> {
            try {
                assertThat(semaphore.tryAcquire(5, TimeUnit.SECONDS)).isTrue();
                controller.setValue(groupId, keyId, "value");
            }
            catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });

        boolean success = controller.await(groupId, Duration.ofSeconds(1));
        assertThat(success).isFalse();

        CountDownLatch latch1 = new CountDownLatch(1);
        Future<Void> future = newVirtualThreadPerTaskExecutor().submit(() -> {
            latch1.countDown();
            controller.await(groupId, Duration.ofSeconds(1));
            return null;
        });

        assertThat(latch1.await(5, TimeUnit.SECONDS)).isTrue();
        semaphore.release();

        assertThat(future)
                .succeedsWithin(5, TimeUnit.SECONDS);

        // wait for change

        CountDownLatch latch2 = new CountDownLatch(1);
        future = newVirtualThreadPerTaskExecutor().submit(() -> {
            latch2.countDown();
            controller.await(groupId, Duration.ofSeconds(1));
            return null;
        });

        assertThat(latch1.await(5, TimeUnit.SECONDS)).isTrue();
        TimeUnit.MILLISECONDS.sleep(50); // ensure the wait is in progress
        controller.setValue(groupId, keyId, "newValue");

        assertThat(future)
                .succeedsWithin(5, TimeUnit.SECONDS);

        // wait for deletion

        CountDownLatch latch4 = new CountDownLatch(1);
        future = newVirtualThreadPerTaskExecutor().submit(() -> {
            latch4.countDown();
            controller.await(groupId, Duration.ofSeconds(1));
            return null;
        });

        assertThat(latch1.await(5, TimeUnit.SECONDS)).isTrue();
        TimeUnit.MILLISECONDS.sleep(50); // ensure the wait is in progress
        controller.deleteValue(groupId, keyId);

        assertThat(future)
                .succeedsWithin(5, TimeUnit.SECONDS);
    }

    protected abstract StorageController storageController();
}
