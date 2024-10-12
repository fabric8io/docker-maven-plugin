package io.fabric8.maven.docker.access.util;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ComposeDurationUtilTest {

    @Test
    void testCombinationsAreNotSupported() {
        Assertions.assertThrows(IllegalArgumentException.class, ()-> ComposeDurationUtil.goDurationToNanoseconds("2h45m", "someField"));
    }
    @Test
    void testUnknownTimeUnit() {
        Assertions.assertThrows(IllegalArgumentException.class, ()-> ComposeDurationUtil.goDurationToNanoseconds("2x", "someField"));
    }
    @Test
    void testDurationNanosecondsCorrect() {
        assertEquals(0, ComposeDurationUtil.goDurationToNanoseconds("0", "someField"));
        assertEquals(1, ComposeDurationUtil.goDurationToNanoseconds("1", "someField"));
        assertEquals(1000000, ComposeDurationUtil.goDurationToNanoseconds("1ms", "someField"));
        assertEquals(3600000000000L, ComposeDurationUtil.goDurationToNanoseconds("1h", "someField"));
        assertEquals(2000000000, ComposeDurationUtil.goDurationToNanoseconds("2s", "someField"));
        assertEquals(259200000000000L, ComposeDurationUtil.goDurationToNanoseconds("3d", "someField"));
        assertEquals(604800000000000L, ComposeDurationUtil.goDurationToNanoseconds("1w", "someField"));
        assertEquals(31536000000000000L, ComposeDurationUtil.goDurationToNanoseconds("1y", "someField"));
    }
}