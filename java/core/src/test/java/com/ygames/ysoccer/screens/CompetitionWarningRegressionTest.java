package com.ygames.ysoccer.screens;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.stream.Stream;

/** Regression for the Chinese warning that crashed both create-competition and load-competition navigation. */
public final class CompetitionWarningRegressionTest {
    /** Checks every bundled translation and edge cases without requiring a graphics context. */
    public static void main(String[] args) throws Exception {
        int checks = 0;
        Path folder = Paths.get(args[0]);
        try (Stream<Path> paths = Files.list(folder)) {
            for (Path path : (Iterable<Path>) paths.filter(p -> p.getFileName().toString().endsWith(".properties"))::iterator) {
                Properties strings = new Properties();
                try (InputStreamReader reader = new InputStreamReader(Files.newInputStream(path), StandardCharsets.UTF_8)) {
                    strings.load(reader);
                }
                for (String category : new String[]{"DIY", "PRESET"}) {
                    String text = strings.getProperty("YOU ARE ABOUT TO LOSE CURRENT " + category + " COMPETITION");
                    if (text != null) {
                        check(text);
                        checks++;
                    }
                }
            }
        }
        for (String text : new String[]{"", "警", "当前自定义赛事将会丢失", "A LONGUNBROKENWORD",
            "UNBROKEN", "\uD83D\uDE00\uD83D\uDE03\uD83D\uDE04"}) {
            check(text);
            checks++;
        }
        System.out.println("Competition warning regression checks passed: " + checks);
    }

    private static void check(String text) {
        String[] lines = CompetitionWarningText.split(text);
        if (lines.length != 2 || !text.replace(" ", "").equals((lines[0] + lines[1]).replace(" ", ""))) {
            throw new AssertionError("Warning text lost: " + text);
        }
        for (String line : lines) {
            if (!line.isEmpty() && (Character.isHighSurrogate(line.charAt(line.length() - 1))
                || Character.isLowSurrogate(line.charAt(0)))) {
                throw new AssertionError("Split Unicode character: " + text);
            }
        }
    }
}
