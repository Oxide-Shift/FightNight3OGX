package org.oxideshift.corner;

import android.content.Context;
import java.io.*;
import java.nio.file.*;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Prepare the player's game locally with the tools in the signed launcher. */
final class DiscBuild {
    private final Context app;
    private final File nativeDir;
    final File runner;
    final boolean benchmark;
    private final boolean palBundled;
    private final java.util.function.Supplier<File> installed;
    private final Map<String,String> kitIds=new java.util.HashMap<>();
    private static volatile String completedBenchmark;
    /** The signed kit for the installed executable: US, or the PAL kit when the APK carries one. */
    private String kit(File xbe){return DiscRegion.kit(xbe,palBundled);}
    private File folder(String kit)throws IOException{
        String id;
        synchronized(kitIds){
            id=kitIds.get(kit);
            if(id==null){
                try(InputStream in=app.getAssets().open(kit+".id");
                    BufferedReader reader=new BufferedReader(new InputStreamReader(in,"US-ASCII"))){
                    id=reader.readLine();
                }
                if(id==null || !id.matches("[0-9a-f]{64}"))throw new IOException("Invalid disc build kit");
                kitIds.put(kit,id);
            }
        }
        return new File(new File(app.getFilesDir(),"disc-build"),id);
    }
    private File folder(){
        try{return folder(kit(installed.get()));}
        catch(IOException e){throw new UncheckedIOException(e);}
    }
    File playableModule(){return preparedModule(folder());}
    private File inspected;
    private long inspectedLength,inspectedTime;
    private boolean loadableLayout;
    synchronized boolean needsPreparation(){
        File folder=folder();
        File module=preparedModule(folder);
        long length=module.length(),time=module.lastModified();
        if(!module.equals(inspected) || length!=inspectedLength || time!=inspectedTime){
            inspected=module;inspectedLength=length;inspectedTime=time;loadableLayout=arm64Module(module);
        }
        return !loadableLayout || (module.getParentFile().equals(folder) && seal(module).length()!=32) ||
                (benchmark && !folder.getName().equals(completedBenchmark));
    }

    static boolean arm64Module(File module){
        if(!module.isFile())return false;
        try(RandomAccessFile file=new RandomAccessFile(module,"r")){
            long size=file.length();
            byte[] h=new byte[64];file.readFully(h);
            java.nio.ByteBuffer b=java.nio.ByteBuffer.wrap(h).order(java.nio.ByteOrder.LITTLE_ENDIAN);
            if(b.getInt(0)!=0x464c457f || h[4]!=2 || h[5]!=1 || b.getShort(16)!=3 || b.getShort(18)!=183)return false;
            long table=b.getLong(32);
            int stride=b.getShort(54)&65535,count=b.getShort(56)&65535;
            if(table<64 || stride!=56 || count==0 || table>size-(long)stride*count)return false;
            file.seek(table);
            boolean text=false;
            for(int i=0;i<count;i++){
                file.readFully(h,0,stride);
                if(b.getInt(0)!=1)continue;
                long offset=b.getLong(8),bytes=b.getLong(32);
                if(offset<0 || bytes<0 || bytes>b.getLong(40) || offset>size-bytes)return false;
                if((b.getInt(4)&1)!=0 && bytes>0)text=true;
            }
            return text;
        }catch(IOException e){return false;}
    }

    DiscBuild(Context context,java.util.function.Supplier<File> installedXbe) throws IOException {
        app=context.getApplicationContext();
        nativeDir=new File(app.getApplicationInfo().nativeLibraryDir);
        runner=new File(nativeDir,"libdisc_runner.so");
        installed=installedXbe;
        palBundled=DiscRegion.palBundled(app);
        folder(DiscRegion.US_KIT);   // the US kit must always be readable
        if(palBundled)folder(DiscRegion.PAL_KIT);
        benchmark=java.util.Arrays.asList(app.getAssets().list("")).contains("benchmark-preparation");
    }

