package org.oxideshift.corner;

import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

/** App-owned copies. The document picker supplies read-only streams. */
public final class GameLibrary {
    public interface Status { void update(String message); }
    private static final long MIB = 1024L * 1024;
    private static final long MAX_DISC = 10L * 1024 * MIB;
    private GameLibrary() {}

    public static void importDisc(InputStream original, long length, Path root, Status status) throws Exception {
        Path target = root.resolve("retail"), stage = root.resolve("disc-copy.tmp");
        if (Files.exists(target)) throw new IOException("Disc already installed");
        clearStage(stage);
        Files.createDirectory(stage);
        try {
            if (length > MAX_DISC || (length > 0 && stage.toFile().getUsableSpace() < length + 2403279877L + 32*MIB))
                throw new IOException("This disc needs about 10.3 GB of free space");
            byte[] buffer = new byte[1024 * 1024];
            Path image = stage.resolve("source.iso");
            long copied = 0, last = -32*MIB;
            try (OutputStream output = Files.newOutputStream(image, StandardOpenOption.CREATE_NEW)) {
                for (int n; (n = original.read(buffer)) != -1;) {
                    copied += n;
                    if (copied > MAX_DISC) throw new IOException("Disc is larger than a supported Xbox image");
                    output.write(buffer,0,n);
                    if (copied-last >= 32*MIB) {
                        status.update("Copying disc · " + copied/MIB + (length>0?" / "+((length+MIB-1)/MIB):"") + " MiB"); last = copied;
                    }
                }
            }
            if (length > 0 && copied != length) throw new EOFException("The selected disc could not be copied in full");
            status.update("Verifying copied disc");
            try (FileChannel channel = FileChannel.open(image, StandardOpenOption.READ)) {
                DiscImage disc = new DiscImage(channel);
                Path game = Files.createDirectory(stage.resolve("game"));
                disc.extract(game,(n,count)->{if(n%8==0 || n==count)status.update("Installing disc · "+n+" / "+count);});
            }
            Files.move(stage,target,StandardCopyOption.ATOMIC_MOVE);
        } finally { clearStage(stage); }
    }

    public static String importSave(InputStream source, Path root) throws IOException {
        Path copy=Files.createTempFile(root,"save-archive-",".zip");
        try {
            Files.copy(source,copy,StandardCopyOption.REPLACE_EXISTING);
            try(ZipFile zip=new ZipFile(copy.toFile())){return importSave(zip,root);}
        }finally{Files.deleteIfExists(copy);}
    }

    private static final String TITLE="45410086";
    private static final java.util.regex.Pattern TITLE_ID=java.util.regex.Pattern.compile("[0-9a-fA-F]{8}");

    /** Portable Xbox E: layout. hakuX identifies unmarked archives as external saves
     * and can apply its own console signing rules at import. */
    static void exportSave(Path slot,OutputStream output)throws IOException{
        boolean[] hasProfile={false};
        try(ZipOutputStream zip=new ZipOutputStream(new BufferedOutputStream(output))){
            for(String area:new String[]{"UDATA","TDATA"}){
                Path tree=childIgnoreCase(slot,area);
                Path title=tree==null?null:childIgnoreCase(tree,TITLE);
                if(title==null)continue;
                Files.walkFileTree(title,new SimpleFileVisitor<Path>(){
                    private String name(Path path){
                        String suffix=title.relativize(path).toString().replace(File.separatorChar,'/');
                        return area+"/"+TITLE+(suffix.isEmpty()?"":"/"+suffix);
                    }
                    @Override public FileVisitResult preVisitDirectory(Path dir,java.nio.file.attribute.BasicFileAttributes attrs)throws IOException{
                        ZipEntry entry=new ZipEntry(name(dir)+"/");entry.setTime(attrs.lastModifiedTime().toMillis());
                        zip.putNextEntry(entry);zip.closeEntry();return FileVisitResult.CONTINUE;
                    }
                    @Override public FileVisitResult visitFile(Path file,java.nio.file.attribute.BasicFileAttributes attrs)throws IOException{
                        if(!attrs.isRegularFile())throw new IOException("Unsupported file in the selected save");
                        ZipEntry entry=new ZipEntry(name(file));entry.setTime(attrs.lastModifiedTime().toMillis());
                        zip.putNextEntry(entry);Files.copy(file,zip);zip.closeEntry();
                        if(area.equals("UDATA") && title.relativize(file).getNameCount()>=2)hasProfile[0]=true;
                        return FileVisitResult.CONTINUE;
                    }
                });
            }
            if(!hasProfile[0])throw new IOException("No saved profile in this slot. Save in the game first.");
        }
    }
    private static Path childIgnoreCase(Path parent,String name)throws IOException{
        if(!Files.isDirectory(parent,LinkOption.NOFOLLOW_LINKS))return null;
        try(DirectoryStream<Path> entries=Files.newDirectoryStream(parent)){
            for(Path child:entries)if(child.getFileName().toString().equalsIgnoreCase(name)
                    && Files.isDirectory(child,LinkOption.NOFOLLOW_LINKS))return child;
        }
        return null;
    }

