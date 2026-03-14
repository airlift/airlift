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
package io.airlift.api.maven;

import com.google.common.collect.ImmutableList;
import io.airlift.api.ApiService;
import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.ScanResult;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.List;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static java.util.Objects.requireNonNull;

public class ServiceClassScanner
{
    private final ClassLoader classLoader;
    private final List<String> packages;

    public ServiceClassScanner(ClassLoader classLoader, List<String> packages)
    {
        this.classLoader = requireNonNull(classLoader, "classLoader is null");
        this.packages = ImmutableList.copyOf(requireNonNull(packages, "packages is null"));
    }

    public List<Class<?>> scan()
    {
        ClassGraph classGraph = new ClassGraph()
                .addClassLoader(classLoader)
                .enableClassInfo()
                .enableAnnotationInfo();

        if (!packages.isEmpty()) {
            classGraph.acceptPackages(packages.toArray(new String[0]));
        }

        try (ScanResult scanResult = classGraph.scan()) {
            return scanResult.getClassesWithAnnotation(ApiService.class.getName())
                    .stream()
                    .map(this::loadClass)
                    .collect(toImmutableList());
        }
    }

    private Class<?> loadClass(ClassInfo classInfo)
    {
        try {
            return classLoader.loadClass(classInfo.getName());
        }
        catch (ClassNotFoundException e) {
            throw new IllegalStateException("Failed to load class: " + classInfo.getName(), e);
        }
    }

    static URLClassLoader createClassLoader(List<URL> urls, ClassLoader parent)
    {
        return new URLClassLoader(urls.toArray(new URL[0]), parent);
    }
}
