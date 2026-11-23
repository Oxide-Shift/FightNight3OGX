package org.oxideshift.corner;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class MenuStripSmoke {
    private static LiveFrame frame(int width, int height) {
        ByteBuffer pixels = ByteBuffer.allocateDirect(8 + 4 * width * height)
                .order(ByteOrder.LITTLE_ENDIAN);
        return new LiveFrame(pixels, width, height, 1, true);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        for (int height : new int[] {480, 720, 1080}) {
            int width = height * 4 / 3;
            LiveFrame original = frame(width, height);
            int first = 8 + (height * 380 / 480) * width * 4;
            original.pixels.put(first, (byte) 42);
            MenuStrip saved = new MenuStrip(original);
            require(saved.matches(original), "Same menu must match");
            require(saved.pixels.remaining() == saved.rows * width * 4, "Strip size");
            original.pixels.put(first, (byte) 7);
            require(saved.pixels.get(0) == 42, "Frame reuse changed cached pixels");

            for (int i = 0; i < 4; i++) {
                int x = original.sourceX(75 + i * 123) * width / 640;
                int y = 82 * height / 480;
                original.pixels.putInt(8 + 4 * (y * width + x), 0xffffffff);
                require(saved.matches(original) == (i < 3), "Scene change, anchor " + i);
            }
            require(!saved.matches(frame(width / 2, height / 2)), "Different resolution");
            require(!saved.matches(null), "Missing frame");
        }
    }
}