    // A hakuX full backup also contains unrelated partitions and emulator files.
    // Only title directories on the Xbox E: partition are eligible.
    private static int titlePart(String[] parts){
        for(int i=0;i<parts.length;i++){
            String part=parts[i];
            if(part.equalsIgnoreCase("__MACOSX"))return -1;
            if(part.equalsIgnoreCase("hdd") && (i+1>=parts.length || !parts[i+1].equalsIgnoreCase("Partition1")))return -1;
            if(TITLE_ID.matcher(part).matches())return part.equalsIgnoreCase(TITLE)?i:-1;
        }
        return -1;
    }
    private static String[] parts(String name){
        return name.replace('\\','/').split("/+",0);
    }
    private static boolean hasParentTraversal(String[] names){
        for(String name:names)if(name.equals(".."))return true;
        return false;
    }
    // A user may also zip the contents of the title directory. Its Xbox metadata
    // identifies the game even when the eight-digit directory itself is absent.
    private static String looseTitle(ZipFile zip)throws IOException{
        for(Enumeration<? extends ZipEntry> it=zip.entries();it.hasMoreElements();){
            ZipEntry entry=it.nextElement();
            String path=entry.getName().replace('\\','/');
            String[] names=parts(path);
            if(entry.isDirectory() || names.length==0 || !names[names.length-1].equalsIgnoreCase("TitleMeta.xbx")
                || entry.getSize()<1 || entry.getSize()>8192 || hasParentTraversal(names))continue;
            boolean qualified=true;
            for(String part:names)if(TITLE_ID.matcher(part).matches() || part.equalsIgnoreCase("__MACOSX") || part.equalsIgnoreCase("hdd"))qualified=false;
            if(!qualified)continue;
            byte[] bytes=new byte[(int)entry.getSize()];
            try(DataInputStream in=new DataInputStream(zip.getInputStream(entry))){in.readFully(bytes);}
            String text=new String(bytes,java.nio.charset.StandardCharsets.UTF_8);
            if(bytes.length>=2 && (bytes[0]==(byte)0xff || bytes[0]==(byte)0xfe || bytes[1]==0)){
                java.nio.charset.Charset encoding=bytes[0]==(byte)0xfe?java.nio.charset.StandardCharsets.UTF_16BE:java.nio.charset.StandardCharsets.UTF_16LE;
                text=new String(bytes,encoding);
            }
            for(String line:text.replace(String.valueOf((char)0xfeff),"").split("[\\r\\n]")){
                if(line.trim().matches("(?i)TitleName\\s*=\\s*(EA SPORTS FN 3|(?:EA SPORTS )?Fight Night Round 3)")){
                    return path.substring(0,path.lastIndexOf('/')+1);
                }
            }
        }
        return null;
    }

