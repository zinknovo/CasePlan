package com.caseplan;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.regex.Pattern;

@SpringBootApplication
public class App {
    public static void main(String[] args) {
        loadEnv();
        SpringApplication.run(App.class, args);
    }

    /** Load .env from project root into System properties so ${VAR} in yaml resolves. */
    private static void loadEnv() {
        Path env = Paths.get(".env");
        if (!Files.isRegularFile(env)) return;
        try {
            List<String> lines = Files.readAllLines(env);
            Pattern assign = Pattern.compile("^([A-Za-z_][A-Za-z0-9_]*)=(.*)$");
            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
                var m = assign.matcher(trimmed);
                if (m.matches()) {
                    String key = m.group(1);
                    String value = m.group(2).trim();
                    if (value.startsWith("\"") && value.endsWith("\"") && value.length() >= 2)
                        value = value.substring(1, value.length() - 1).replace("\\\"", "\"");
                    else if (value.startsWith("'") && value.endsWith("'") && value.length() >= 2)
                        value = value.substring(1, value.length() - 1);
                    if (System.getProperty(key) == null)
                        System.setProperty(key, value);
                }
            }
        } catch (IOException ignored) { /* .env optional */ }
    }
}
