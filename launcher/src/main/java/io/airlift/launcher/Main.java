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

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.jar.Manifest;

public class Main {

    private static final int STATUS_GENERIC_ERROR = 1;

    public static void main(String[] args)
    {
        ClassLoader classLoader = Main.class.getClassLoader();
        URL launcherResource = classLoader.getResource("META-INF/MANIFEST.MF");
        if (launcherResource == null) {
            System.err.print("Unable to get path of launcher jar manifest\n");
            System.exit(STATUS_GENERIC_ERROR);
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
        try {
            mainClassMethod.invoke(null, (Object)args);
        }
        catch (Exception e) {
            e.printStackTrace();
            System.exit(STATUS_GENERIC_ERROR);
        }
    }
}
