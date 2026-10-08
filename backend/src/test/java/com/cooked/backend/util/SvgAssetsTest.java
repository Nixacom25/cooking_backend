package com.cooked.backend.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SvgAssetsTest {

    static final String OK = "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 64 64\">"
            + "<circle cx=\"32\" cy=\"32\" r=\"20\" fill=\"#E2553B\"/><path d=\"M1 1\" fill=\"#E2553B\" stroke=\"#2A1B16\"/></svg>";

    @Test
    void acceptsAClean64x64Svg() {
        assertTrue(SvgAssets.problems("<?xml version=\"1.0\"?>\n<!-- art -->\n" + OK).isEmpty());
        assertEquals(OK, SvgAssets.clean("<?xml version=\"1.0\"?>\n" + OK + "\n"));
        assertEquals(8, SvgAssets.hash(OK).length());
    }

    @Test
    void rejectsUnsafeOrWrongArt() {
        assertFalse(SvgAssets.problems("<png/>").isEmpty());
        assertFalse(SvgAssets.problems(OK.replace("0 0 64 64", "0 0 24 24")).isEmpty());
        assertFalse(SvgAssets.problems(OK.replace("<circle", "<script>alert(1)</script><circle")).isEmpty());
        assertFalse(SvgAssets.problems(OK.replace("<circle", "<circle onclick=\"x()\"")).isEmpty());
        assertFalse(SvgAssets.problems(OK.replace("<circle", "<image href=\"https://x/y.png\"/><circle")).isEmpty());
        assertFalse(SvgAssets.problems(OK.replace("<circle", "<filter id=\"f\"/><circle")).isEmpty());
        String big = OK.replace("</svg>", "<path d=\"" + "M1 1 ".repeat(3000) + "\"/></svg>");
        assertTrue(SvgAssets.problems(big).stream().anyMatch(p -> p.contains("12 KB")));
    }

    @Test
    void recolourMovesTheWholeColourFamily() {
        String pepper = "<svg viewBox=\"0 0 64 64\"><radialGradient id=\"g\"><stop stop-color=\"#F1B0AE\"/><stop stop-color=\"#DE4D48\"/>"
                + "<stop stop-color=\"#D9302A\"/><stop stop-color=\"#80221D\"/></radialGradient><path fill=\"#5E3B2C\" d=\"M1 1\"/>"
                + "<path fill=\"#4C8D56\" d=\"M1 1\"/><path stroke=\"#2A1410\" d=\"M1 1\"/></svg>";
        assertEquals("#D9302A", SvgAssets.mainColor(pepper).orElseThrow());
        String purple = SvgAssets.recolor(pepper, "#7A4A9A");
        assertTrue(purple.contains("#7A4A9A"));
        for (String red : new String[]{"#F1B0AE", "#DE4D48", "#D9302A", "#80221D"}) assertFalse(purple.contains(red), red);
        assertTrue(purple.contains("#4C8D56") && purple.contains("#2A1410") && purple.contains("#5E3B2C"));
    }

    @Test
    void recolourReplacesTheMainColourOnly() {
        assertEquals("#E2553B", SvgAssets.mainColor(OK).orElseThrow());
        String purple = SvgAssets.recolor(OK, "#7b4fa0");
        assertFalse(purple.contains("#E2553B"));
        assertEquals(2, purple.split("#7B4FA0", -1).length - 1);
        assertTrue(purple.contains("#2A1B16"));
    }
}
