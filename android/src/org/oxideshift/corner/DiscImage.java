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
    private static final int US_BYTES = 5750784;
    private static final int US_LAST_SECTION_END = 5748960;
    private static final String PAL_XBE_MD5 = "4f65d6d6e3d1ba717e4a9cbf8afc0a7b";
    private final FileChannel channel;
    private final long length, origin;
    private long base, total;
    private byte[] verifiedExecutable;
    final DiscFacts facts;
    private byte[] sourceDigest;
    private final Map<String, long[]> files = new LinkedHashMap<>();
    private final Set<Long> directories = new HashSet<>();
    private final Set<String> names = new HashSet<>();
    private final byte[] buffer = new byte[262144];
    public interface Progress { void update(int files, int count); }

    static void checkContainer(byte[] prefix) throws IOException {
        if (Arrays.equals(prefix, new byte[]{0x37, 0x7a, (byte)0xbc, (byte)0xaf, 0x27, 0x1c}))
            throw new DiscImport.Failure(DiscImport.Reason.ARCHIVE,"Unsupported container: this is a 7z archive. Extract it, then choose the .iso or .xiso.iso file inside.").format("7z");
        if (Arrays.equals(prefix, new byte[]{'R','a','r','!',0x1a,7}))
            throw new DiscImport.Failure(DiscImport.Reason.ARCHIVE,"Unsupported container: this is a RAR archive. Extract it, then choose the Xbox ISO inside.").format("rar");
        String header = new String(prefix, StandardCharsets.US_ASCII);
        String format=header.startsWith("MCompr")?"chd":header.startsWith("CCIM")?"cci":header.startsWith("CISO")?"cso":header.startsWith("ZISO")?"ziso":null;
        if (format!=null)
            throw new DiscImport.Failure(DiscImport.Reason.COMPRESSED,"Unsupported container: this is a "+format.toUpperCase(Locale.ROOT)+" compressed disc image. Convert it to a single Xbox ISO or XISO first.").format(format);
    }

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
        this(channel,origin,length,null,new DiscFacts(),"raw",length);
    }
    DiscImage(FileChannel channel,long origin,long length,GameLibrary.Status status,DiscFacts observed,String container,long selectedBytes)throws Exception{
        if(origin < 0 || length < 0 || origin > channel.size()-length)throw new DiscImport.Failure(DiscImport.Reason.TRUNCATED,"Invalid disc range");
        this.channel=channel; this.origin=origin; this.length=length;
        facts=observed;
        facts.put("origin","import");facts.put("image_bytes",length);facts.put("selected_bytes",selectedBytes);
        if(status!=null)status.discFacts(facts);
        if (length >= 6) checkContainer(read(0, 6));
        facts.put("container",container);
        byte[] magic = "MICROSOFT*XBOX*MEDIA".getBytes(StandardCharsets.US_ASCII);
        // 0x2080000 is the Xbox 360 XGD3 game partition: recognizing it lets a 360 disc get the wrong-platform message.
        for (long candidate : new long[]{0, 0xFD90, 0x30600, 0xFD90000, 0x18300000, 0x2080000}) {
            long at = candidate + 65536;
            if (at + 28 > length || !Arrays.equals(magic, read(at, 20))) continue;
            base = candidate;
            facts.put("layout",candidate==0?"xiso":"partitioned");facts.put("partition_offset",candidate);
            if(status!=null)status.format(candidate==0?"xiso":"iso");
            ByteBuffer root = ByteBuffer.wrap(read(at + 20, 8)).order(ByteOrder.LITTLE_ENDIAN);
            directory("", root.getInt() & 0xffffffffL, root.getInt() & 0xffffffffL, 0);
            facts.put("files",count());facts.put("payload_bytes",total);
            String executable = null;
            for (String name : files.keySet()) if (name.equalsIgnoreCase("default.xbe")) executable = name;
            if(executable==null){
                if(names.contains("default.xex"))throw new DiscImport.Failure(DiscImport.Reason.WRONG_PLATFORM,
                        "Wrong platform: this image contains the Xbox 360 executable (default.xex). Choose the original Xbox edition of Fight Night Round 3.");
                throw new DiscImport.Failure(DiscImport.Reason.MISSING_XBE,"This Xbox image has no default.xbe at its root. Choose the original Xbox game disc.");
            }
            long[] entry=files.get(executable);
            try{verifiedExecutable=verifyExecutable(entry);}
            catch(DiscImport.Failure rejected){
                String rejectedHash=sourceDigest==null?null:hex(sourceDigest);
                if(sourceDigest==null && entry[1]<=64L*1024*1024){
                    MessageDigest sha=DiscFacts.digest();copy(entry,null,sha);
                    rejectedHash=DiscFacts.hex(sha.digest());
                    facts.put("source_xbe_sha256",rejectedHash);
                }
                // Copy details prints the failure's hash: every refused executable must carry it.
                if(rejected.hash==null)rejected.hash=rejectedHash;
                throw rejected;
            }
            long[] menu=null;
            for(Map.Entry<String,long[]> file:files.entrySet())
                if(file.getKey().equalsIgnoreCase("GUI/overlay.viv")){menu=file.getValue();break;}
            checkMenuHeader(menu==null || menu[1]<16?null:read(menu[0],16),menu==null?0:menu[1]);
            if (!executable.equals("default.xbe")) files.put("default.xbe", files.remove(executable));
            return;
        }
        if(length>=32768+40){
            byte[] volume=read(32768,40);
            if(volume[0]==1 && new String(volume,1,5,StandardCharsets.US_ASCII).equals("CD001")
                    && new String(volume,8,32,StandardCharsets.US_ASCII).trim().equals("PLAYSTATION"))
                throw new DiscImport.Failure(DiscImport.Reason.WRONG_PLATFORM,"Wrong platform: this is a PlayStation disc image. Choose the original Xbox edition of Fight Night Round 3.");
        }
        throw new DiscImport.Failure(DiscImport.Reason.FILESYSTEM,"No Xbox disc filesystem found. Choose an uncompressed original Xbox ISO or XISO.");
    }

    private static String hex(byte[] digest) {
        StringBuilder text = new StringBuilder(digest.length * 2);
        for (byte b : digest) text.append(String.format(Locale.ROOT, "%02x", b & 255));
        return text.toString();
    }

    static void checkMenuHeader(byte[] header,long length)throws IOException{
        if(header!=null && header.length==16){
            ByteBuffer b=ByteBuffer.wrap(header);
            int count=b.getInt(8),indexEnd=b.getInt(12);
            long big=Integer.toUnsignedLong(b.getInt(4));
            long little=Integer.toUnsignedLong(ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN).getInt(4));
            if(b.getInt(0)==0x42494746 && count>0 && indexEnd>=16L+9L*count && indexEnd<=length
                    && ((little>=indexEnd && little<=length) || (big>=indexEnd && big<=length)))return;
        }
        throw new DiscImport.Failure(DiscImport.Reason.MENU_ASSET,
                "The game menu archive (GUI/overlay.viv) is missing or incomplete. Select a complete original Xbox disc image to install or repair the game. Your saves stay on the device.");
    }

    static void checkInstalledMenu(Path game)throws IOException{
        Path gui=GameLibrary.childIgnoreCase(game,"GUI");
        if(gui!=null)try(DirectoryStream<Path> entries=Files.newDirectoryStream(gui)){
            for(Path path:entries)if(path.getFileName().toString().equalsIgnoreCase("overlay.viv") && Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS)){
                long length=Files.size(path);
                byte[] header=new byte[16];
                if(length>=16)try(DataInputStream in=new DataInputStream(Files.newInputStream(path))){in.readFully(header);}
                checkMenuHeader(header,length);
                return;
            }
        }
        checkMenuHeader(null,0);
    }

    private byte[] verifyExecutable(long[] entry) throws IOException, NoSuchAlgorithmException {
        long offset = entry[0], size = entry[1];
        facts.put("source_xbe_bytes",size);
        if (size < 0x11c) throw new DiscImport.Failure(DiscImport.Reason.INVALID_XBE,"The disc's default.xbe is truncated.");
        ByteBuffer header = ByteBuffer.wrap(read(offset, 0x11c)).order(ByteOrder.LITTLE_ENDIAN);
        if (header.getInt(0) != 0x48454258) throw new DiscImport.Failure(DiscImport.Reason.INVALID_XBE,"The disc's default.xbe is not an original Xbox executable.");
        long baseAddress = Integer.toUnsignedLong(header.getInt(0x104));
        long headerSize = Integer.toUnsignedLong(header.getInt(0x108));
        long cert = Integer.toUnsignedLong(header.getInt(0x118)) - baseAddress;
        if (cert < 0x11c || headerSize > size || cert > headerSize - 0xb0)
            throw new DiscImport.Failure(DiscImport.Reason.INVALID_XBE,"The Xbox executable has an invalid certificate offset.");
        ByteBuffer certificate = ByteBuffer.wrap(read(offset + cert, 0xb0)).order(ByteOrder.LITTLE_ENDIAN);
        long certSize = Integer.toUnsignedLong(certificate.getInt(0));
        if (certSize < 0xb0 || certSize > headerSize - cert)
            throw new DiscImport.Failure(DiscImport.Reason.INVALID_XBE,"The Xbox executable has an invalid certificate size.");
        int title = certificate.getInt(8);
        facts.certificate(certificate);
        if (title != 0x45410086)
            throw new DiscImport.Failure(DiscImport.Reason.WRONG_TITLE,String.format(Locale.ROOT, "Different game: this is Xbox title %08X. Choose original Xbox Fight Night Round 3 (45410086).", title))
                    .xbe(title,certificate.getInt(0xa0),certificate.getInt(0xac),null);

        MessageDigest sha = MessageDigest.getInstance("SHA-256");
        String originalHash;
        if (size >= US_LAST_SECTION_END && size <= US_BYTES) {
            // The retail file ends with 1,824 zero bytes after its final section.
            byte[] image = new byte[US_BYTES];
            readInto(offset, image, (int)size);
            sha.update(image, 0, (int)size);
            sourceDigest=sha.digest();originalHash=hex(sourceDigest);
            facts.put("source_xbe_sha256",originalHash);
            if (HASH.equals(originalHash)) return selected(image,"exact");
            if (size < US_BYTES && HASH.equals(hex(sha.digest(image)))) return selected(image,"aligned");
            // Xbox backup tools may rename the dashboard title or edit media/region flags.
            // Only hand the result to the compiler if the entire retail image matches.
            ByteBuffer restored = ByteBuffer.wrap(image).order(ByteOrder.LITTLE_ENDIAN);
            Arrays.fill(image, (int)cert + 0x0c, (int)cert + 0x5c, (byte)0);
            restored.position((int)cert + 0x0c);
            restored.put("EA SPORTS FN 3".getBytes(StandardCharsets.UTF_16LE));
            restored.putInt((int)cert + 0x9c, 0x202);
            restored.putInt((int)cert + 0xa0, 1);
            if (HASH.equals(hex(sha.digest(image)))) return selected(image,"certificate_restored");
        } else {
            copy(entry, null, sha);
            sourceDigest=sha.digest();originalHash=hex(sourceDigest);facts.put("source_xbe_sha256",originalHash);
        }

        int region = certificate.getInt(0xa0);
        if ((region & 1) != 0 && certificate.getInt(0xac) == 2) {
            // DVD2Xbox can keep the original next to its edited default.xbe.
            for (Map.Entry<String,long[]> file : files.entrySet()) {
                String name = file.getKey();
                if (!(name.equalsIgnoreCase("default.xbe_orig") || name.equalsIgnoreCase("default.xbe.bak"))) continue;
                long[] backup = file.getValue();
                if (backup[1] != US_BYTES) continue;
                byte[] original = read(backup[0], US_BYTES);
                if (HASH.equals(hex(sha.digest(original)))) return selected(original,"backup");
            }
        }
        String edition = region == 1 ? "USA/Canada" : String.format(Locale.ROOT, "region mask 0x%08X", region);
        if (region == 4) {
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            copy(entry, null, md5);
            // Catalog MD5 identifies the pressing for display only.
            edition = PAL_XBE_MD5.equals(hex(md5.digest())) ? "Europe/PAL, EA13401E" : "PAL";
        }
        throw new DiscImport.Failure(DiscImport.Reason.REVISION,"Unrecognized disc revision: Fight Night Round 3 (" + edition + "), Xbox title 45410086, XBE version "
                + Integer.toUnsignedLong(certificate.getInt(0xac)) + ". This executable has no supported game build yet."
                + "\n\ndefault.xbe SHA-256\n" + originalHash).xbe(title,region,certificate.getInt(0xac),originalHash);
    }

    private byte[] selected(byte[] image,String how){facts.put("xbe_selection",how);return image;}

    private void directory(String prefix, long sector, long size, int depth) throws IOException {
        if (depth > 32 || size > (8L << 20) || !directories.add(sector)) throw new DiscImport.Failure(DiscImport.Reason.INVALID_TREE,"Invalid disc directory");
        long offset = base + sector * 2048;
        byte[] data = read(offset, (int)size);
        ByteBuffer buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        for (int page = 0; page < size; page += 2048) {
            int end = Math.min(data.length, page + 2048);
            for (int pos = page; pos + 14 <= end;) {
                int chars = data[pos + 13] & 255;
                if (buf.getInt(pos) == -1 || chars == 0) break;
                if (pos + 14 + chars > end) throw new DiscImport.Failure(DiscImport.Reason.TRUNCATED,"Truncated directory entry");
                String leaf = new String(data, pos + 14, chars, StandardCharsets.US_ASCII);
                if (leaf.equals(".") || leaf.equals("..") || !leaf.matches("[ -~]+") ||
                    leaf.matches(".*[\\\\/:*?\"<>|].*") || leaf.endsWith(".") || leaf.endsWith(" "))
                    throw new DiscImport.Failure(DiscImport.Reason.INVALID_TREE,"Invalid filename on disc");
                String path = prefix + leaf;
                if (names.size() >= 25000 || !names.add(path.toLowerCase(Locale.ROOT))) throw new DiscImport.Failure(DiscImport.Reason.INVALID_TREE,"Invalid disc tree");
                long child = buf.getInt(pos + 4) & 0xffffffffL, bytes = buf.getInt(pos + 8) & 0xffffffffL;
                if ((data[pos + 12] & 16) != 0) directory(path + "/", child, bytes, depth + 1);
                else {
                    long start = base + child * 2048;
                    if (start > length - bytes || (total += bytes) > (8L << 30)) throw new DiscImport.Failure(DiscImport.Reason.TRUNCATED,"Invalid file extent");
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
            throw new DiscImport.Failure(DiscImport.Reason.SPACE,"Insufficient space for the disc");
        try(OutputStream out=Files.newOutputStream(stage.resolve("default.xbe"),StandardOpenOption.CREATE_NEW)){
            out.write(verifiedExecutable);
        }
        executableReady.run();
        int done = 1;
        SortedMap<String,byte[]> hashes=new TreeMap<>();
        hashes.put("default.xbe",sourceDigest);
        MessageDigest sha=DiscFacts.digest();
        // Translation is already running. Read the remaining assets in physical disc order.
        List<Map.Entry<String,long[]>> order=new ArrayList<>(files.entrySet());
        order.sort(Comparator.comparingLong(entry->entry.getValue()[0]));
        for (Map.Entry<String,long[]> file : order) {
            if(file.getKey().equals("default.xbe"))continue;
            Path path = stage.resolve(file.getKey()).normalize();
            if (!path.startsWith(stage)) throw new DiscImport.Failure(DiscImport.Reason.INVALID_TREE,"Invalid disc path");
            Files.createDirectories(path.getParent());
            try (OutputStream out = Files.newOutputStream(path, StandardOpenOption.CREATE_NEW)) { copy(file.getValue(), out, sha); }
            hashes.put(file.getKey().toLowerCase(Locale.ROOT),sha.digest());
            progress.update(++done, files.size());
        }
        facts.put("disc_files_sha256",DiscFacts.fileSet(hashes));
        facts.put("installed_xbe_sha256",DiscFacts.hex(DiscFacts.digest().digest(verifiedExecutable)));
        facts.put("installed_xbe_bytes",verifiedExecutable.length);
    }
}
