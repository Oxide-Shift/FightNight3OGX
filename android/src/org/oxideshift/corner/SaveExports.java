package org.oxideshift.corner;

import android.content.ContentResolver;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.provider.DocumentsContract.Document;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.*;
import java.util.zip.ZipFile;

/** Reads only the directory selected by the player, newest matching archive first. */
final class SaveExports {
    static final class Entry {
        final String name;
        final Uri uri;
        final long modified;
        Entry(String name,Uri uri,long modified){this.name=name;this.uri=uri;this.modified=modified;}
    }
    static String importDocument(ContentResolver resolver,Uri uri,Path root,java.util.function.Consumer<Set<String>> awards)throws IOException{
        return importDocument(resolver,uri,root,awards,false);
    }
    static String importDocument(ContentResolver resolver,Uri uri,Path root,java.util.function.Consumer<Set<String>> awards,boolean requireProfile)throws IOException{
        // Full disk backups can be large. Use a directly seekable ZIP for those;
        // ordinary exports and cloud/pipe-backed documents use the picker stream.
        try(AssetFileDescriptor fd=resolver.openAssetFileDescriptor(uri,"r")){
            if(fd!=null && fd.getStartOffset()==0 && fd.getLength()>256L*1024*1024){
                ZipFile archive=null;
                try{archive=new ZipFile("/proc/self/fd/"+fd.getParcelFileDescriptor().getFd());}
                catch(IOException ignored){}
                if(archive!=null)try(ZipFile opened=archive){return GameLibrary.importSave(opened,root,awards,requireProfile);}
            }
            if(fd!=null)try(InputStream in=fd.createInputStream()){return GameLibrary.importSave(in,root,awards,requireProfile);}
        }
        throw new IOException("Android could not open the selected file");
    }
    static List<Entry> find(ContentResolver resolver,Uri tree)throws IOException{
        List<Entry> files=candidates(resolver,tree);
        // Seekable local files can be identified cheaply. For cloud documents, validate on import.
        for(Iterator<Entry> it=files.iterator();it.hasNext();){
            Entry file=it.next();
            try(AssetFileDescriptor fd=resolver.openAssetFileDescriptor(file.uri,"r")){
                if(fd==null){it.remove();continue;}
                if(fd.getStartOffset()!=0)continue;
                ZipFile archive;
                try{archive=new ZipFile("/proc/self/fd/"+fd.getParcelFileDescriptor().getFd());}
                catch(IOException streaming){continue;}
                try(ZipFile zip=archive){if(!GameLibrary.hasFightSave(zip))it.remove();}
            }catch(IOException | SecurityException unavailable){it.remove();}
        }
        return files;
    }
    interface Import {String read(Uri uri)throws IOException;}
    interface Select {void slot(String id)throws IOException;}
    static Entry restoreNewest(List<Entry> files,Import read,Select select)throws IOException{
        for(Entry file:files){
            String slot;
            try{slot=read.read(file.uri);}
            catch(IOException | SecurityException invalid){continue;}
            select.slot(slot);
            return file;
        }
        return null;
    }
    static List<Entry> candidates(ContentResolver resolver,Uri tree)throws IOException{
        List<Entry> files=new ArrayList<>();
        collect(resolver,tree,DocumentsContract.getTreeDocumentId(tree),"",files);
        files.sort(Comparator.comparingLong((Entry e)->e.modified).reversed()
                .thenComparing((Entry e)->e.name,Comparator.reverseOrder()).thenComparing(e->e.uri.toString()));
        return files;
    }
    private static void collect(ContentResolver resolver,Uri tree,String id,String prefix,List<Entry> files)throws IOException{
        Uri children=DocumentsContract.buildChildDocumentsUriUsingTree(tree,id);
        String[] columns={Document.COLUMN_DOCUMENT_ID,Document.COLUMN_DISPLAY_NAME,Document.COLUMN_LAST_MODIFIED,Document.COLUMN_MIME_TYPE};
        Map<String,String> folders=new LinkedHashMap<>();
        try(Cursor rows=resolver.query(children,columns,null,null,null)){
            if(rows==null)throw new IOException("Directory unavailable");
            while(rows.moveToNext()){
                String name=rows.getString(1),child=rows.getString(0);
                if(name==null || child==null)continue;
                if(Document.MIME_TYPE_DIR.equals(rows.getString(3))){
                    if(prefix.isEmpty() && !id.equals(child))folders.put(child,name+"/");
                }else if(name.toLowerCase(Locale.ROOT).endsWith(".zip")){
                    long when=rows.getLong(2);
                    java.util.regex.Matcher date=BACKUP_DATE.matcher(name);
                    if(date.find()){
                        try{
                            java.text.SimpleDateFormat format=new java.text.SimpleDateFormat("yyyy-MM-dd_HH-mm-ss-SSS",Locale.US);
                            format.setLenient(false);
                            when=format.parse(date.group(1)).getTime();
                        }catch(java.text.ParseException ignored){}
                    }
                    files.add(new Entry(prefix+name,DocumentsContract.buildDocumentUriUsingTree(tree,child),when));
                }
            }
        }
        for(Map.Entry<String,String> folder:folders.entrySet()){
            try{collect(resolver,tree,folder.getKey(),folder.getValue(),files);}
            catch(IOException | SecurityException unavailable){/* Other profiles can still be restored. */}
        }
    }
    private static final java.util.regex.Pattern BACKUP_DATE=java.util.regex.Pattern.compile(
            "^Career-.*?(\\d{4}-\\d{2}-\\d{2}_\\d{2}-\\d{2}-\\d{2}-\\d{3})\\.zip$",java.util.regex.Pattern.CASE_INSENSITIVE);
}
