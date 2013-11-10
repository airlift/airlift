/*
 * Copyright 2012 Proofpoint, Inc.
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
package com.proofpoint.launcher;

import com.google.common.base.Joiner;
import io.airlift.command.Arguments;
import io.airlift.command.Cli;
import io.airlift.command.Command;
import io.airlift.command.Help;
import io.airlift.command.Option;
import io.airlift.command.OptionType;
import io.airlift.command.ParseException;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ProcessBuilder.Redirect;
import java.lang.reflect.Method;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.locks.LockSupport;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

import static com.google.common.base.Objects.firstNonNull;
import static com.google.common.base.Preconditions.checkNotNull;

public class Main
{
    private static final int STATUS_GENERIC_ERROR = 1;
    private static final int STATUS_INVALID_ARGS = 2;
    private static final int STATUS_UNSUPPORTED = 3;
    private static final int STATUS_CONFIG_MISSING = 6;

    // Specific to the "status" command
    private static final int STATUS_NOT_RUNNING = 3;

    public static void main(String[] args)
    {
        Cli<Runnable> cli = Cli.buildCli("launcher", Runnable.class)
                .withDescription("The service launcher")
                .withCommands(Help.class, StartCommand.class, StartClientCommand.class,
                        RunCommand.class, RunClientCommand.class,
                        RestartCommand.class, TryRestartCommand.class, ForceReloadCommand.class,
                        StatusCommand.class, StopCommand.class, KillCommand.class)
                .build();

        Runnable parse;
        try {
            parse = cli.parse(args);
        }
        catch (ParseException e) {
            parse = new ParseError(e, cli);
        }
        parse.run();
    }

    abstract static class LauncherCommand implements Runnable
    {
        final String installPath;

        @Option(type = OptionType.GLOBAL, name = {"-v", "--verbose"}, description = "Run verbosely")
        public boolean verbose = false;

        @Option(type = OptionType.GLOBAL, name = "--node-config", description = "Path to node properties file. Defaults to INSTALL_PATH/etc/node.properties")
        public String nodePropertiesPath = null;

        @Option(type = OptionType.GLOBAL, name = "--jvm-config", description = "Path to jvm config file. Defaults to INSTALL_PATH/etc/jvm.config")
        public String jvmConfigPath = null;

        @Option(type = OptionType.GLOBAL, name = "--config", description = "Path to configuration file. Defaults to INSTALL_PATH/etc/config.properties")
        public String configPath = null;

        @Option(type = OptionType.GLOBAL, name = "--data", description = "Path to data directory. Defaults to INSTALL_PATH")
        public String dataDir = null;

        @Option(type = OptionType.GLOBAL, name = "--pid-file", description = "Path to pid file. Defaults to DATA_DIR/var/run/launcher.pid")
        public String pidFilePath = null;
        public String legacyPidFilePath = null;

        @Option(type = OptionType.GLOBAL, name = "--log-file", description = "Path to log file. Defaults to DATA_DIR/var/log/launcher.log")
        public String logPath = null;

        @Option(type = OptionType.GLOBAL, name = "--log-levels-file", description = "Path to log config file. Defaults to INSTALL_PATH/etc/log.config")
        public String logLevelsPath = null;

        @Option(type = OptionType.GLOBAL, name = "-D", description = "Set a Java System property")
        public final List<String> property = new LinkedList<>();

        final Properties systemProperties = new Properties();
        final List<String> launcherArgs = new LinkedList<>();

        LauncherCommand()
        {
            URL launcherResource = Main.class.getProtectionDomain().getCodeSource().getLocation();
            if (launcherResource == null) {
                System.err.print("Unable to get path of launcher jar\n");
                System.exit(STATUS_GENERIC_ERROR);
            }

            try {
                installPath = new File(launcherResource.toURI()).getParentFile().getParent();
            }
            catch (URISyntaxException e) {
                // Can't happen
                throw new RuntimeException(e);
            }

            logLevelsPath = installPath + "/etc/log.properties";
            if (!(new File(logLevelsPath).canRead()) && new File(installPath + "/etc/log.config").canRead()) {
                System.err.print("Did not find a log.properties file, but found a log.config instead.  log.config is deprecated, please use log.properties.");
                logLevelsPath = installPath + "/etc/log.config";
            }
        }

        abstract void execute();

        @Override
        public final void run()
        {
            if (verbose) {
                launcherArgs.add("-v");
            }
            if (nodePropertiesPath == null) {
                nodePropertiesPath = installPath + "/etc/node.properties";
            }
            else {
                launcherArgs.add("--node-config");
                launcherArgs.add(new File(nodePropertiesPath).getAbsolutePath());
            }
            if (jvmConfigPath == null) {
                jvmConfigPath = installPath + "/etc/jvm.config";
            }
            else {
                launcherArgs.add("--jvm-config");
                launcherArgs.add(new File(jvmConfigPath).getAbsolutePath());
            }
            if (configPath == null) {
                configPath = installPath + "/etc/config.properties";
            }
            else {
                launcherArgs.add("--config");
                launcherArgs.add(new File(configPath).getAbsolutePath());
            }
            if (dataDir == null) {
                dataDir = installPath;
            }
            else {
                launcherArgs.add("--data");
                launcherArgs.add(new File(dataDir).getAbsolutePath());
            }
            launcherArgs.add("--log-levels-file");
            launcherArgs.add(new File(logLevelsPath).getAbsolutePath());

            try (InputStream nodeFile = new FileInputStream(nodePropertiesPath)) {
                systemProperties.load(nodeFile);
            }
            catch (FileNotFoundException ignore) {
            }
            catch (IOException e) {
                throw new RuntimeException("Error reading node properties file: " + e);
            }

            for (String s : property) {
                launcherArgs.add("-D");
                launcherArgs.add(s);
                String[] split = s.split("=", 2);
                String key = split[0];
                if (key.equals("config")) {
                    System.out.println("Config can not be passed in a -D argument. Use --config instead");
                    System.exit(STATUS_INVALID_ARGS);
                }

                String value = "";
                if (split.length > 1) {
                    value = split[1];
                }
                systemProperties.setProperty(key, value);
            }

            dataDir = firstNonNull(systemProperties.getProperty("node.data-dir"), dataDir);

            if (pidFilePath == null) {
                pidFilePath = dataDir + "/var/run/platform.pid";
                legacyPidFilePath = dataDir + "/var/run/launcher.pid";
            }
            else {
                launcherArgs.add("--pid-file");
                launcherArgs.add(new File(pidFilePath).getAbsolutePath());
            }
            if (logPath == null) {
                logPath = dataDir + "/var/log/launcher.log";
            }
            else {
                launcherArgs.add("--log-file");
                launcherArgs.add(new File(logPath).getAbsolutePath());
            }

            if (verbose) {
                for (String key : systemProperties.stringPropertyNames()) {
                    System.out.println(key + "=" + systemProperties.getProperty(key));
                }
            }

            execute();
        }

        protected void start(List<String> args, boolean daemon)
        {
            PidFile pidFile = new PidFile(pidFilePath);

            PidStatus pidStatus = pidFile.getStatus();
            if (pidStatus.held) {
                String msg = "Already running";
                if (pidStatus.pid != 0) {
                    msg += " as " + pidStatus.pid;
                }
                System.err.println(msg);
                System.exit(0);
            }

            List<String> javaArgs = new LinkedList<>();
            javaArgs.add("java");

            if (!new File(configPath).exists()) {
                System.err.println("Config file is missing: " + configPath);
                System.exit(STATUS_CONFIG_MISSING);
            }

            try (BufferedReader jvmReader = new BufferedReader(new FileReader(jvmConfigPath))) {
                String line;
                boolean allowSpaces = false;
                while ((line = jvmReader.readLine()) != null) {
                    if (!line.matches("\\s*(?:#.*)?")) {
                        line = line.trim();
                        if (!allowSpaces && line.matches(".*[ '\"\\\\].*")) {
                            System.err.println("JVM config file line contains space or other shell metacharacter: " + line);
                            System.err.println("JVM config file format is one argument per line, no shell quoting.");
                            System.err.println("To indicate you know what you're doing, add before this line the comment line:");
                            System.err.println("# allow spaces");
                            System.exit(STATUS_GENERIC_ERROR);
                        }

                        javaArgs.add(line);
                    }
                    else if (line.matches("(?i)\\s*#\\s*allow\\s+spaces\\s*")) {
                        allowSpaces = true;
                    }
                }
            }
            catch (FileNotFoundException e) {
                System.err.println("JVM config file is missing: " + jvmConfigPath);
                System.exit(STATUS_CONFIG_MISSING);
            }
            catch (IOException e) {
                System.err.println("Error reading JVM config file: " + e);
                System.exit(STATUS_CONFIG_MISSING);
            }

            for (String key : systemProperties.stringPropertyNames()) {
                javaArgs.add("-D" + key + "=" + systemProperties.getProperty(key));
            }
            javaArgs.add("-Dconfig=" + configPath);
            if (daemon) {
                javaArgs.add("-Dlog.output-file=" + logPath);
            }
            if (new File(logLevelsPath).exists()) {
                javaArgs.add("-Dlog.levels-file=" + logLevelsPath);
            }
            javaArgs.add("-jar");
            javaArgs.add(installPath + "/lib/launcher.jar");
            javaArgs.addAll(launcherArgs);
            if (daemon) {
                javaArgs.add("start-client");
            }
            else {
                javaArgs.add("run-client");
            }
            javaArgs.addAll(args);

            if (verbose) {
                System.out.println(Joiner.on(' ').join(javaArgs));
            }

            Process child = null;
            try {
                ProcessBuilder processBuilder = new ProcessBuilder(javaArgs)
                        .directory(new File(dataDir))
                        .redirectInput(Processes.NULL_FILE);
                if (daemon) {
                    processBuilder = processBuilder
                            .redirectOutput(Processes.NULL_FILE)
                            .redirectError(Processes.NULL_FILE);
                }
                else {
                    processBuilder = processBuilder
                            .redirectOutput(Redirect.INHERIT)
                            .redirectError(Redirect.INHERIT);
                }
                child = processBuilder
                        .start();
            }
            catch (IOException e) {
                e.printStackTrace();
                System.exit(STATUS_GENERIC_ERROR);
            }

            if (!daemon) {
                try {
                    System.exit(child.waitFor());
                }
                catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }

            do {
                try {
                    int status = child.exitValue();
                    if (status == 0) {
                        status = STATUS_GENERIC_ERROR;
                    }
                    System.err.println("Failed to start");
                    System.exit(status);
                }
                catch (IllegalThreadStateException ignored) {
                }
                pidStatus = pidFile.waitRunning();
                if (!pidStatus.held) {
                    if (verbose) {
                        System.out.println("Waiting for child to lock pid file");
                    }
                    LockSupport.parkNanos(100_000_000);
                }
            } while (!pidStatus.held);

            System.out.println("Started as " + pidStatus.pid);
            System.exit(0);
        }

        protected void invokeMain(List<String> args, boolean daemon)
        {
            if (!installPath.equals(dataDir)) {
                // symlink etc directory into data directory
                // this is needed to support programs that reference etc/xyz from within their config files (e.g., log.levels-file=etc/log.properties)
                try {
                    Files.delete(Paths.get(dataDir, "etc"));
                }
                catch (IOException ignored) {
                }
                try {
                    Files.createSymbolicLink(Paths.get(dataDir, "etc"), Paths.get(installPath, "etc"));
                }
                catch (IOException ignored) {
                }
            }

            Manifest manifest = null;
            try {
                manifest = new JarFile(installPath + "/lib/main.jar").getManifest();
            }
            catch (IOException e) {
                System.err.println("Unable to open main jar manifest: " + e + "\n");
                System.exit(STATUS_GENERIC_ERROR);
            }

            String mainClassName = manifest.getMainAttributes().getValue("Main-Class");
            if (mainClassName == null) {
                System.err.println("Unable to get Main-Class attribute from main jar manifest");
                System.exit(STATUS_GENERIC_ERROR);
            }

            Class<?> mainClass = null;
            try {
                mainClass = Class.forName(mainClassName);
            }
            catch (ClassNotFoundException e) {
                System.err.println("Unable to load class " + mainClassName + ": " + e);
                System.exit(STATUS_GENERIC_ERROR);
            }
            Method mainClassMethod = null;
            try {
                mainClassMethod = mainClass.getMethod("main", String[].class);
            }
            catch (NoSuchMethodException e) {
                System.err.println("Unable to find main method: " + e);
                System.exit(STATUS_GENERIC_ERROR);
            }

            if (daemon) {
                Processes.detach();
            }

            try {
                mainClassMethod.invoke(null, (Object) args.toArray(new String[0]));
            }
            catch (Throwable e) {
                System.exit(STATUS_GENERIC_ERROR);
            }
        }

        static class KillStatus
        {
            public final int exitCode;
            public final String msg;

            public KillStatus(int exitCode, String msg)
            {
                this.exitCode = exitCode;
                this.msg = checkNotNull(msg, "msg is null");
            }
        }

        KillStatus killProcess(boolean graceful)
        {
            PidStatusSource pidFile = new PidFile(pidFilePath);

            for (int pidTriesLeft = 10; pidTriesLeft > 0; --pidTriesLeft) {
                PidStatus pidStatus = pidFile.getStatus();
                if (!pidStatus.held) {
                    if (legacyPidFilePath != null) {
                        pidFile = new LegacyPidFile(legacyPidFilePath);
                        legacyPidFilePath = null;
                        continue;
                    }
                    return new KillStatus(0, "Not running\n");
                }
                if (pidStatus.pid != 0) {
                    int pid = pidStatus.pid;
                    Processes.kill(pid, graceful);
                    for (int waitTriesLeft = 60 * 10; waitTriesLeft > 0; --waitTriesLeft) {
                        pidStatus = pidFile.getStatus();
                        if (!pidStatus.held || pidStatus.pid != pid) {
                            return new KillStatus(0, (graceful ? "Stopped " : "Killed ") + pid + "\n");
                        }
                        if (waitTriesLeft == 1 && graceful) {
                            waitTriesLeft = 10;
                            graceful = false;
                            Processes.kill(pid, graceful);
                        }
                        LockSupport.parkNanos(100_000_000);
                    }
                    return new KillStatus(STATUS_GENERIC_ERROR, "Process " + pid + " refuses to die\n");
                }
                LockSupport.parkNanos(100_000_000);
            }
            return new KillStatus(STATUS_GENERIC_ERROR, "Unable to get server pid\n");
        }
    }

    @Command(name = "start", description = "Start server")
    public static class StartCommand extends LauncherCommand
    {
        @Arguments(description = "Arguments to pass to server")
        public final List<String> args = new LinkedList<>();

        @Override
        public void execute()
        {
            start(args, true);
        }
    }

    @Command(name = "run", description = "Start server in foreground")
    public static class RunCommand extends LauncherCommand
    {
        @Arguments(description = "Arguments to pass to server")
        public final List<String> args = new LinkedList<>();

        @Override
        public void execute()
        {
            start(args, false);
        }
   }

    @Command(name = "start-client", description = "Internal use only", hidden = true)
    public static class StartClientCommand extends LauncherCommand
    {
        @Arguments(description = "Arguments to pass to server")
        public final List<String> args = new LinkedList<>();

        @SuppressWarnings("StaticNonFinalField")
        private static PidFile pidFile = null; // static so it doesn't destruct and drop lock when main thread exits

        @Override
        public void execute()
        {
            pidFile = new PidFile(pidFilePath);

            try {
                pidFile.indicateStarting();
            }
            catch (AlreadyRunningException e) {
                System.err.println(e.getMessage());
                System.exit(0);
            }

            invokeMain(args, true);

            pidFile.indicateRunning();
        }
    }

    @Command(name = "run-client", description = "Internal use only", hidden = true)
    public static class RunClientCommand extends LauncherCommand
    {
        @Arguments(description = "Arguments to pass to server")
        public final List<String> args = new LinkedList<>();

        @Override
        public void execute()
        {
            invokeMain(args, false);
        }
    }

    @Command(name = "status", description = "Check status of server")
    public static class StatusCommand extends LauncherCommand
    {
        @Override
        public void execute()
        {
            PidFile pidFile = new PidFile(pidFilePath);

            PidStatus pidStatus = pidFile.getStatus();
            if (pidStatus.held) {
                Integer pid = pidStatus.pid;
                String msg = "Starting";

                pidStatus = pidFile.getRunning();
                if (pidStatus.held) {
                    msg = "Running";
                    if (pidStatus.pid != 0) {
                        pid = pidStatus.pid;
                    }
                }
                if (pid != 0) {
                    msg += " as " + pid;
                }
                System.out.println(msg);
                System.exit(0);
            }

            if (legacyPidFilePath != null) {
                pidStatus = new LegacyPidFile(legacyPidFilePath).getStatus();
                if (pidStatus.held) {
                    System.out.println("Running as " + pidStatus.pid);
                    System.exit(0);
                }
            }

            System.out.println("Not running");
            System.exit(STATUS_NOT_RUNNING);
        }
    }

    @Command(name = "restart", description = "Restart server gracefully")
    public static class RestartCommand extends LauncherCommand
    {
        @Arguments(description = "Arguments to pass to server")
        public final List<String> args = new LinkedList<>();

        @Override
        public void execute()
        {
            KillStatus killStatus = killProcess(true);
            if (killStatus.exitCode != 0) {
                System.out.println(killStatus.msg);
                System.exit(killStatus.exitCode);
            }

            start(args, true);
       }
    }

    @Command(name = "try-restart", description = "Restart server gracefully if it is already running")
    public static class TryRestartCommand extends LauncherCommand
    {
        @Arguments(description = "Arguments to pass to server")
        public final List<String> args = new LinkedList<>();

        @Override
        public void execute()
        {
            PidFile pidFile = new PidFile(pidFilePath);

            PidStatus pidStatus = pidFile.getStatus();
            if (!pidStatus.held) {
                System.out.println("Not running");
                System.exit(0);
            }

            KillStatus killStatus = killProcess(true);
            if (killStatus.exitCode != 0) {
                System.out.println(killStatus.msg);
                System.exit(killStatus.exitCode);
            }

            start(args, true);
       }
    }

    @SuppressWarnings("EmptyClass")
    @Command(name = "force-reload", description = "Cause server configuration to be reloaded")
    public static class ForceReloadCommand extends TryRestartCommand
    {
    }

    @Command(name = "stop", description = "Stop server gracefully")
    public static class StopCommand extends LauncherCommand
    {
        @Override
        public void execute()
        {
            KillStatus killStatus = killProcess(true);
            System.out.println(killStatus.msg);
            System.exit(killStatus.exitCode);
       }
    }

    @Command(name = "kill", description = "Hard stop of server")
    public static class KillCommand extends LauncherCommand
    {
        @Override
        public void execute()
        {
            KillStatus killStatus = killProcess(false);
            System.out.println(killStatus.msg);
            System.exit(killStatus.exitCode);
       }
    }

    @Command(name = "ParseError")
    public static class ParseError implements Runnable
    {
        private final ParseException e;
        private final Cli<Runnable> cli;

        public ParseError(ParseException e, Cli<Runnable> cli)
        {
            this.e = e;
            this.cli = cli;
        }

        @Override
        public void run()
        {
            final int status;
            if (e.getMessage().equals("No command specified")) {
                status = STATUS_UNSUPPORTED;
            }
            else {
                status = STATUS_INVALID_ARGS;
            }

            System.err.println(e.getMessage());
            System.err.print("\n");
            cli.parse("help").run();

            System.exit(status);
        }
    }
}
