/*
 * Copyright 2010 Proofpoint, Inc.
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

import com.google.common.base.Preconditions;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;

@SuppressWarnings("FieldCanBeLocal")
class PidFile
{
    private final String pid_file_path;
    private FileChannel pidChannel = null;
    private FileChannel lockChannel = null;
    private FileLock startLock = null;

    PidFile(String pid_file_path)
    {
        Preconditions.checkNotNull(pid_file_path, "pid_file_path is null");
        this.pid_file_path = pid_file_path;
    }

    int starting()
    {
        //noinspection ResultOfMethodCallIgnored
        new File(pid_file_path).getParentFile().mkdirs();
        try {
            pidChannel = new RandomAccessFile(pid_file_path, "rw").getChannel();
        }
        catch (FileNotFoundException e) {
            throw new RuntimeException("Cannot open pid file: " + e);
        }

        if (System.getProperty("os.name").startsWith("Windows")) {
            // Windows file locks are mandatory: use a separate lock file so that
            // the pid file can be read by other processes
            try {
                lockChannel = new RandomAccessFile(pid_file_path + ".lck", "rw").getChannel();
            }
            catch (FileNotFoundException e) {
                throw new RuntimeException("Cannot open pid lockfile: " + e);
            }
        }
        else {
            lockChannel = pidChannel;
        }

        try {
            startLock = lockChannel.tryLock(0, 1, false);
        }
        catch (IOException e) {
            throw new RuntimeException("Cannot lock pid file: " + e);
        }

        if (startLock != null) {
            // There is a race here between the time we lock the file and the
            // time we write our pid into it. This could be fixed by readers using
            // the pid returned by fcntl(F_GETLK) instead of the file contents, but
            // have not yet found a way to call fcntl(F_GETLK) from Java.
            try {
                pidChannel.truncate(0);
                pidChannel.write(ByteBuffer.wrap((Integer.toString(Porting.getpid()) + "\n").getBytes()));
            }
            catch (IOException e) {
                throw new RuntimeException("Cannot write to pid file: " + e);
            }
        }
        else {
            try {
                String line = new BufferedReader(Channels.newReader(pidChannel, "us-ascii")).readLine();
                return Integer.decode(line.trim());
            }
            catch (IOException e) {
                throw new RuntimeException("Cannot read pid file: " + e);
            }
        }
        return 0;
    }
}
