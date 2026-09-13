package com.finotech.jewellery.shared.app;

import java.util.Arrays;

/**
 * A {@code major.minor.patch} version, compared numerically.
 *
 * <p>Tolerant of what app builds actually send: a missing minor or patch reads
 * as zero, and a {@code +build} suffix (Flutter's {@code 1.2.3+45}) or a
 * {@code -pre} suffix is ignored, so {@code 1.2.3+7} equals {@code 1.2.3}.
 */
public record SemanticVersion(int major, int minor, int patch) implements Comparable<SemanticVersion> {

    /**
     * @throws IllegalArgumentException when the text is not a version
     */
    public static SemanticVersion parse(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Version must not be blank");
        }
        String core = text.trim();
        int cut = indexOfAny(core, '+', '-');
        if (cut >= 0) {
            core = core.substring(0, cut);
        }
        if (core.startsWith("v") || core.startsWith("V")) {
            core = core.substring(1);
        }
        String[] parts = core.split("\\.", -1);
        if (parts.length == 0 || parts.length > 3) {
            throw new IllegalArgumentException("Not a semantic version: " + text);
        }
        int[] numbers = new int[3];
        for (int i = 0; i < parts.length; i++) {
            if (parts[i].isEmpty() || !parts[i].chars().allMatch(Character::isDigit)) {
                throw new IllegalArgumentException("Not a semantic version: " + text);
            }
            numbers[i] = Integer.parseInt(parts[i]);
        }
        return new SemanticVersion(numbers[0], numbers[1], numbers[2]);
    }

    public boolean isOlderThan(SemanticVersion other) {
        return compareTo(other) < 0;
    }

    @Override
    public int compareTo(SemanticVersion other) {
        if (major != other.major) {
            return Integer.compare(major, other.major);
        }
        if (minor != other.minor) {
            return Integer.compare(minor, other.minor);
        }
        return Integer.compare(patch, other.patch);
    }

    @Override
    public String toString() {
        return major + "." + minor + "." + patch;
    }

    private static int indexOfAny(String s, char... chars) {
        return Arrays.stream(new int[]{s.indexOf(chars[0]), s.indexOf(chars[1])})
                .filter(i -> i >= 0)
                .min()
                .orElse(-1);
    }
}
