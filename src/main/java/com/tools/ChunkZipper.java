package com.tools;
    
import java.io.ByteArrayOutputStream;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

public class ChunkZipper {

    public static byte[] Compress(int[][][] chunk) {
        try {
            // 1. Flatten the 3D int array into a flat 1D byte buffer
            // 16 * 16 * 16 = 4096 blocks. Each int is 4 bytes. Total = 16,384 bytes.
            byte[] rawBytes = new byte[16 * 16 * 16 * 4];
            int index = 0;

            for (int x = 0; x < 16; x++) {
                for (int y = 0; y < 16; y++) {
                    for (int z = 0; z < 16; z++) {
                        int value = chunk[x][y][z];
                        // Split the 32-bit int into 4 distinct bytes
                        rawBytes[index++] = (byte) (value >> 24);
                        rawBytes[index++] = (byte) (value >> 16);
                        rawBytes[index++] = (byte) (value >> 8);
                        rawBytes[index++] = (byte) value;
                    }
                }
            }

            // 2. Compress the flattened byte array using Java's built-in Deflater
            Deflater deflater = new Deflater();
            deflater.setInput(rawBytes);
            deflater.finish();

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream(rawBytes.length);
            byte[] buffer = new byte[1024];
            while (!deflater.finished()) {
                int count = deflater.deflate(buffer);
                outputStream.write(buffer, 0, count);
            }
            outputStream.close();
            deflater.end();

            return outputStream.toByteArray(); // This is what you send over TCP
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static int[][][] Decompress(byte[] compressedBytes) {
        try {
            // 1. Inflate back to the raw uncompressed 16,384 bytes
            Inflater inflater = new Inflater();
            inflater.setInput(compressedBytes);

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream(compressedBytes.length);
            byte[] buffer = new byte[1024];
            while (!inflater.finished()) {
                int count = inflater.inflate(buffer);
                outputStream.write(buffer, 0, count);
            }
            outputStream.close();
            inflater.end();

            byte[] rawBytes = outputStream.toByteArray();

            // 2. Reconstruct the 3D int array
            int[][][] chunk = new int[16][16][16];
            int index = 0;

            for (int x = 0; x < 16; x++) {
                for (int y = 0; y < 16; y++) {
                    for (int z = 0; z < 16; z++) {
                        // Reassemble the 4 bytes back into a single 32-bit int
                        int value = ((rawBytes[index++] & 0xFF) << 24) |
                                    ((rawBytes[index++] & 0xFF) << 16) |
                                    ((rawBytes[index++] & 0xFF) << 8)  |
                                    (rawBytes[index++] & 0xFF);
                        chunk[x][y][z] = value;
                    }
                }
            }
            return chunk;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
