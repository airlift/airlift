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
package io.airlift.api.maven.tests;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class MavenTestSupport
{
    private MavenTestSupport() {}

    static String resolveProjectVersion()
            throws Exception
    {
        String projectVersion = System.getProperty("project.version");
        if (projectVersion != null && !projectVersion.isBlank()) {
            return projectVersion;
        }

        String pom = Files.readString(Path.of("pom.xml"));
        Matcher matcher = Pattern.compile("(?s)<parent>.*?<version>([^<]+)</version>.*?</parent>").matcher(pom);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }

        throw new IllegalStateException("Could not resolve project version from system properties or pom.xml");
    }

    static String resolveLocalRepository()
    {
        String localRepository = System.getProperty("maven.repo.local");
        if (localRepository != null) {
            return localRepository;
        }

        return Path.of(System.getProperty("user.home"), ".m2", "repository")
                .toAbsolutePath()
                .toString();
    }
}
