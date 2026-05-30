package io.github.projectunified.faststats.errortracker;

import java.nio.charset.StandardCharsets;

/**
 * Implementation of the MurmurHash3 128-bit hash algorithm.
 */
final class MurmurHash3 {

    public static String hash(final String data) {
        final long[] hash = hashBytes(data.getBytes(StandardCharsets.UTF_8));
        return Long.toHexString(hash[0]) + Long.toHexString(hash[1]);
    }

    private static long[] hashBytes(final byte[] bytes) {
        long h1 = 0L;
        long h2 = 0L;
        final long c1 = 0x87c37b91114253d5L;
        final long c2 = 0x4cf5ad432745937fL;
        final int length = bytes.length;
        final int blocks = length / 16;

        // Process 128-bit blocks
        for (int i = 0; i < blocks; i++) {
            long k1 = getInt(bytes, i * 16);
            long k2 = getInt(bytes, i * 16 + 4);
            long k3 = getInt(bytes, i * 16 + 8);
            long k4 = getInt(bytes, i * 16 + 12);

            k1 *= (int) c1;
            k1 = Integer.rotateLeft((int) k1, 31);
            k1 *= (int) c2;
            h1 ^= k1;

            h1 = Long.rotateLeft(h1, 27);
            h1 += h2;
            h1 = h1 * 5 + 0x52dce729;

            k2 *= (int) c2;
            k2 = Integer.rotateLeft((int) k2, 33);
            k2 *= (int) c1;
            h2 ^= k2;

            h2 = Long.rotateLeft(h2, 31);
            h2 += h1;
            h2 = h2 * 5 + 0x38495ab5;
        }

        // Tail
        long k1 = 0;
        long k2 = 0;
        long k3 = 0;
        long k4 = 0;
        final int tail = blocks * 16;

        switch (length & 15) {
            case 15:
                k4 ^= (long) (bytes[tail + 14] & 0xff) << 16;
            case 14:
                k4 ^= (long) (bytes[tail + 13] & 0xff) << 8;
            case 13:
                k4 ^= (long) (bytes[tail + 12] & 0xff);
                k4 *= (int) c2;
                k4 = Integer.rotateLeft((int) k4, 33);
                k4 *= (int) c1;
                h2 ^= k4;
            case 12:
                k3 ^= (long) (bytes[tail + 11] & 0xff) << 24;
            case 11:
                k3 ^= (long) (bytes[tail + 10] & 0xff) << 16;
            case 10:
                k3 ^= (long) (bytes[tail + 9] & 0xff) << 8;
            case 9:
                k3 ^= (long) (bytes[tail + 8] & 0xff);
                k3 *= (int) c1;
                k3 = Integer.rotateLeft((int) k3, 31);
                k3 *= (int) c2;
                h1 ^= k3;
            case 8:
                k2 ^= (long) (bytes[tail + 7] & 0xff) << 24;
            case 7:
                k2 ^= (long) (bytes[tail + 6] & 0xff) << 16;
            case 6:
                k2 ^= (long) (bytes[tail + 5] & 0xff) << 8;
            case 5:
                k2 ^= (long) (bytes[tail + 4] & 0xff);
                k2 *= (int) c2;
                k2 = Integer.rotateLeft((int) k2, 33);
                k2 *= (int) c1;
                h2 ^= k2;
            case 4:
                k1 ^= (long) (bytes[tail + 3] & 0xff) << 24;
            case 3:
                k1 ^= (long) (bytes[tail + 2] & 0xff) << 16;
            case 2:
                k1 ^= (long) (bytes[tail + 1] & 0xff) << 8;
            case 1:
                k1 ^= (long) (bytes[tail] & 0xff);
                k1 *= (int) c1;
                k1 = Integer.rotateLeft((int) k1, 31);
                k1 *= (int) c2;
                h1 ^= k1;
        }

        // Finalization
        h1 ^= length;
        h2 ^= length;

        h1 += h2;
        h2 += h1;

        h1 = fmix64(h1);
        h2 = fmix64(h2);

        h1 += h2;
        h2 += h1;

        return new long[]{h1, h2};
    }

    private static long fmix64(long k) {
        k ^= k >>> 33;
        k *= 0xff51afd7ed558ccdL;
        k ^= k >>> 33;
        k *= 0xc4ceb9fe1a85ec53L;
        k ^= k >>> 33;
        return k;
    }

    private static int getInt(final byte[] bytes, final int offset) {
        return (bytes[offset] & 0xff) |
                ((bytes[offset + 1] & 0xff) << 8) |
                ((bytes[offset + 2] & 0xff) << 16) |
                ((bytes[offset + 3] & 0xff) << 24);
    }
}
