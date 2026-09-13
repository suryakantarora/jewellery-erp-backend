package com.finotech.jewellery.shared.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SemanticVersionTest {

    @Test
    @DisplayName("compares numerically, not lexically")
    void numericComparison() {
        assertThat(SemanticVersion.parse("1.10.0").isOlderThan(SemanticVersion.parse("1.9.0")))
                .isFalse();
        assertThat(SemanticVersion.parse("1.2.3").isOlderThan(SemanticVersion.parse("1.2.4")))
                .isTrue();
        assertThat(SemanticVersion.parse("0.9.9").isOlderThan(SemanticVersion.parse("1.0.0")))
                .isTrue();
        assertThat(SemanticVersion.parse("2.0.0").isOlderThan(SemanticVersion.parse("1.99.99")))
                .isFalse();
    }

    @Test
    @DisplayName("ignores a +build or -pre suffix and a leading v")
    void toleratesSuffixes() {
        assertThat(SemanticVersion.parse("1.2.3+45")).isEqualTo(new SemanticVersion(1, 2, 3));
        assertThat(SemanticVersion.parse("1.2.3-beta")).isEqualTo(new SemanticVersion(1, 2, 3));
        assertThat(SemanticVersion.parse("v1.2.3")).isEqualTo(new SemanticVersion(1, 2, 3));
        assertThat(SemanticVersion.parse(" 1.2.3 ")).isEqualTo(new SemanticVersion(1, 2, 3));
    }

    @Test
    @DisplayName("a missing minor or patch reads as zero")
    void shortForms() {
        assertThat(SemanticVersion.parse("2")).isEqualTo(new SemanticVersion(2, 0, 0));
        assertThat(SemanticVersion.parse("2.1")).isEqualTo(new SemanticVersion(2, 1, 0));
    }

    @Test
    @DisplayName("rejects text that is not a version")
    void rejectsGarbage() {
        assertThatThrownBy(() -> SemanticVersion.parse("latest"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SemanticVersion.parse("1.2.3.4"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SemanticVersion.parse(""))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SemanticVersion.parse("1..2"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
