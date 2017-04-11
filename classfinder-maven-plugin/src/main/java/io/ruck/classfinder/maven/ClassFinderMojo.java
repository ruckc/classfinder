/*
 * Copyright 2017 ruckc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.ruck.classfinder.maven;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.annotation.Annotation;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.reflections.Reflections;
import org.reflections.util.ConfigurationBuilder;

/**
 *
 * @author ruckc
 */
@Mojo(name = "classfinder", defaultPhase = LifecyclePhase.PROCESS_CLASSES)
public class ClassFinderMojo extends AbstractMojo {

    @Parameter(required = true)
    private ClassFinderFilter filter;

    @Parameter(required = true)
    private String name;

    @Parameter(defaultValue = "${project}", required = true, readonly = true)
    private MavenProject project;

    /**
     * Injected value of our classpath elements. This is used in conjunction
     * with the Reflections API in order to help identify where annotations
     * appear on methods.
     */
    @Parameter(defaultValue = "${project.compileClasspathElements}", readonly = true, required = true)
    private List<String> projectClasspathElements;

    @Parameter(defaultValue = "${project.build.directory}/classes", required = true, readonly = true)
    private File classFolder;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        getLog().debug("Filter: " + filter);
        try {
            // these paths should include our module's source root plus any generated code
            List<String> compileSourceOutputs = Collections.singletonList(project.getBuild().getOutputDirectory());
            URL[] sourceFiles = buildMavenClasspath(compileSourceOutputs);

            List<String> results = new ArrayList<>();
            Reflections reflections = new Reflections(
                    new ConfigurationBuilder()
                            .setUrls(sourceFiles)
                            .addClassLoader(getClassLoader())
            );
            if (filter.getSubTypesOf() != null) {
                for (String className : filter.getSubTypesOf()) {
                    getLog().debug("Processing subtypes of " + className);
                    Class<?> cls = Class.forName(className);
                    reflections.getSubTypesOf(cls).stream().map(c -> c.getName()).forEach(results::add);
                }
            }
            if (filter.getTypesAnnotatedWith() != null) {
                for (String annotation : filter.getTypesAnnotatedWith()) {
                    getLog().debug("Processing annotation " + annotation);
                    Class<?> maybeAnnotation = Class.forName(annotation);
                    Class<? extends Annotation> cls = maybeAnnotation.asSubclass(Annotation.class);
                    reflections.getTypesAnnotatedWith(cls).stream().map(c -> c.getName()).forEach(results::add);
                }
            }
            writeList(name, results);
        } catch (DependencyResolutionRequiredException | IOException | ClassNotFoundException e) {
            throw new MojoFailureException("Unable to process configuration", e);
        }
    }

    public ClassLoader getClassLoader() throws DependencyResolutionRequiredException, MojoExecutionException {
        // the project classpath includes the source roots plus the transitive
        // dependencies according to our Mojo annotation's requiresDependencyResolution
        // attribute.
        URL[] projectClasspath = buildMavenClasspath(this.projectClasspathElements);

        URLClassLoader projectClassloader = new URLClassLoader(projectClasspath);

        return projectClassloader;
    }

    /**
     * Extracted this method simply for unit testing.
     *
     * @param classpathElements List of class path entries from the maven
     * project
     * @return array of URL's for the classpath
     * @throws MojoExecutionException only thrown if we can't convert a
     * classpath element to a URL which shouldn't happen
     */
    protected URL[] buildMavenClasspath(List<String> classpathElements) throws MojoExecutionException {
        List<URL> projectClasspathList = new ArrayList<>();
        for (String element : classpathElements) {
            try {
                projectClasspathList.add(new File(element).toURI().toURL());
            } catch (MalformedURLException e) {
                throw new MojoExecutionException(element + " is an invalid classpath element", e);
            }
        }

        return projectClasspathList.toArray(new URL[projectClasspathList.size()]);
    }

    public void writeList(String name, List<String> results) throws IOException {
        Path parent = Paths.get("META-INF", "classfinder");
        Path outputFile = classFolder.toPath().resolve(parent.resolve(name));
        Files.createDirectories(outputFile.getParent());
        Files.createFile(outputFile);
        try (OutputStream out = Files.newOutputStream(outputFile);
                PrintStream print = new PrintStream(out)) {
            results.stream().peek(line -> getLog().info("   Adding " + line + " to " + name)).forEach(print::println);
        }
    }
}