    static File preparedModule(File folder){
        File current=new File(folder,"libfightnight_game.so");
        // Alpha 47 permits a seventh compiler process on capable devices.
        // Both schedules produced the same ARM64 binary.
        if(!current.isFile() && folder.getName().equals("c1894a564908041f5bb0f54a77f5e8517d99262b7c81ecfb66cf2f49cd5a0d81")){
            File previous=preparedModule(new File(folder.getParentFile(),"48d9ad8b971f20a77f7695e15661d2b47240d62793e13616735a2e32647ceee7"));
            if(previous.isFile() && previous.length()>64)return previous;
        }
        // Alpha 46 changes worker scheduling and patch lookup. Its generated files
        // matched Alpha 45 byte for byte, with the same compiler and runtime.
        if(!current.isFile() && folder.getName().equals("48d9ad8b971f20a77f7695e15661d2b47240d62793e13616735a2e32647ceee7")){
            File previous=new File(folder.getParentFile(),"1ed6887e8b501f9e0c20d15cd3b7cec38df65dae36fb9767be87a5abe59fcc1b/libfightnight_game.so");
            if(previous.isFile() && previous.length()>64)return previous;
        }
        // Only these two verified release kits produce identical guest sources
        // and ship the same compiler, link flags and native runtime.
        if(!current.isFile() && folder.getName().equals("fbd6a69963af4c68c3e6d1ef839ac08ef346204f12886a274a94c82991699063")){
            File alpha40=new File(folder.getParentFile(),"782d09bf447b7bf9d306b4f6b7353dadc0553e21faa7818746d3069ab199885b/libfightnight_game.so");
            if(alpha40.isFile() && alpha40.length()>64)return alpha40;
        }
        return current;
    }

