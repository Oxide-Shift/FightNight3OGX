package org.oxideshift.corner;

import java.io.*;
import java.nio.*;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;

/** Reads a seekable original Xbox disc supplied by the player. */
public final class DiscImage {
    public static final String HASH = "cfaf2d2c1c338a6e4e7135466c8dcb80fdf7da63682e71cfecdcba65b3682064";
    private final FileChannel channel;
    private final long length, origin;
    private long base, total;
    private final Map<String, long[]> files = new LinkedHashMap<>();
    private final Set<Long> directories = new HashSet<>();
    private final Set<String> names = new HashSet<>();
    private final byte[] buffer = new byte[262144];
    public interface Progress { void update(int files, int count); }

    private byte[] read(long offset, int size) throws IOException {
        byte[] bytes = new byte[size]; readInto(offset, bytes, size); return bytes;
    }

    private void readInto(long offset, byte[] bytes, int size) throws IOException {
        if (offset < 0 || offset > length - size) throw new EOFException("Disc data is truncated");
        ByteBuffer dst = ByteBuffer.wrap(bytes, 0, size);
        while (dst.hasRemaining()) {
            int n = channel.read(dst, origin + offset + dst.position());
            if (n <= 0) throw new EOFException("Cannot read disc image");
        }
    }

    public DiscImage(FileChannel channel) throws Exception {
        this(channel, 0, channel.size());
    }
    public DiscImage(FileChannel channel, long origin, long length) throws Exception {
        if(origin < 0 || length < 0 || origin > channel.size()-length)throw new IOException("Invalid disc range");
        this.channel=channel; this.origin=origin; this.length=length;
        byte[] magic = "MICROSOFT*XBOX*MEDIA".getBytes(StandardCharsets.US_ASCII);
        for (long candidate : new long[]{0, 0xFD90, 0x30600, 0xFD90000, 0x18300000}) {
            long at = candidate + 65536;
            if (at + 28 > length || !Arrays.equals(magic, read(at, 20))) continue;
            base = candidate;
            ByteBuffer root = ByteBuffer.wrap(read(at + 20, 8)).order(ByteOrder.LITTLE_ENDIAN);
            directory("", root.getInt() & 0xffffffffL, root.getInt() & 0xffffffffL, 0);
            String executable = null;
            for (String name : files.keySet()) if (name.equalsIgnoreCase("default.xbe")) executable = name;
            if (executable == null || files.get(executable)[1] != 5750784) throw new IOException("Unsupported Xbox executable");
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            copy(files.get(executable), null, digest);
            StringBuilder hex = new StringBuilder();
            for (byte b : digest.digest()) hex.append(String.format(Locale.ROOT, "%02x", b & 255));
            if (!HASH.contentEquals(hex)) throw new IOException("Unsupported Fight Night disc revision");
            if (!executable.equals("default.xbe")) files.put("default.xbe", files.remove(executable));
            return;
        }
        throw new IOException("Choose a seekable original Xbox ISO");
    }

    private void directory(String prefix, long sector, long size, int depth) throws IOException {
        if (depth > 32 || size > (8L << 20) || !directories.add(sector)) throw new IOException("Invalid disc directory");
        long offset = base + sector * 2048;
        byte[] data = read(offset, (int)size);
        ByteBuffer buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        for (int page = 0; page < size; page += 2048) {
            int end = Math.min(data.length, page + 2048);
            for (int pos = page; pos + 14 <= end;) {
                int chars = data[pos + 13] & 255;
                if (buf.getInt(pos) == -1 || chars == 0) break;
                if (pos + 14 + chars > end) throw new IOException("Truncated directory entry");
                String leaf = new String(data, pos + 14, chars, StandardCharsets.US_ASCII);
                if (leaf.equals(".") || leaf.equals("..") || !leaf.matches("[ -~]+") ||
                    leaf.matches(".*[\\\\/:*?\"<>|].*") || leaf.endsWith(".") || leaf.endsWith(" "))
                    throw new IOException("Invalid filename on disc");
                String path = prefix + leaf;
                if (names.size() >= 25000 || !names.add(path.toLowerCase(Locale.ROOT))) throw new IOException("Invalid disc tree");
                long child = buf.getInt(pos + 4) & 0xffffffffL, bytes = buf.getInt(pos + 8) & 0xffffffffL;
                if ((data[pos + 12] & 16) != 0) directory(path + "/", child, bytes, depth + 1);
                else {
                    long start = base + child * 2048;
                    if (start > length - bytes || (total += bytes) > (8L << 30)) throw new IOException("Invalid file extent");
                    files.put(path, new long[]{start, bytes});
                }
                pos += (14 + chars + 3) & ~3;
            }
        }
    }

    private void copy(long[] entry, OutputStream out, MessageDigest digest) throws IOException {
        for (long pos = 0; pos < entry[1];) {
            int n = (int)Math.min(buffer.length, entry[1] - pos);
            readInto(entry[0] + pos, buffer, n);
            if (out != null) out.write(buffer, 0, n);
            if (digest != null) digest.update(buffer, 0, n);
            pos += n;
        }
    }
    public int count() { return files.size(); }
    public long bytes() { return total; }

    /** The caller owns a fresh staging folder and publishes it only after success. */
    public void extract(Path stage, Progress progress) throws IOException {
        extract(stage,progress,()->{});
    }
    public void extract(Path stage, Progress progress, Runnable executableReady) throws IOException {
        if (!Files.isDirectory(stage) || stage.toFile().getUsableSpace() < total + (16L << 20))
            throw new IOException("Insufficient space for the disc");
        try(OutputStream out=Files.newOutputStream(stage.resolve("default.xbe"),StandardOpenOption.CREATE_NEW)){
            copy(files.get("default.xbe"),out,null);
        }
        executableReady.run();
        int done = 1;
        // Translation is already running. Read the remaining assets in physical disc order.
        List<Map.Entry<String,long[]>> order=new ArrayList<>(files.entrySet());
        order.sort(Comparator.comparingLong(entry->entry.getValue()[0]));
        for (Map.Entry<String,long[]> file : order) {
            if(file.getKey().equals("default.xbe"))continue;
            Path path = stage.resolve(file.getKey()).normalize();
            if (!path.startsWith(stage)) throw new IOException("Invalid disc path");
            Files.createDirectories(path.getParent());
            try (OutputStream out = Files.newOutputStream(path, StandardOpenOption.CREATE_NEW)) { copy(file.getValue(), out, null); }
            progress.update(++done, files.size());
        }
    }
}
