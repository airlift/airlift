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

import io.airlift.command.Arguments;
import io.airlift.command.Cli;
import io.airlift.command.Command;
import io.airlift.command.Help;
import io.airlift.command.Option;
import io.airlift.command.OptionType;
import io.airlift.command.ParseException;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.JarURLConnection;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.jar.Manifest;

public class Main
{
    private static final int STATUS_GENERIC_ERROR = 1;
    private static final int STATUS_INVALID_ARGS = 2;
    private static final int STATUS_UNSUPPORTED = 3;

    public static void main(String[] args)
    {
        Cli<Runnable> cli = Cli.buildCli("launcher", Runnable.class)
                .withDescription("The service launcher")
                .withCommands(Help.class, StartClientCommand.class)
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
        private final Map<String, String> system_properties = new HashMap<>();

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

            node_properties_path = install_path + "/etc/node.properties";
            jvm_config_path = install_path + "/etc/jvm.config";
            config_path = install_path + "/etc/config.properties";
            data_dir = install_path;

            log_levels_path = install_path + "/etc/log.config";
            if (!(new File(log_levels_path).canRead()) && new File(install_path + "/etc/log.properties").canRead()) {
//todo                System.err.print("Did not find a log.properties file, but found a log.config instead.  log.config is deprecated, please use log.properties.");
                 log_levels_path = install_path + "/etc/log.properties";
            }
        }

        abstract void execute();

        @Override
        public final void run()
        {
            try (BufferedReader nodeReader = new BufferedReader(new FileReader(node_properties_path))) {
                String line;
                while ((line = nodeReader.readLine()) != null) {
                    if (!line.matches("\\s*#.*")) {
                        String[] split = line.split("=", 2);
                        system_properties.put(split[0], split[1]);
                    }
                }
            }
            catch (FileNotFoundException ignore) {
            }
            catch (IOException e) {
                throw new RuntimeException("Error reading node properties file: " + e);
            }

            for (String s : property) {
                String[] split = s.split("=", 2);
                String key = split[0];
                if (key.equals("config")) {
                    System.out.print("Config can not be passed in a -D argument. Use --config instead\n");
                    System.exit(STATUS_INVALID_ARGS);
                }
                system_properties.put(key, split[1]);
            }

            if (system_properties.containsKey("node.data-dir")) {
                data_dir = system_properties.get("node.data_dir");
            }

            if (pid_file_path == null) {
                pid_file_path = data_dir + "/var/run/launcher.pid";
            }
            if (log_path == null) {
                log_path = data_dir + "/var/log/launcher.log";
            }

            if (verbose) {
                //todo dump properties
            }

            //todo symlink etc into data directory

            execute();
        }

    }

    @Command(name = "start-client", description = "Internal use only", hidden = true)
    public static class StartClientCommand extends LauncherCommand
    {
        @Arguments(description = "Arguments to pass to server")
        public final List<String> argList = new LinkedList<>();

        @Override
        public void execute()
        {
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
            try {
                mainClassMethod.invoke(null, (Object) argList.toArray(new String[0]));
            }
            catch (Exception e) {
                e.printStackTrace();
                System.exit(STATUS_GENERIC_ERROR);
            }
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