    // zip paths are Xbox paths. Keep the filesystem spelling consistent on Android.
    private static String relativeSave(String name,String loose)throws IOException{
        String path=name.replace('\\','/');
        String[] names=parts(path);int first=titlePart(names);
        boolean standard=first>=0;
        if(!standard && (loose==null || !path.regionMatches(true,0,loose,0,loose.length())))return null;
        if(!standard){
            names=parts(path.substring(loose.length()));
            if(names.length==0 || !(names[0].matches("(?i)[0-9a-f]{12}") ||
                    (names.length==1 && names[0].matches("(?i)(TitleMeta|TitleImage|SaveImage)\\.xbx"))))return null;
        }
        if(path.startsWith("/") || hasParentTraversal(parts(path)))throw new IOException("Unsafe path in the save archive");
        String tree=standard && first>0 && names[first-1].equalsIgnoreCase("TDATA")?"TDATA":"UDATA";
        StringBuilder out=new StringBuilder(tree).append('/').append(TITLE);
        for(int i=standard?first+1:0;i<names.length;i++){
            String part=names[i];
            if(part.isEmpty()||part.equals(".")||part.endsWith(".")||part.endsWith(" ")||
               part.matches(".*[\\p{Cntrl}:*?\"<>|].*"))throw new IOException("Invalid Xbox filename in the ZIP");
            out.append('/').append(part);
        }
        return out.toString();
    }
    static boolean hasFightSave(ZipFile zip)throws IOException{
        String loose=looseTitle(zip);
        for(Enumeration<? extends ZipEntry> list=zip.entries();list.hasMoreElements();){
            ZipEntry entry=list.nextElement();
            if(!entry.isDirectory() && relativeSave(entry.getName(),loose)!=null)return true;
        }
        return false;
    }

    static String importSave(ZipFile zip,Path root) throws IOException {
        Path slots=Files.createDirectories(root.resolve("save-slots"));
        Path stage=Files.createTempDirectory(slots,"import-");
        String id="save-"+UUID.randomUUID().toString();
        Set<String> names=new HashSet<>();
        byte[] buffer=new byte[65536];
        long total=0;int files=0;
        try {
            String loose=looseTitle(zip);
            Map<String,Path> directories=new HashMap<>();
            directories.put("",stage);
            Set<String> otherTitles=new LinkedHashSet<>();
            for(Enumeration<? extends ZipEntry> list=zip.entries();list.hasMoreElements();){
                ZipEntry entry=list.nextElement();
                String relative=relativeSave(entry.getName(),loose);
                if(relative==null){
                    if(otherTitles.size()<4){
                        String[] parts=parts(entry.getName());
                        for(String name:parts)if(TITLE_ID.matcher(name).matches()){otherTitles.add(name);break;}
                    }
                    continue;
                }
                if(names.size()+directories.size()>=5000)throw new IOException("Too many Fight Night save entries");
                String[] components=relative.split("/");
                int folderCount=entry.isDirectory()?components.length:components.length-1;
                String key="";Path path=stage;
                for(int i=0;i<folderCount;i++){
                    key+="/"+components[i].toLowerCase(Locale.ROOT);
                    Path existing=directories.get(key);
                    if(existing==null){
                        existing=path.resolve(components[i]);
                        Files.createDirectory(existing);
                        directories.put(key,existing);
                    }
                    path=existing;
                }
                if(entry.isDirectory())continue;
                if(components.length<3)throw new IOException("The title ID must be a folder");
                String leaf=components[components.length-1];
                path=path.resolve(leaf);
                if(!names.add(key+"/"+leaf.toLowerCase(Locale.ROOT)))throw new IOException("Duplicate save file in the ZIP");
                CRC32 crc=new CRC32();
                try(InputStream in=zip.getInputStream(entry);OutputStream out=Files.newOutputStream(path,StandardOpenOption.CREATE_NEW)){
                    for(int n;(n=in.read(buffer))!=-1;){
                        total+=n;if(total>256*MIB)throw new IOException("Fight Night save is too large");
                        crc.update(buffer,0,n);out.write(buffer,0,n);
                    }
                }
                if(crc.getValue()!=entry.getCrc())throw new IOException("Damaged save archive");
                files++;
            }
            if(files==0)throw new IOException(otherTitles.isEmpty()?
                "No Xbox Fight Night save was found. Choose the original ZIP from hakuX’s Game saves export.":
                "This ZIP contains Xbox title "+String.join(", ",otherTitles)+". Fight Night Round 3 uses "+TITLE+".");
            try{Files.move(stage,slots.resolve(id),StandardCopyOption.ATOMIC_MOVE);}
            catch(AtomicMoveNotSupportedException e){Files.move(stage,slots.resolve(id));}
            return id;
        }finally{clearStage(stage);}
    }
    static void clearStage(Path path) throws IOException {
        if (!Files.exists(path,LinkOption.NOFOLLOW_LINKS)) return;
        if (Files.isDirectory(path,LinkOption.NOFOLLOW_LINKS)) {
            try(DirectoryStream<Path> children=Files.newDirectoryStream(path)) {
                for(Path child:children)clearStage(child);
            }
        }
        Files.delete(path);
    }
}
