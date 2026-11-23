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
    private final File nativeDir, folder;
    final File runner;
    final boolean benchmark;
    private static volatile String completedBenchmark;
    private volatile boolean repeatRequested;
    File playableModule(){return preparedModule(folder);}
    boolean needsPreparation(){return repeatRequested || !playableModule().isFile() || (benchmark && !folder.getName().equals(completedBenchmark));}
    void repeatBenchmark(){repeatRequested=true;completedBenchmark=null;}

    DiscBuild(Context context) throws IOException {
        app=context.getApplicationContext();
        nativeDir=new File(app.getApplicationInfo().nativeLibraryDir);
        runner=new File(nativeDir,"libdisc_runner.so");
        String id;
        try(InputStream in=app.getAssets().open("disc-kit.id");
            BufferedReader reader=new BufferedReader(new InputStreamReader(in,"US-ASCII"))){
            id=reader.readLine();
        }
        if(id==null || !id.matches("[0-9a-f]{64}"))throw new IOException("Invalid disc build kit");
        folder=new File(new File(app.getFilesDir(),"disc-build"),id);
        benchmark=java.util.Arrays.asList(app.getAssets().list("")).contains("benchmark-preparation");
    }

    static File preparedModule(File folder){
        File current=new File(folder,"libfightnight_game.so");
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

    private void unpack(File kit, GameLibrary.Status status) throws IOException {
        Path base=kit.toPath();
        Files.createDirectories(base);
        try(android.content.res.AssetFileDescriptor asset=app.getAssets().openFd("disc-kit.zip");
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

    synchronized String prepare(File xbe, GameLibrary.Status status)throws Exception{
        if(!needsPreparation())return "Xbox game ready";
        final boolean benchmark=this.benchmark || repeatRequested;
        final long started=android.os.SystemClock.elapsedRealtime();
        long translating=0,compiling=0;
        File trial=new File(folder,"timed-build");
        File module=new File(folder,"libfightnight_game.so");
        File output=module;
        if(benchmark){
            GameLibrary.clearStage(trial.toPath());
            output=new File(new File(trial,"output"),module.getName());
            Files.createDirectories(output.getParentFile().toPath());
        }
        File kit=new File(benchmark?trial:folder,"kit");
        File ready=new File(kit,".ready");
        if(!ready.isFile()){
            status.update("Preparing build tools");
            unpack(kit,status);
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
        Process p=builder.start();
        try(BufferedReader in=new BufferedReader(new InputStreamReader(p.getInputStream(),"UTF-8"));
            PrintWriter log=new PrintWriter(new File(folder,"build.log"),"UTF-8")){
            for(String line;(line=in.readLine())!=null;){
                log.println(line);
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
                throw new IOException("Game preparation stopped. Tap Prepare game to retry.");
            }
        }finally{p.destroy();}
        if(benchmark){
            Files.move(output.toPath(),module.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
            completedBenchmark=folder.getName();repeatRequested=false;
        }
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
            File[] prior=folder.getParentFile().listFiles();
            if(prior!=null)for(File old:prior)
                if(!old.equals(folder) && old.getName().matches("[0-9a-f]{64}"))GameLibrary.clearStage(old.toPath());
        } catch(IOException e){android.util.Log.i("FightNightDiscBuild","Build files retained",e);}
        if(translating>0 && compiling>=translating){
            return "Game ready  ·  Translation "+duration(compiling-translating)+"  ·  Compile and link "+duration(finished-compiling)+"  ·  Total "+duration(finished-started);
        }
        return "Xbox game ready";
    }
    static String duration(long ms){
        long seconds=(ms+500)/1000;
        return String.format(java.util.Locale.ROOT,"%d:%02d",seconds/60,seconds%60);
    }
}