    private void unpack(File kit, String kitName, GameLibrary.Status status) throws IOException {
        Path base=kit.toPath();
        Files.createDirectories(base);
        try(android.content.res.AssetFileDescriptor asset=app.getAssets().openFd(kitName+".zip");
            InputStream stream=asset.createInputStream()){
            final long size=asset.getLength();
            final long totalKiB=(size+1023)/1024;
            status.update("Preparing compiler tools: 0 / "+totalKiB+" KiB");
            InputStream counted=new FilterInputStream(stream){
                private long consumed;
                private int lastPercent;
                @Override public int read(byte[] data,int offset,int length)throws IOException{
                    int n=super.read(data,offset,length);
                    if(n>0){
                        consumed+=n;
                        int percent=(int)(100*consumed/size);
                        if(percent>lastPercent && percent<100){
                            lastPercent=percent;
                            status.update("Preparing compiler tools: "+consumed/1024+" / "+totalKiB+" KiB");
                        }
                    }
                    return n;
                }
            };
            try(ZipInputStream zip=new ZipInputStream(new BufferedInputStream(counted))){
                for(ZipEntry e;(e=zip.getNextEntry())!=null;){
                    Path target=base.resolve(e.getName()).normalize();
                    if(!target.startsWith(base) || target.equals(base))throw new IOException("Invalid build kit path");
                    if(e.isDirectory())Files.createDirectories(target);
                    else{
                        Files.createDirectories(target.getParent());
                        // Restarting an interrupted extraction replaces only files from our own signed APK.
                        Files.copy(zip,target,StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }
            status.update("Preparing compiler tools: "+totalKiB+" / "+totalKiB+" KiB");
        }
    }

    String prepare(File xbe, GameLibrary.Status status)throws Exception{
        synchronized(DiscBuild.class){return prepareLocked(xbe,status);}
    }

    private String prepareLocked(File xbe, GameLibrary.Status status)throws Exception{
        if(!needsPreparation())return "Xbox game ready";
        final String kitName=kit(xbe);
        final File folder=folder(kitName);
        final long started=android.os.SystemClock.elapsedRealtime();
        long translating=0,compiling=0;
        File trial=new File(folder,"timed-build");
        File module=new File(folder,"libfightnight_game.so");
        File output=new File(folder,"libfightnight_candidate.so");
        if(benchmark){
            GameLibrary.clearStage(trial.toPath());
            output=new File(new File(trial,"output"),module.getName());
            Files.createDirectories(output.getParentFile().toPath());
        }
        File kit=new File(benchmark?trial:folder,"kit");
        File ready=new File(kit,".ready");
        if(!ready.isFile()){
            status.update("Preparing build tools");
            unpack(kit,kitName,status);
            Files.write(ready.toPath(),new byte[]{1});
        }
        ProcessBuilder builder=new ProcessBuilder(new File(nativeDir,"libpyrun.so").getAbsolutePath(),
                "-u",new File(kit,"tools/device_build.py").getAbsolutePath(),xbe.getAbsolutePath(),
                output.getAbsolutePath(),nativeDir.getAbsolutePath());
        builder.directory(folder);builder.redirectErrorStream(true);
        Map<String,String> env=builder.environment();
        env.put("PYTHONHOME",new File(kit,"py").getAbsolutePath());
        env.put("PYTHONPATH",new File(kit,"site").getAbsolutePath());
        env.put("PYTHONDONTWRITEBYTECODE","1");
        env.put("LIBCAPSTONE_PATH",nativeDir.getAbsolutePath());env.put("LD_LIBRARY_PATH",nativeDir.getAbsolutePath());
        File temp=new File(folder,"tmp");Files.createDirectories(temp.toPath());env.put("TMPDIR",temp.getAbsolutePath());
        status.update("Reading Xbox executable");
        Process p;
        try{p=builder.start();}
        catch(IOException e){throw new DiscImport.Failure(DiscImport.Reason.COMPILER,
                "Android could not start the bundled ARM64 compiler. Install the complete APK over this app, then tap Prepare game.");}
        boolean outOfSpace=false;
        try(BufferedReader in=new BufferedReader(new InputStreamReader(p.getInputStream(),"UTF-8"));
            PrintWriter log=new PrintWriter(new File(folder,"build.log"),"UTF-8")){
            for(String line;(line=in.readLine())!=null;){
                log.println(line);
                if(line.contains("No space left on device") || line.contains("ENOSPC"))outOfSpace=true;
                if(translating==0 && line.startsWith("Translating Xbox functions:"))translating=android.os.SystemClock.elapsedRealtime();
                if(compiling==0 && (line.startsWith("Preparing shared") || line.startsWith("Compiling Xbox game:")))compiling=android.os.SystemClock.elapsedRealtime();
                if(line.startsWith("Compiling Xbox game") || line.startsWith("Preparing shared") ||
                   line.startsWith("Reusing compiled") || line.startsWith("Resuming saved") || line.startsWith("Retrying interrupted") ||
                   line.startsWith("Linking optimized") || line.startsWith("Saving prepared") ||
                   line.startsWith("Verifying imported") || line.startsWith("Translating ") ||
                   line.startsWith("Writing translated") || line.startsWith("Applying game fixes"))status.update(line);
                else if(line.equals("xbe_parser"))status.update("Reading Xbox executable sections");
                else if(line.equals("disasm"))status.update("Finding Xbox functions and branch targets");
                else if(line.equals("func_id"))status.update("Identifying game and library functions");
                else if(line.equals("abi_analysis"))status.update("Analyzing function arguments and registers");
                else if(line.equals("recomp"))status.update("Translating Xbox functions to native source");
            }
            int result=p.waitFor();
            if(result!=0 || !output.isFile()){
                android.util.Log.e("FightNightDiscBuild","Compiler exited "+result);
                if(outOfSpace)throw new DiscImport.Failure(DiscImport.Reason.SPACE,
                        "Compilation ran out of internal storage. Free space on this device, then tap Prepare game to resume.");
                String message=result==137 || result==143
                        ?"Android stopped the compiler. Close other apps, keep Fight Setup open and tap Prepare game to resume."
                        :"The compiler stopped (exit "+result+"). Tap Prepare game to retry with the installed disc.";
                throw new DiscImport.Failure(DiscImport.Reason.COMPILER,message);
            }
        }finally{p.destroy();}
        status.update("Checking prepared game");
        verifyAndPublish(output,module,candidate->{
            Process probe=new ProcessBuilder(runner.getAbsolutePath(),"--check-module",candidate.getAbsolutePath())
                    .redirectErrorStream(true).redirectOutput(new File(folder,"module-check.log")).start();
            try{return probe.waitFor(30,java.util.concurrent.TimeUnit.SECONDS) && probe.exitValue()==0;}
            finally{probe.destroyForcibly();}
        });
        if(benchmark)completedBenchmark=folder.getName();
        final long finished=android.os.SystemClock.elapsedRealtime();
        // The module lives under files/, so Android clearing cache cannot require another disc build.
        // Remove only compiler files in the game's dedicated private build directory.
        try {
            if(benchmark){
                String key=new String(Files.readAllBytes(new File(kit,"guest-build.id").toPath()),"US-ASCII").trim();
                if(key.matches("[0-9a-f]{64}")){
                    Path shared=new File(folder.getParentFile(),"guest-cache").toPath();
                    Files.createDirectories(shared);
                    Path cached=shared.resolve(key);
                    if(!Files.exists(cached))Files.move(new File(new File(trial,"guest-cache"),key).toPath(),cached,StandardCopyOption.ATOMIC_MOVE);
                }
                GameLibrary.clearStage(trial.toPath());
            }else GameLibrary.clearStage(kit.toPath());
            GameLibrary.clearStage(temp.toPath());
            File[] prior=folder.getParentFile().listFiles();
            if(prior!=null)for(File old:prior)
                if(!old.equals(folder) && old.getName().matches("[0-9a-f]{64}"))GameLibrary.clearStage(old.toPath());
        } catch(IOException e){android.util.Log.i("FightNightDiscBuild","Build files retained",e);}
        if(translating>0 && compiling>=translating){
            return "Game ready  ·  Translation "+duration(compiling-translating)+"  ·  Compile and link "+duration(finished-compiling)+"  ·  Total "+duration(finished-started);
        }
        return "Xbox game ready";
    }
    private static File seal(File module){return new File(module.getParentFile(),module.getName()+".sha256");}
    private static byte[] moduleDigest(File module)throws IOException{
        java.security.MessageDigest hash;
        try{hash=java.security.MessageDigest.getInstance("SHA-256");}
        catch(java.security.NoSuchAlgorithmException impossible){throw new AssertionError(impossible);}
        try(InputStream input=new FileInputStream(module)){
            byte[] block=new byte[128*1024];
            for(int n;(n=input.read(block))!=-1;)hash.update(block,0,n);
        }
        return hash.digest();
    }
    // Called off the UI thread immediately before spawning the game child. A damaged
    // payload can retain both its ELF load segments and its original file length.
    static synchronized boolean verifyPayload(File module)throws IOException{
        File receipt=seal(module);
        if(receipt.length()==32 && java.security.MessageDigest.isEqual(
                Files.readAllBytes(receipt.toPath()),moduleDigest(module)))return true;
        Files.deleteIfExists(receipt.toPath());
        return false;
    }
    File sealedModule(){
        File folder=folder();
        File module=preparedModule(folder);
        return module.getParentFile().equals(folder)?module:null;
    }
    interface ModuleCheck {boolean canLoad(File module)throws Exception;}
    static void verifyAndPublish(File candidate,File destination,ModuleCheck check)throws Exception{
        if(!check.canLoad(candidate)){
            Files.deleteIfExists(candidate.toPath());
            throw new DiscImport.Failure(DiscImport.Reason.COMPILER,"Android could not load the compiled ARM64 game. Tap Prepare game to retry. If it repeats, send the report from Fight Setup.");
        }
        Path temp=Files.createTempFile(destination.getAbsoluteFile().getParentFile().toPath(),"module-receipt-",".tmp");
        try{
            try(FileOutputStream stream=new FileOutputStream(temp.toFile())){
                stream.write(moduleDigest(candidate));stream.getFD().sync();
            }
            Files.move(candidate.toPath(),destination.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
            Files.move(temp,seal(destination).toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
        }finally{Files.deleteIfExists(temp);}
    }
    static String duration(long ms){
        long seconds=(ms+500)/1000;
        return String.format(java.util.Locale.ROOT,"%d:%02d",seconds/60,seconds%60);
    }
}
