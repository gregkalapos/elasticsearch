/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the "Elastic License
 * 2.0", the "GNU Affero General Public License v3.0 only", and the "Server Side
 * Public License v 1"; you may not use this file except in compliance with, at
 * your election, the "Elastic License 2.0", the "GNU Affero General Public
 * License v3.0 only", or the "Server Side Public License, v 1".
 */

package org.elasticsearch.test.cluster.util.resource;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.function.Supplier;

public interface Resource {
    InputStream asStream();

    static Resource fromString(String text) {
        return new StringResource(text);
    }

    static Resource fromString(Supplier<String> supplier) {
        return new StringResource(supplier);
    }

    static Resource fromClasspath(String path) {
        return new ClasspathResource(path);
    }

    static Resource fromFile(Path file) {
        return fromFile(() -> file);
    }

    static Resource fromFile(Supplier<Path> file) {
        return new FileResource(file);
    }

    default void writeTo(Path path) {
        try (InputStream is = asStream()) {
            Files.copy(is, path, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
