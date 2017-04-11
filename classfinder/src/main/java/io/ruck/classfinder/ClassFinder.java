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
package io.ruck.classfinder;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 *
 * @author ruckc
 */
public final class ClassFinder {

    /**
     * Logger.
     */
    private static final Logger LOG = Logger.getLogger(
            ClassFinder.class.getName());

    /**
     * ClassFinder cache.
     */
    private static final Map<String, ClassFinder> CLASSFINDERS
            = new ConcurrentHashMap<>();

    /**
     * Cached Regex to reduce processing times.
     */
    private static final Pattern SCANNER_PATTERN = Pattern.compile("\\n+");

    /**
     * Name sanitization pattern.
     */
    private static final Pattern NAME_PATTERN = Pattern
            .compile("[a-zA-Z0-9-_]+");

    /**
     * Returns a named ClassFinder instance. This may return a previously
     * generated ClassFinder. This is not safe for applications that dynamically
     * change elements on the classpath. Uses
     * Thread.currentThread().getContextClassLoader() as default ClassLoader.
     *
     * @param name the ClassFinder instance name
     * @return ClassFinder
     */
    public static ClassFinder getInstance(final String name) {
        return getInstance(name, Thread.currentThread()
                .getContextClassLoader());
    }

    /**
     * Returns a named ClassFinder instance. This may return a previously
     * generated ClassFinder. This is not safe for applications that dynamically
     * change elements on the classpath.
     *
     * @param name the ClassFinder instance name
     * @param cl the ClassLoader to use for Class lookup
     * @return ClassFinder
     */
    public static ClassFinder getInstance(
            final String name,
            final ClassLoader cl
    ) {
        sanitizeName(name);
        return CLASSFINDERS.computeIfAbsent(name, n -> new ClassFinder(n, cl));
    }

    /**
     *
     * @param name untrusted input
     */
    private static void sanitizeName(final String name) {
        if (!NAME_PATTERN.matcher(name).matches()) {
            throw new InvalidParameterException(
                    "The name parameter should match " + NAME_PATTERN.pattern()
            );
        }
    }

    /**
     * ClassFinder name.
     */
    private final String name;

    /**
     * ClassLoader for lookup.
     */
    private final ClassLoader classLoader;

    /**
     * list of generated classes.
     */
    private final List<Class<?>> classes = new ArrayList<>();

    /**
     * Cached unmodifiable list wrapper.
     */
    private final List<Class<?>> unmodifiable = Collections
            .unmodifiableList(classes);

    /**
     * ClassFinder constructor.
     *
     * @param str of ClassFinder instance
     * @param cl to utilize for Class lookup.
     */
    private ClassFinder(final String str, final ClassLoader cl) {
        sanitizeName(str);
        this.name = str;
        this.classLoader = cl;
        reload();
    }

    /**
     * Reloads internal Class cache.
     */
    public void reload() {
        try {
            Set<Class<?>> temp = new HashSet<>();
            Enumeration<URL> resources = classLoader.getResources(
                    String.format("META-INF/classfinder/" + name)
            );
            if (!resources.hasMoreElements()) {
                LOG.log(Level.WARNING,
                        "Unable to find any classfinder files for ''{0}''",
                        name
                );
            }
            while (resources.hasMoreElements()) {
                URL resource = resources.nextElement();
                try (InputStream is = resource.openStream()) {
                    Scanner scanner = new Scanner(is,
                            StandardCharsets.UTF_8.name()
                    );
                    scanner.useDelimiter(SCANNER_PATTERN);
                    while (scanner.hasNext()) {
                        String line = scanner.next();
                        line = line.trim();
                        if (!line.isEmpty()) {
                            try {
                                temp.add(Class.forName(
                                        line,
                                        true,
                                        classLoader
                                ));
                            } catch (ClassNotFoundException e) {
                                LOG.log(
                                        Level.WARNING,
                                        "Unable to resolve class {0}"
                                        + ", skipping it",
                                        line
                                );
                            }
                        }
                    }
                }
            }
            classes.clear();
            classes.addAll(temp);
        } catch (IOException ex) {
            throw new RuntimeException(
                    "Cannot load classes due to IOException",
                    ex);
        }
    }

    /**
     *
     * @return List of classes.
     */
    public List<Class<?>> getClasses() {
        return unmodifiable;
    }
}
