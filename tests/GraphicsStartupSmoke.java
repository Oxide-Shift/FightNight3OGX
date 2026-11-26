package org.oxideshift.corner;

import java.io.File;
import java.util.concurrent.atomic.AtomicLong;

/** Drive the real process owner with native-protocol peers and a controllable foreground clock. */
public final class GraphicsStartupSmoke {
    private static final AtomicLong clock=new AtomicLong();
    private static CornerSession session(String scenario,boolean general,boolean software){
        File java=new File(System.getProperty("java.home"),"bin/java");
        CornerSession game=new CornerSession(clock::get,new File("."),java.getPath(),"-cp",
                System.getProperty("java.class.path"),GraphicsStartupSmoke.class.getName(),scenario);
        game.superviseGraphics(general);game.setVideo(720,1,software);return game;
    }
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception{
        if(args.length!=0){
            if(args[0].equals("wait")){
                System.out.println("FNR3/1 FRONT 1");System.out.flush();
                Thread.sleep(15000);
            }else if(args[0].equals("device-lost")){
                System.err.println("FNR3/1 GPU_DEVICE_LOST");
                System.exit(75);
            }else{
                if(args[0].equals("no-adapter"))System.err.println("FNR3/1 GPU_NO_ADAPTER");
                if(!args[0].equals("unclassified"))System.err.println("FNR3/1 GPU_INIT_FAILED");
                System.exit(74);
            }
            return;
        }
        CornerSession failed=session("allocation",false,false);failed.run();
        check(failed.retryGraphics(),"Reaped Vulkan initialization failure");
        CornerSession general=session("allocation",true,false);general.run();
        check(!general.retryGraphics(),"Conservative child exhausted retry");
        CornerSession unsupported=session("no-adapter",false,false);unsupported.run();
        check(unsupported.unsupportedGraphics() && !unsupported.retryGraphics(),"Explicit software choice");
        CornerSession unknown=session("unclassified",false,false);unknown.run();
        check(!unknown.retryGraphics() && !unknown.deviceLost(),"Unclassified exit");
        CornerSession lost=session("device-lost",false,false);lost.run();
        check(lost.deviceLost() && lost.retryGraphics() && !lost.unsupportedGraphics(),"Device lost before first frame retries once");
        CornerSession lostGeneral=session("device-lost",true,false);lostGeneral.run();
        check(lostGeneral.deviceLost() && !lostGeneral.retryGraphics(),"Device lost on general shaders does not loop");
        for(boolean drawn:new boolean[]{false,true}){
            clock.set(0);
            CornerSession game=session("wait",false,false);
            Thread worker=new Thread(game::run);worker.start();
            long deadline=System.nanoTime()+5_000_000_000L;
            while(!game.atRoot() && System.nanoTime()<deadline)Thread.sleep(10);
            check(game.atRoot(),"Child ready");
            if(drawn){game.observeStartup(1,1,true);game.observeStartup(2,2,true);}
            game.setSleeping(true);clock.set(90000);game.setSleeping(false);
            game.observeStartup(5,drawn?2:-1,true);
            check(!game.startupTimedOut(),"Lid-closed time ignored");
            clock.addAndGet(31000);
            game.observeStartup(8,drawn?2:-1,drawn);
            if(drawn)game.stop();
            worker.join(5000);
            check(!worker.isAlive() && !game.active(),"Child reaped");
            check(game.retryGraphics()!=drawn,"Only missing initial presentation retries");
        }
        CornerSession policy=new CornerSession(()->0L,new File("."),new String[]{"test"});
        policy.requestSharedFrames(true);
        if(!policy.useSharedFrames())throw new AssertionError("Explicit shared transport ignored");
        policy.superviseGraphics(true);
        if(policy.useSharedFrames() || policy.allowGpuScanout())throw new AssertionError("Conservative mode opened GPU transport");
        policy.superviseGraphics(false);
        policy.setVideo(1080,2,true);
        if(policy.useSharedFrames())throw new AssertionError("Software renderer opened GPU transport");
        System.out.println("Eight fresh child processes exercised Vulkan recovery, device loss, sleep, missing presentation and stable play.");
    }
}
