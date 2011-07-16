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
package com.proofpoint.rackpackager;

import com.google.common.io.Files;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;
import org.testng.annotations.Test;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.UUID;
import java.util.jar.JarFile;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertTrue;

public class TestRackPackager
{
    @Test(expectedExceptions = MojoExecutionException.class,
          expectedExceptionsMessageRegExp = ".*Please ensure a Gemfile exists, is readable, and is in the root of your project structure.*")
    public void testThrowsMojoExceptionForMissingGemfile()
            throws MojoExecutionException, MojoFailureException
    {
        File sourceDirectory = Files.createTempDir();
        File buildDirectory = Files.createTempDir();

        RackPackager packager = new RackPackager();
        packager.setOutputDirectory(buildDirectory);
        packager.setProject(createMockMavenProject(sourceDirectory, "TestName", "test-version"));
        packager.execute();
    }

    @Test(expectedExceptions = MojoExecutionException.class,
          expectedExceptionsMessageRegExp = ".*Try running bundle install manually to verify the contents of the Gemfile.*")
    public void testThrowsMojoExceptionForBadGemsGemfile()
            throws MojoExecutionException, MojoFailureException, IOException
    {
        File sourceDirectory = Files.createTempDir();
        File buildDirectory = Files.createTempDir();

        createGemfile(UUID.randomUUID().toString(), UUID.randomUUID().toString(), sourceDirectory);

        RackPackager packager = new RackPackager();
        packager.setOutputDirectory(buildDirectory);
        packager.setProject(createMockMavenProject(sourceDirectory, "TestName", "test-version"));
        packager.execute();
    }

    @Test
    public void testPackaging()
            throws MojoExecutionException, MojoFailureException, IOException
    {
        String gemName = "rspec";
        String gemVersion = "2.6.0";

        File sourceDirectory = Files.createTempDir();
        File buildDirectory = Files.createTempDir();

        createGemfile(gemName, gemVersion, sourceDirectory);

        MavenProject mockProject = createMockMavenProject(sourceDirectory, "TestName", "test-version");
        RackPackager packager = new RackPackager();
        packager.setOutputDirectory(buildDirectory);
        packager.setProject(mockProject);
        packager.execute();

        String expectedGemRepoJarLocation = String.format("%s/%s-%s-gemrepo.jar", buildDirectory.getCanonicalPath(), mockProject.getName(), mockProject.getVersion());
        assertTrue(new File(expectedGemRepoJarLocation).exists(), "Jar not found, expected it to be located at [" + expectedGemRepoJarLocation + "]");

        JarFile gemRepoJar = new JarFile(expectedGemRepoJarLocation);
        assertTrue(gemRepoJar.getEntry("specifications") != null,
                "Did not find the specifications directory in the gemrepo jar at [" + expectedGemRepoJarLocation + "]");
        assertTrue(gemRepoJar.getEntry(String.format("specifications/%s-%s.gemspec",gemName, gemVersion)) != null,
                String.format("Did not find the gemspec for [%s-%s] in the gemrepo jar at [%s]", gemName, gemVersion, expectedGemRepoJarLocation));
        assertTrue(gemRepoJar.getEntry("gems") != null,
                "Did not find the gems directory in the gemrepo jar at [" + expectedGemRepoJarLocation + "]");
        assertTrue(gemRepoJar.getEntry(String.format("gems/%s-%s",gemName, gemVersion)) != null,
                String.format("Did not find the gems directory for [%s-%s] in the gemrepo jar at [%s]", gemName, gemVersion, expectedGemRepoJarLocation));
    }

    private void createGemfile(String gemName, String gemVersion, File sourceDirectory)
            throws IOException
    {
        FileWriter gemFileWriter = new FileWriter(new File(sourceDirectory.getCanonicalFile() + "/Gemfile"));
        gemFileWriter.write("source 'http://rubygems.org'\n");
        gemFileWriter.write(String.format("gem '%s', '%s'\n", gemName, gemVersion));
        gemFileWriter.close();
    }

    private MavenProject createMockMavenProject(File sourceDirectory, String name, String version)
    {
        MavenProject project = mock(MavenProject.class);
        when(project.getBasedir()).thenReturn(sourceDirectory);
        when(project.getName()).thenReturn(name);
        when(project.getVersion()).thenReturn(version);
        return project;
    }
}
