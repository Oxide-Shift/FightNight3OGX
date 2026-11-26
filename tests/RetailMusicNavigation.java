package org.oxideshift.corner;

import java.io.*;

/** Live retail-host test. stdin supplies controller packets, OPEN and CLOSE stand for
 * the Music page actions. Every screen receipt comes from the running title. */
public final class RetailMusicNavigation {
    public static void main(String[] args)throws Exception{
        CornerSession session=new CornerSession(()->System.nanoTime()/1_000_000L,new File("."),args);
        session.onFrontChanged(()->{
            if(session.playlistOpening())session.cabinetOpen=false;
            System.out.println("FRONT "+session.frontPage);
        });
        session.onCabinet(()->{
            if(session.takeMusicRequest()){
                session.cabinetOpen=true;
                System.out.println(session.takePlaylistReturn()?"MUSIC RETURN":"MUSIC ENTRY");
            }else if(session.takeCabinetRequest()){
                session.cabinetOpen=true;System.out.println("ACHIEVEMENTS ENTRY");
            }
        });
        Thread guest=new Thread(session::run);guest.start();
        try(BufferedReader commands=new BufferedReader(new InputStreamReader(System.in))){
            for(String line;(line=commands.readLine())!=null;){
                if(line.equals("QUIT"))break;
                if(line.equals("OPEN")){
                    if(!session.requestPlaylist())throw new AssertionError("Open rejected");
                    System.out.println("OPEN ACCEPTED");
                }else if(line.equals("CLOSE")){
                    if(!session.cabinetOpen)throw new AssertionError("Missing parent");
                    session.leaveMusicParent();session.menuCancelSound();session.cabinetOpen=false;
                }else if(line.startsWith("PAD ")){
                    if((session.cabinetOpen || session.waitingForPlaylist()) && !line.equals("PAD 000000000000000000000000000000000000"))throw new AssertionError("Pad leaked to guest");
                    session.controller(line);
                }else throw new IllegalArgumentException(line);
            }
        }finally{session.stop();guest.join(5000);}
    }
}
