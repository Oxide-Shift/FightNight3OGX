import org.oxideshift.corner.*;
import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.zip.*;

public class GameLibrarySmoke {
    private static byte[] digest(Path path)throws Exception{
        MessageDigest sum=MessageDigest.getInstance("SHA-256");byte[] buf=new byte[1024*1024];
        try(InputStream in=Files.newInputStream(path)){for(int n;(n=in.read(buf))!=-1;)sum.update(buf,0,n);}
        return sum.digest();
    }
    public static void main(String[] args)throws Exception{
        Path original=Paths.get(args[0]),root=Files.createDirectories(Paths.get(args[1]));
        long size=Files.size(original),modified=Files.getLastModifiedTime(original).toMillis();
        byte[] before=digest(original);
        try(InputStream in=Files.newInputStream(original)){GameLibrary.importDisc(in,size,root,s->{});}
        if(Files.exists(root.resolve("retail/source.iso")) || Files.size(original)!=size ||
           Files.getLastModifiedTime(original).toMillis()!=modified || !Arrays.equals(before,digest(original)))throw new AssertionError("Disc copy changed");
        System.out.println("Stream import removed its staging ISO and preserved the selected disc hash and modification time.");
        try(java.util.stream.Stream<Path> game=Files.walk(root.resolve("retail/game"))){
            if(game.filter(Files::isRegularFile).count()!=901)throw new AssertionError("Disc asset inventory");
        }
        ByteArrayOutputStream data=new ByteArrayOutputStream();
        try(ZipOutputStream zip=new ZipOutputStream(data)){zip.putNextEntry(new ZipEntry("UDATA/45410086/slot/test.dat"));zip.write(53);}
        String id=GameLibrary.importSave(new ByteArrayInputStream(data.toByteArray()),root);
        Path first=root.resolve("save-slots").resolve(id).resolve("UDATA/45410086/slot/test.dat");
        if(Files.readAllBytes(first)[0]!=53)throw new AssertionError();
        data.reset();
        try(ZipOutputStream zip=new ZipOutputStream(data)){zip.putNextEntry(new ZipEntry("UDATA/45410086/../other"));zip.write(1);}
        try {GameLibrary.importSave(new ByteArrayInputStream(data.toByteArray()),root);throw new AssertionError("Traversal accepted");}
        catch(IOException expected){}
        if(Files.readAllBytes(first)[0]!=53)throw new AssertionError("Original slot changed");
        System.out.println("901 game files installed, save imported, malformed save rejected with the previous slot intact.");
    }
}
