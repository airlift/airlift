/*
 * Copyright 2013 Proofpoint, Inc.
 *
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
package io.airlift.launcher;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;

import static com.google.common.base.Preconditions.checkNotNull;

public class LegacyPidFile implements PidStatusSource
{
    private final String pidFilePath;

    public LegacyPidFile(String pidFilePath)
    {
        this.pidFilePath = checkNotNull(pidFilePath, "pidFilePath is null");
    }

    @Override
    public PidStatus getStatus()
    {
        try (FileChannel fileChannel = new FileInputStream(pidFilePath).getChannel()) {
            String line = new BufferedReader(Channels.newReader(fileChannel, "us-ascii")).readLine();
            if (line != null) {
                int pid = Integer.decode(line);
                if (Processes.exists(pid)) {
                    PidStatus pidStatus = new PidStatus();
                    pidStatus.held = true;
                    pidStatus.pid = pid;
                    return pidStatus;
                }
            }
        }
        catch (FileNotFoundException e) {
            return new PidStatus();
        }
        catch (IOException | NumberFormatException ignored) {
        }

        new File(pidFilePath).delete();
        return new PidStatus();
    }
}
