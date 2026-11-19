package com.referralportal.common;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ChipListsTest {

    @Test
    void acceptsValidRoles() {
        List<String> result = ChipLists.clean(List.of("Backend", "Full Stack", "ML / AI"), 6, "roles");
        assertEquals(List.of("Backend", "Full Stack", "ML / AI"), result);
    }

    @Test
    void acceptsParenthesesInRoles() {
        List<String> result = ChipLists.clean(List.of("AI Engineer (RAG / Agents)"), 6, "roles");
        assertEquals(1, result.size());
        assertEquals("AI Engineer (RAG / Agents)", result.get(0));
    }

    @Test
    void acceptsUpToMaxRoles() {
        List<String> six = List.of("A", "Ba", "Cc", "De", "Ef", "Fg");
        assertDoesNotThrow(() -> ChipLists.clean(six, 6, "roles"));
    }

    @Test
    void rejectsMoreThanMaxRoles() {
        List<String> seven = List.of("Aa", "Bb", "Cc", "Dd", "Ee", "Ff", "Gg");
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> ChipLists.clean(seven, 6, "roles"));
        assertTrue(ex.getMessage().contains("6"));
    }

    @Test
    void rejectsInvalidCharacters() {
        assertThrows(ResponseStatusException.class,
                () -> ChipLists.clean(List.of("<script>alert(1)</script>"), 6, "roles"));
    }

    @Test
    void rejectsAtSign() {
        assertThrows(ResponseStatusException.class,
                () -> ChipLists.clean(List.of("admin@company"), 6, "roles"));
    }

    @Test
    void acceptsLegacyCompatibleValue_StaffEngineer() {
        // "Staff Engineer" is a valid chip value (renamed from "Staff Software")
        assertDoesNotThrow(() -> ChipLists.clean(List.of("Staff Engineer"), 6, "roles"));
    }

    @Test
    void acceptsLegacyCompatibleValue_SdetQa() {
        assertDoesNotThrow(() -> ChipLists.clean(List.of("SDET / QA"), 6, "roles"));
    }

    @Test
    void acceptsValueUpTo40Chars() {
        // 40-char string of letters
        String fortyChars = "Solutions and Cloud Architecture Design";  // 39 chars
        assertDoesNotThrow(() -> ChipLists.clean(List.of(fortyChars), 6, "roles"));
    }

    @Test
    void skipsNullAndEmptyEntries() {
        List<String> result = ChipLists.clean(List.of("Backend", "", "Full Stack"), 6, "roles");
        assertEquals(List.of("Backend", "Full Stack"), result);
    }

    @Test
    void deduplicatesEntries() {
        List<String> result = ChipLists.clean(List.of("Backend", "Backend", "Full Stack"), 6, "roles");
        assertEquals(List.of("Backend", "Full Stack"), result);
    }

    @Test
    void returnsEmptyListForNull() {
        List<String> result = ChipLists.clean(null, 6, "roles");
        assertTrue(result.isEmpty());
    }

    @Test
    void acceptsSkillsWithHigherMax() {
        List<String> twelve = List.of(
            "React", "TypeScript", "JavaScript", "Node.js", "Java",
            "Spring Boot", "Python", "SQL", "Kotlin", "Flutter", "Go", "GCP"
        );
        assertDoesNotThrow(() -> ChipLists.clean(twelve, 12, "skills"));
    }

    @Test
    void rejectsThirteenSkills() {
        List<String> thirteen = List.of(
            "React", "TypeScript", "JavaScript", "Node.js", "Java",
            "Spring Boot", "Python", "SQL", "Kotlin", "Flutter", "Go", "GCP", "AWS"
        );
        assertThrows(ResponseStatusException.class,
                () -> ChipLists.clean(thirteen, 12, "skills"));
    }
}
