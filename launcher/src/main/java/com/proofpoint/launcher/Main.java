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
import com.google.common.base.Preconditions;
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
import java.net.JarURLConnection;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.locks.LockSupport;
import java.util.jar.Manifest;

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
        final URL launcherResource;
        final String install_path;

        @Option(type = OptionType.GLOBAL, name = {"-v", "--verbose"}, description = "Run verbosely")
        public boolean verbose = false;

        @Option(type = OptionType.GLOBAL, name = "--node-config", description = "Path to node properties file. Defaults to INSTALL_PATH/etc/node.properties")
        public String node_properties_path = null;

        @Option(type = OptionType.GLOBAL, name = "--jvm-config", description = "Path to jvm config file. Defaults to INSTALL_PATH/etc/jvm.config")
        public String jvm_config_path = null;

        @Option(type = OptionType.GLOBAL, name = "--config", description = "Path to configuration file. Defaults to INSTALL_PATH/etc/config.properties")
        public String config_path = null;

        @Option(type = OptionType.GLOBAL, name = "--data", description = "Path to data directory. Defaults to INSTALL_PATH")
        public String data_dir = null;

        @Option(type = OptionType.GLOBAL, name = "--pid-file", description = "Path to pid file. Defaults to DATA_DIR/var/run/launcher.pid")
        public String pid_file_path = null;

        @Option(type = OptionType.GLOBAL, name = "--log-file", description = "Path to log file. Defaults to DATA_DIR/var/log/launcher.log")
        public String log_path = null;

        @Option(type = OptionType.GLOBAL, name = "--log-levels-file", description = "Path to log config file. Defaults to INSTALL_PATH/etc/log.config")
        public String log_levels_path = null;

        @Option(type = OptionType.GLOBAL, name = "-D", description = "Set a Java System property")
        public final List<String> property = new LinkedList<>();

        final Properties system_properties = new Properties();
        final List<String> launcherArgs = new LinkedList<>();

        LauncherCommand()
        {
            ClassLoader classLoader = Main.class.getClassLoader();
            launcherResource = classLoader.getResource("META-INF/MANIFEST.MF");
            if (launcherResource == null) {
                System.err.print("Unable to get path of launcher jar manifest\n");
                System.exit(STATUS_GENERIC_ERROR);
            }

            URLConnection urlConnection = null;
            try {
                urlConnection = new URL(launcherResource.toString().replaceFirst("!.*", "!/")).openConnection();
            }
            catch (IOException e) {
                System.err.print("Unable to open launcher jar: " + e);
                System.exit(STATUS_GENERIC_ERROR);
            }
            try {
                install_path = new File(((JarURLConnection) urlConnection).getJarFileURL().toURI()).getParentFile().getParent();
            }
            catch (URISyntaxException e) {
                // Can't happen
                throw new RuntimeException(e);
            }

            log_levels_path = install_path + "/etc/log.properties";
            if (!(new File(log_levels_path).canRead()) && new File(install_path + "/etc/log.config").canRead()) {
                System.err.print("Did not find a log.properties file, but found a log.config instead.  log.config is deprecated, please use log.properties.");
                log_levels_path = install_path + "/etc/log.config";
            }
        }

        abstract void execute();

        @Override
        public final void run()
        {
            if (verbose) {
                launcherArgs.add("-v");
            }
            if (node_properties_path == null) {
                node_properties_path = install_path + "/etc/node.properties";
            }
            else {
                launcherArgs.add("--node-config");
                launcherArgs.add(new File(node_properties_path).getAbsolutePath());
            }
            if (jvm_config_path == null) {
                jvm_config_path = install_path + "/etc/jvm.config";
            }
            else {
                launcherArgs.add("--jvm-config");
                launcherArgs.add(new File(jvm_config_path).getAbsolutePath());
            }
            if (config_path == null) {
                config_path = install_path + "/etc/config.properties";
            }
            else {
                launcherArgs.add("--config");
                launcherArgs.add(new File(config_path).getAbsolutePath());
            }
            if (data_dir == null) {
                data_dir = install_path;
            }
            else {
                launcherArgs.add("--data");
                launcherArgs.add(new File(data_dir).getAbsolutePath());
            }
            launcherArgs.add("--log-levels-file");
            launcherArgs.add(new File(log_levels_path).getAbsolutePath());

            try (InputStream nodeFile = new FileInputStream(node_properties_path)) {
                system_properties.load(nodeFile);
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
                    System.out.print("Config can not be passed in a -D argument. Use --config instead\n");
                    System.exit(STATUS_INVALID_ARGS);
                }
                system_properties.setProperty(key, split[1]);
            }

            if (system_properties.containsKey("node.data-dir")) {
                data_dir = system_properties.getProperty("node.data_dir");
            }

            if (pid_file_path == null) {
                pid_file_path = data_dir + "/var/run/launcher.pid";
            }
            else {
                launcherArgs.add("--pid-file");
                launcherArgs.add(new File(pid_file_path).getAbsolutePath());
            }
            if (log_path == null) {
                log_path = data_dir + "/var/log/launcher.log";
            }
            else {
                launcherArgs.add("--log-file");
                launcherArgs.add(new File(log_path).getAbsolutePath());
            }

            if (verbose) {
                for (String key : system_properties.stringPropertyNames()) {
                    System.out.print(key + "=" + system_properties.getProperty(key) + "\n");
                }
            }

            execute();
        }

        static class KillStatus
        {
            public final int code;
            public final String msg;

            public KillStatus(int code, String msg)
            {
                Preconditions.checkNotNull(msg, "msg is null");

                this.code = code;
                this.msg = msg;
            }
        }

        KillStatus killProcess(boolean graceful)
        {
            PidFile pidFile = new PidFile(pid_file_path);

            for (int pidTriesLeft = 10; pidTriesLeft > 0; --pidTriesLeft) {
                PidStatus pidStatus = pidFile.get();
                if (!pidStatus.held) {
                   return new KillStatus(0, "Not running\n");
                }
                if (pidStatus.pid != 0) {
                    int pid = pidStatus.pid;
                    Porting.kill(pid, graceful);
                    for (int waitTriesLeft = 60 * 10; waitTriesLeft > 0; --waitTriesLeft) {
                        pidStatus = pidFile.get();
                        if (!pidStatus.held || pidStatus.pid != pid) {
                            return new KillStatus(0, (graceful ? "Stopped " : "Killed ") + pid + "\n");
                        }
                        if (waitTriesLeft == 1 && graceful) {
                            waitTriesLeft = 10;
                            graceful = false;
                            Porting.kill(pid, graceful);
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
        boolean daemon = true;

        @Override
        public void execute()
        {
            PidFile pidFile = new PidFile(pid_file_path);

            PidStatus pidStatus = pidFile.get();
            if (pidStatus.held) {
                String msg = "Already running";
                if (pidStatus.pid != 0) {
                    msg += " as " + pidStatus.pid;
                }
                System.err.print(msg + "\n");
                System.exit(0);
            }

            List<String> javaArgs = new LinkedList<>();
            javaArgs.add("java");

            if (!new File(config_path).exists()) {
                System.err.print("Config file is missing: " + config_path);
                System.exit(STATUS_CONFIG_MISSING);
            }

            try (BufferedReader jvmReader = new BufferedReader(new FileReader(jvm_config_path))) {
                String line;
                while ((line = jvmReader.readLine()) != null) {
                    if (!line.matches("\\s*(?:#.*)?")) {
                        javaArgs.add(line.trim());
                    }
                }
            }
            catch (FileNotFoundException e) {
                System.err.print("JVM config file is missing: " + jvm_config_path);
                System.exit(STATUS_CONFIG_MISSING);
            }
            catch (IOException e) {
                System.err.print("Error reading JVM config file: " + e);
                System.exit(STATUS_CONFIG_MISSING);
            }

            for (String key : system_properties.stringPropertyNames()) {
                javaArgs.add("-D" + key + "=" + system_properties.getProperty(key));
            }
            javaArgs.add("-Dconfig=" + config_path);
            if (daemon) {
                javaArgs.add("-Dlog.output-file=" + log_path);
            }
            if (new File(log_levels_path).exists()) {
                javaArgs.add("-Dlog.levels-file=" + log_levels_path);
            }
            javaArgs.add("-jar");
            javaArgs.add(install_path + "/lib/launcher.jar");
            javaArgs.addAll(launcherArgs);
            if (daemon) {
                javaArgs.add("start-client");
            }
            else {
                javaArgs.add("run-client");
            }
            javaArgs.addAll(args);

            if (verbose) {
                System.out.print(Joiner.on(' ').join(javaArgs) + "\n");
            }

            Process child = null;
            try {
                child = new ProcessBuilder(javaArgs)
                        .directory(new File(data_dir))
                        .redirectInput(Porting.NULL_FILE)
                        .redirectOutput(Redirect.INHERIT)
                        .redirectError(Redirect.INHERIT)
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
                    System.err.print("Failed to start\n");
                    System.exit(status);
                }
                catch (IllegalThreadStateException ignored) {
                }
                pidStatus = pidFile.waitRunning();
                if (!pidStatus.held) {
                    if (verbose) {
                        System.out.print("Waiting for child to lock pid file\n");
                    }
                    LockSupport.parkNanos(100_000_000);
                }
            } while (!pidStatus.held);

            System.out.print("Started as " + pidStatus.pid + "\n");
            System.exit(0);
        }
    }

    @Command(name = "run", description = "Start server in foreground")
    public static class RunCommand extends StartCommand
    {
        public RunCommand()
        {
            daemon = false;
        }
   }

    @Command(name = "start-client", description = "Internal use only", hidden = true)
    public static class StartClientCommand extends LauncherCommand
    {
        @Arguments(description = "Arguments to pass to server")
        public final List<String> argList = new LinkedList<>();
        boolean daemon = true;

        @SuppressWarnings("StaticNonFinalField")
        private static PidFile pidFile = null; // static so it doesn't destruct and drop lock when main thread exits

        @Override
        public void execute()
        {
            if (daemon) {
                //noinspection AssignmentToStaticFieldFromInstanceMethod
                pidFile = new PidFile(pid_file_path);

                int otherPid = pidFile.starting();
                if (otherPid != -1) {
                    String msg = "Already running";
                    if (otherPid != 0) {
                        msg += " as " + otherPid;
                    }
                    System.err.print(msg + "\n");
                    System.exit(0);
                }
            }

            if (!install_path.equals(data_dir)) {
                // symlink etc directory into data directory
                // this is needed to support programs that reference etc/xyz from within their config files (e.g., log.levels-file=etc/log.properties)
                try {
                    Files.delete(Paths.get(data_dir, "etc"));
                }
                catch (IOException ignored) {
                }
                try {
                    Files.createSymbolicLink(Paths.get(data_dir, "etc"), Paths.get(install_path, "etc"));
                }
                catch (IOException ignored) {
                }
            }

            URL mainResource;
            try {
                mainResource = new URL(launcherResource.toString().replaceFirst("/launcher.jar!", "/main.jar!"));
            }
            catch (MalformedURLException e) {
                // Can't happen
                throw new RuntimeException(e);
            }
            Manifest manifest = null;
            try {
                manifest = new Manifest(mainResource.openStream());
            }
            catch (IOException e) {
                System.err.print("Unable to open main jar manifest: " + e + "\n");
                System.exit(STATUS_GENERIC_ERROR);
            }

            String mainClassName = manifest.getMainAttributes().getValue("Main-Class");
            if (mainClassName == null) {
                System.err.print("Unable to get Main-Class attribute from main jar manifest\n");
                System.exit(STATUS_GENERIC_ERROR);
            }

            Class<?> mainClass = null;
            try {
                mainClass = Class.forName(mainClassName);
            }
            catch (ClassNotFoundException e) {
                System.err.print("Unable to load class " + mainClassName + ": " + e + "\n");
                System.exit(STATUS_GENERIC_ERROR);
            }
            Method mainClassMethod = null;
            try {
                mainClassMethod = mainClass.getMethod("main", String[].class);
            }
            catch (NoSuchMethodException e) {
                System.err.print("Unable to find main method: " + e + "\n");
                System.exit(STATUS_GENERIC_ERROR);
            }

            if (daemon) {
                Porting.detach();
            }

            try {
                mainClassMethod.invoke(null, (Object) argList.toArray(new String[0]));
            }
            catch (Exception e) {
                System.exit(STATUS_GENERIC_ERROR);
            }

            if (daemon) {
                pidFile.running();
            }
        }
    }

    @Command(name = "run-client", description = "Internal use only", hidden = true)
    public static class RunClientCommand extends StartClientCommand
    {
        public RunClientCommand()
        {
            daemon = false;
        }
    }

    @Command(name = "status", description = "Check status of server")
    public static class StatusCommand extends LauncherCommand
    {
        @Override
        public void execute()
        {
            PidFile pidFile = new PidFile(pid_file_path);

            PidStatus pidStatus = pidFile.get();
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
                System.out.print(msg + "\n");
                System.exit(0);
            }

            System.out.print("Not running\n");
            System.exit(STATUS_NOT_RUNNING);
        }
    }

    @Command(name = "restart", description = "Restart server gracefully")
    public static class RestartCommand extends StartCommand
    {
        @Override
        public void execute()
        {
            KillStatus killStatus = killProcess(true);
            if (killStatus.code != 0) {
                System.out.print(killStatus.msg);
                System.exit(killStatus.code);
            }

            super.execute();
       }
    }

    @Command(name = "try-restart", description = "Restart server gracefully if it is already running")
    public static class TryRestartCommand extends StartCommand
    {
        @Override
        public void execute()
        {
            PidFile pidFile = new PidFile(pid_file_path);

            PidStatus pidStatus = pidFile.get();
            if (!pidStatus.held) {
                System.out.print("Not running\n");
                System.exit(0);
            }

            KillStatus killStatus = killProcess(true);
            if (killStatus.code != 0) {
                System.out.print(killStatus.msg);
                System.exit(killStatus.code);
            }

            super.execute();
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
            System.out.print(killStatus.msg);
            System.exit(killStatus.code);
       }
    }

    @Command(name = "kill", description = "Hard stop of server")
    public static class KillCommand extends LauncherCommand
    {
        @Override
        public void execute()
        {
            KillStatus killStatus = killProcess(false);
            System.out.print(killStatus.msg);
            System.exit(killStatus.code);
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

            System.err.print(e.getMessage());
            System.err.print("\n\n");
            cli.parse("help").run();

            System.exit(status);
        }
    }
}
