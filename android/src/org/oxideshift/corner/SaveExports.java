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
    static String importDocument(ContentResolver resolver,Uri uri,Path root)throws IOException{
        // Full disk backups can be large. Use a directly seekable ZIP for those;
        // ordinary exports and cloud/pipe-backed documents use the picker stream.
        try(AssetFileDescriptor fd=resolver.openAssetFileDescriptor(uri,"r")){
            if(fd!=null && fd.getStartOffset()==0 && fd.getLength()>256L*1024*1024){
                ZipFile archive=null;
                try{archive=new ZipFile("/proc/self/fd/"+fd.getParcelFileDescriptor().getFd());}
                catch(IOException ignored){}
                if(archive!=null)try(ZipFile opened=archive){return GameLibrary.importSave(opened,root);}
            }
            if(fd!=null)try(InputStream in=fd.createInputStream()){return GameLibrary.importSave(in,root);}
        }
        throw new IOException("Android could not open the selected file");
    }
    static List<Entry> find(ContentResolver resolver,Uri tree)throws IOException{
        List<Entry> files=new ArrayList<>();
        Uri children=DocumentsContract.buildChildDocumentsUriUsingTree(tree,DocumentsContract.getTreeDocumentId(tree));
        String[] columns={Document.COLUMN_DOCUMENT_ID,Document.COLUMN_DISPLAY_NAME,Document.COLUMN_LAST_MODIFIED,Document.COLUMN_MIME_TYPE};
        try(Cursor rows=resolver.query(children,columns,null,null,null)){
            if(rows==null)throw new IOException("Directory unavailable");
            while(rows.moveToNext()){
                String name=rows.getString(1);
                if(name==null || Document.MIME_TYPE_DIR.equals(rows.getString(3)) || !name.toLowerCase(Locale.ROOT).endsWith(".zip"))continue;
                Uri uri=DocumentsContract.buildDocumentUriUsingTree(tree,rows.getString(0));
                try(AssetFileDescriptor fd=resolver.openAssetFileDescriptor(uri,"r")){
                    if(fd==null || fd.getStartOffset()!=0)continue;
                    try(ZipFile zip=new ZipFile("/proc/self/fd/"+fd.getParcelFileDescriptor().getFd())){
                        if(GameLibrary.hasFightSave(zip))files.add(new Entry(name,uri,rows.getLong(2)));
                    }
                }catch(IOException ignored){/* A non-seekable provider still works through Choose ZIP. */}
            }
        }
        files.sort(Comparator.comparingLong((Entry e)->e.modified).reversed().thenComparing(e->e.name));
        return files;
    }
}
