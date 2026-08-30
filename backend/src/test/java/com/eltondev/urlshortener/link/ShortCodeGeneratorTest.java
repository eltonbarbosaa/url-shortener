package com.eltondev.urlshortener.link;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShortCodeGeneratorTest {

    @Test
    void generatesSevenCharacterAlphanumericCode() {
        ShortCodeGenerator generator = new ShortCodeGenerator();

        String code = generator.generate();

        assertEquals(7, code.length());
        assertTrue(code.matches("[a-zA-Z0-9]+"), "code should be alphanumeric, was: " + code);
    }

    @Test
    void generatesDifferentCodesAcrossCalls() {
        ShortCodeGenerator generator = new ShortCodeGenerator();

        String first = generator.generate();
        String second = generator.generate();

        assertTrue(!first.equals(second), "two consecutive codes should not collide in practice");
    }
}
