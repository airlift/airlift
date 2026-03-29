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
import java.util.concurrent.ExecutorService;
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

        newVirtualThreadPerTaskExecutor().submit(() -> {
            assertThat(semaphore.tryAcquire(5, TimeUnit.SECONDS)).isTrue();
            controller.setValue(groupId, keyId, "value");
            return null;
        });

        boolean success = controller.await(groupId, keyId, Duration.ofSeconds(1));
        assertThat(success).isFalse();

        CountDownLatch latch1 = new CountDownLatch(1);
        Future<Void> future = newVirtualThreadPerTaskExecutor().submit(() -> {
            latch1.countDown();
            controller.await(groupId, keyId, Duration.ofSeconds(1));
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
            controller.await(groupId, keyId, Duration.ofSeconds(1));
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
            controller.await(groupId, keyId, Duration.ofSeconds(1));
            return null;
        });

        assertThat(latch1.await(5, TimeUnit.SECONDS)).isTrue();
        TimeUnit.MILLISECONDS.sleep(50); // ensure the wait is in progress
        controller.deleteValue(groupId, keyId);

        assertThat(future)
                .succeedsWithin(5, TimeUnit.SECONDS);
    }

    @Test
    public void testAwaitWithCompetingKeys()
            throws InterruptedException
    {
        StorageController controller = storageController();

        StorageGroupId group1Id = controller.randomGroupId();
        StorageGroupId group2Id = controller.randomGroupId();

        controller.createGroup(group1Id, Duration.ofDays(1));
        controller.createGroup(group2Id, Duration.ofDays(1));

        StorageKeyId key1Id = new StorageKeyId("key1Id");
        StorageKeyId key2Id = new StorageKeyId("key2Id");

        Semaphore group1Key1Latch = new Semaphore(0);
        Semaphore group1Key2Latch = new Semaphore(0);
        Semaphore group2Key1Latch = new Semaphore(0);
        Semaphore group2Key2Latch = new Semaphore(0);

        try (ExecutorService executorService = newVirtualThreadPerTaskExecutor()) {
            try {
                CountDownLatch readyLatch = new CountDownLatch(4);

                executorService.submit(() -> awaitWithKeyProc(controller, readyLatch, group1Id, key1Id, group1Key1Latch));
                executorService.submit(() -> awaitWithKeyProc(controller, readyLatch, group1Id, key2Id, group1Key2Latch));
                executorService.submit(() -> awaitWithKeyProc(controller, readyLatch, group2Id, key1Id, group2Key1Latch));
                executorService.submit(() -> awaitWithKeyProc(controller, readyLatch, group2Id, key2Id, group2Key2Latch));

                assertThat(readyLatch.await(5, TimeUnit.SECONDS)).isTrue();
                // need a bit of time for controller.await() to get called
                TimeUnit.SECONDS.sleep(1);

                assertThat(group1Key1Latch.availablePermits()).isZero();
                assertThat(group1Key2Latch.availablePermits()).isZero();
                assertThat(group2Key1Latch.availablePermits()).isZero();
                assertThat(group2Key2Latch.availablePermits()).isZero();

                controller.setValue(group1Id, key1Id, "newValue");
                assertThat(group1Key1Latch.tryAcquire(5, TimeUnit.SECONDS)).isTrue();
                assertThat(group1Key2Latch.availablePermits()).isZero();
                assertThat(group2Key1Latch.availablePermits()).isZero();
                assertThat(group2Key2Latch.availablePermits()).isZero();

                controller.setValue(group2Id, key1Id, "newValue");
                assertThat(group1Key1Latch.availablePermits()).isZero();
                assertThat(group1Key2Latch.availablePermits()).isZero();
                assertThat(group2Key1Latch.tryAcquire(5, TimeUnit.SECONDS)).isTrue();
                assertThat(group2Key2Latch.availablePermits()).isZero();
            }
            finally {
                executorService.shutdownNow();
            }
        }
    }

    private static Object awaitWithKeyProc(StorageController controller, CountDownLatch readyLatch, StorageGroupId group, StorageKeyId key, Semaphore latch)
            throws InterruptedException
    {
        readyLatch.countDown();

        while (!Thread.currentThread().isInterrupted()) {
            if (controller.await(group, key, Duration.ofDays(1))) {
                latch.release();
            }
        }
        return null;
    }

    protected abstract StorageController storageController();
}
