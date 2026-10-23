package com.mahiru.gui;

import java.nio.file.Path;

/** Resolves paths against the Gradle project directory (the unit-test working directory is not the project). */
final class TestPaths {
    static Path project(String rel) {
        return Path.of(System.getProperty("project.dir", ".")).resolve(rel);
    }

    private TestPaths() {}
}
