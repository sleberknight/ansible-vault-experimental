package org.example.ansible.vault.testing;

import com.google.common.io.Resources;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Fixtures {

    public static String fixture(String resourceName) {
        try {
            @SuppressWarnings("UnstableApiUsage") var url = Resources.getResource(resourceName);
            var path = Paths.get(url.toURI());
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (URISyntaxException | IOException e) {
            throw new RuntimeException("Error reading fixture: " + resourceName, e);
        }
    }

    public static Path fixturePath(String resourceName) {
        @SuppressWarnings("UnstableApiUsage") var url = Resources.getResource(resourceName);
        try {
            return Paths.get(url.toURI());
        } catch (URISyntaxException e) {
            throw new RuntimeException("Error getting path of fixture: " + resourceName, e);
        }
    }
}
