package com.zszl.zszlScriptMod.system;

import org.junit.Assume;
import org.junit.Test;
import java.lang.reflect.Method;
import java.nio.file.*;
import java.util.*;
import static org.junit.Assert.*;

/** Opt-in, read-only benchmark against the player's existing profile. */
public class ProfileShareBenchmarkTest {
    @Test public void realConfigurationsRoundTrip() throws Exception {
        String directory = System.getenv("ZSZL_SHARE_FIXTURES");
        Assume.assumeTrue(directory != null && Files.isDirectory(Paths.get(directory)));
        List<String> files = ProfileShareCodeManager.listShareableFiles(directory);
        for (String file : Arrays.asList("custom_paths.json", "autofollow_rules.json", "keycommand_killaura.json")) {
            if (files.contains(file)) measure(directory, Collections.singletonList(file), file);
        }
        measure(directory, files, "all-shareable");
    }

    private void measure(String directory, List<String> files, String label) throws Exception {
        long start = System.nanoTime();
        String dense = ProfileShareCodeManager.generateShareCode(directory, files);
        byte[] payload = ProfileShareCompression.decode(dense);
        Method legacy = ProfileShareCodeManager.class.getDeclaredMethod("deflate", byte[].class);
        legacy.setAccessible(true);
        byte[] oldBytes = (byte[]) legacy.invoke(null, (Object) payload);
        int oldLength = 4 + Base64.getUrlEncoder().withoutPadding().encodeToString(oldBytes).length();
        String ascii = ProfileShareCompression.encode(payload, true);
        assertArrayEquals(payload, ProfileShareCompression.decode(ascii));
        assertTrue(dense.length() < oldLength);
        byte[] envelope = ProfileShareTextEncoding.undense(dense.substring(4));
        System.out.println(label + ": oldChars=" + oldLength + " denseChars=" + dense.length()
                + " asciiChars=" + ascii.length() + " oldCompressedBytes=" + oldBytes.length
                + " newEnvelopeBytes=" + envelope.length + " codec=" + envelope[0]
                + " totalMs=" + (System.nanoTime() - start) / 1000000);
    }
}
