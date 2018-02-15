/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.airlift.stats;

import io.airlift.log.Logging;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Manual tester for GcMonitor.
 * <p>
 * Test should always be run with:
 * <pre>
 * {@code   -Xmx1g -Xms1g -XX:+PrintGCApplicationStoppedTime}
 * </pre>
 * All GC algorithms should be tested:
 * <ul>
 * <li>Serial: {@code -XX:+UseSerialGC }</li>
 * <li>Parallel: {@code -XX:+UseParallelGC}</li>
 * <li>CMS: {@code -XX:+UseConcMarkSweepGC}</li>
 * <li>G1: {@code -XX:+UseG1GC}</li>
 * </ul>
 * Verifying stopped time in GC log to the stopped time from GCMonitor.
 */
public final class GcMonitorTester
{
    private static final List<byte[]> VALUES = new ArrayList<>();
    private static final int VALUE_SIZE = 100 * 1024;
    private static final long MAX_MEMORY_SIZE = (long) (0.9 * 1024 * 1024 * 1024);
    private static long memorySize;

    private GcMonitorTester() {}

    public static void main(String[] args)
            throws InterruptedException
    {
        Logging.initialize();
        JmxGcMonitor gcMonitor = new JmxGcMonitor();
        gcMonitor.start();

        while (true) {
            byte[] value = new byte[VALUE_SIZE];
            if (memorySize > MAX_MEMORY_SIZE) {
                VALUES.set(ThreadLocalRandom.current().nextInt(VALUES.size()), value);
            }
            else {
                VALUES.add(value);
                memorySize += VALUE_SIZE;
            }
            Thread.sleep(1);
        }
    }
}
