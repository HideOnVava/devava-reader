package com.devavaxp.reader.pdf;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PageSpreadsTest {

    @Test
    void singleLayoutIsOnePagePerSpread() {
        List<int[]> spreads = PageSpreads.compute(4, false);
        assertEquals(4, spreads.size());
        for (int i = 0; i < 4; i++) assertArrayEquals(new int[]{i}, spreads.get(i));
        assertEquals(2, PageSpreads.spreadOf(spreads, 2));
    }

    @Test
    void doubleLayoutShowsTheCoverAloneAndPairsTheRest() {
        List<int[]> spreads = PageSpreads.compute(6, true);
        assertEquals(4, spreads.size());
        assertArrayEquals(new int[]{0}, spreads.get(0));
        assertArrayEquals(new int[]{1, 2}, spreads.get(1));
        assertArrayEquals(new int[]{3, 4}, spreads.get(2));
        assertArrayEquals(new int[]{5}, spreads.get(3), "an odd last page stands alone");

        assertEquals(1, PageSpreads.spreadOf(spreads, 2));
        assertEquals(2, PageSpreads.spreadOf(spreads, 3));
        assertEquals(3, PageSpreads.firstPage(spreads, 2));
        assertEquals(4, PageSpreads.lastPage(spreads, 2));
        assertEquals(5, PageSpreads.lastPage(spreads, 3));
    }

    @Test
    void doubleLayoutWithOddCountEndsWithAFullPair() {
        List<int[]> spreads = PageSpreads.compute(5, true);
        assertEquals(3, spreads.size());
        assertArrayEquals(new int[]{3, 4}, spreads.get(2));
    }

    @Test
    void widePagesStandAloneAndPairingCarriesOn() {
        // pages 3 and 6 are landscape scans of two printed pages
        List<int[]> spreads = PageSpreads.compute(9, true, p -> p == 3 || p == 6);
        assertEquals(6, spreads.size());
        assertArrayEquals(new int[]{0}, spreads.get(0));
        assertArrayEquals(new int[]{1, 2}, spreads.get(1));
        assertArrayEquals(new int[]{3}, spreads.get(2), "a wide page is never paired");
        assertArrayEquals(new int[]{4, 5}, spreads.get(3));
        assertArrayEquals(new int[]{6}, spreads.get(4));
        assertArrayEquals(new int[]{7, 8}, spreads.get(5));

        List<int[]> single = PageSpreads.compute(9, false, p -> true);
        assertEquals(9, single.size(), "single layout ignores page shapes");
        assertEquals(9, PageSpreads.compute(9, true, p -> true).size(), "all wide: one page per spread");
    }

    @Test
    void degenerateCases() {
        assertTrue(PageSpreads.compute(0, true).isEmpty());
        assertEquals(1, PageSpreads.compute(1, true).size());
        List<int[]> two = PageSpreads.compute(2, true);
        assertEquals(2, two.size(), "cover alone, then the second page alone");
        assertEquals(0, PageSpreads.spreadOf(two, 99), "unknown pages fall back to the first spread");
        assertEquals(0, PageSpreads.firstPage(List.of(), 3));
    }
}
