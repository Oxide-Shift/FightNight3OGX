package org.oxideshift.corner;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.Dialog;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.SoundPool;
import android.view.*;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.graphics.drawable.ColorDrawable;
import java.io.*;
import java.nio.file.Files;

/** Routes the gallery to the game display and unlocks to the corner display when connected. */
final class AchievementDisplay {
    private final Activity activity;
    private final FrameLayout top;
    private final DiscFonts fonts;
    private final Achievements awards;
    private final CornerSession session;
    private final Runnable neutralPad;
    private boolean closing,awaitingPlaylist;
    private boolean musicHome;
    private FrameLayout lowerDisplay;
    private final java.util.function.BooleanSupplier enterPlaylist;
    private final SoundPool sounds;
    private final int chime;
    private final int[] menuSounds=new int[DiscMenuSounds.Cue.values().length];
    private boolean released;
    private int navigationStream;
    private final android.util.SparseBooleanArray loaded=new android.util.SparseBooleanArray();
    private final java.util.function.Supplier<android.graphics.Bitmap[]> artwork;
    private boolean soundReady,soundLoading;
    private Dialog gallery;
    private AchievementGallery galleryPage;
    private FrameLayout promptHost,promptOverlay;
    private View banner;
    private final Runnable retire=this::slideOut;

    AchievementDisplay(Activity activity,FrameLayout top,DiscFonts fonts,java.util.function.Supplier<android.graphics.Bitmap[]> artwork,CornerSession session,File game,java.util.function.BooleanSupplier enterPlaylist,Runnable neutralPad){
        this.enterPlaylist=enterPlaylist;
        this.activity=activity;this.top=top;this.artwork=artwork;this.fonts=fonts==DiscFonts.FALLBACK?DiscFonts.load(game):fonts;this.session=session;this.awards=session.achievements;this.neutralPad=neutralPad;
        sounds=new SoundPool.Builder().setMaxStreams(3).setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build();
        sounds.setOnLoadCompleteListener((pool,id,status)->loaded(id,status));
        chime=load("corner_unlock");soundLoading=chime!=0;
        new Thread(()->loadMenu(game),"Fight Night front end").start();
    }
    private void loadMenu(File game){
        try{
            DiscMenuSounds bank=new DiscMenuSounds(game);
            for(DiscMenuSounds.Cue cue:DiscMenuSounds.Cue.values()){
                File wav=File.createTempFile("front-end-",".wav",activity.getCacheDir());
                try{
                    Files.write(wav.toPath(),bank.wave(cue));
                    synchronized(this){
                        if(released)return;
                        menuSounds[cue.ordinal()]=sounds.load(wav.getAbsolutePath(),1);
                    }
                }finally{wav.delete();}
            }
        }catch(IOException e){android.util.Log.w("FightNight","Imported front-end sounds unavailable",e);}
    }
    private int load(String name){
        int res=activity.getResources().getIdentifier(name,"raw",activity.getPackageName());
        return res==0?0:sounds.load(activity,res,1);
    }
    private void loaded(int id,int status){
        loaded.put(id,status==0);
        if(id==chime){soundLoading=false;soundReady=status==0;}
    }
    private synchronized void play(DiscMenuSounds.Cue cue){
        int id=menuSounds[cue.ordinal()];
        if(released || !loaded.get(id))return;
        if(navigationStream!=0){sounds.stop(navigationStream);navigationStream=0;}
        if(cue==DiscMenuSounds.Cue.HORIZONTAL){
            sounds.play(id,.22f,.22f,1,0,1f);
        }else if(cue==DiscMenuSounds.Cue.VERTICAL || cue==DiscMenuSounds.Cue.SCORECARD_PAGE){
            float volume=cue==DiscMenuSounds.Cue.SCORECARD_PAGE?.13f:.16f;
            navigationStream=sounds.play(id,volume,volume,1,0,1f);
        }else sounds.play(id,.45f,.45f,1,0,1f);
    }
    private void open(boolean music){
        open(music,false);
    }
    private void open(boolean music,boolean returning){
        if(gallery!=null)return;
        neutralPad.run();musicHome=music;
        Dialog dialog=new Dialog(activity,android.R.style.Theme_Material_NoActionBar_Fullscreen);
        android.content.SharedPreferences prefs=activity.getSharedPreferences("fight-setup",Activity.MODE_PRIVATE);
        AchievementGallery.MusicSettings options=music?new AchievementGallery.MusicSettings(){
            private String key(int row){return row==0?"fightMusic":"expandedMusic";}
            public boolean enabled(int row){return prefs.getBoolean(key(row),false);}
            public void openTrax(){
                if(gallery==null || closing || awaitingPlaylist)return;
                if(!enterPlaylist.getAsBoolean()){playlistUnavailable();return;}
                awaitingPlaylist=true;
                top.postDelayed(playlistTimeout,4000);
            }
            public void toggle(int row){
                prefs.edit().putBoolean(key(row),!enabled(row)).apply();
                session.musicOptions(enabled(0),enabled(1));
            }
        }:null;
        AchievementGallery page=new AchievementGallery(activity,fonts,awards,top.getWidth(),artwork.get(),this::leave,this::play,options);
        FrameLayout layers=new FrameLayout(activity);
        layers.setBackgroundColor(0xff201713);
        layers.addView(page);
        dialog.setContentView(layers);
        dialog.setOnKeyListener((d,code,key)->{
            if(code==android.view.KeyEvent.KEYCODE_BACK){
                if(key.getAction()==android.view.KeyEvent.ACTION_UP && !key.isCanceled())activity.onBackPressed();
                return true;
            }
            return page.handleKey(key,closing || awaitingPlaylist);
        });
        dialog.setOnCancelListener(d->activity.onBackPressed());
        dialog.setOnDismissListener(d->{if(gallery==dialog){routePrompts(null);galleryPage=null;gallery=null;closing=false;session.cabinetOpen=false;}});
        gallery=dialog;galleryPage=page;session.cabinetOpen=true;
        Window w=dialog.getWindow();
        w.setBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));
        w.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        w.setVolumeControlStream(AudioManager.STREAM_MUSIC);
        w.setWindowAnimations(0);
        w.getDecorView().setSystemUiVisibility(activity.getWindow().getDecorView().getSystemUiVisibility());
        if(returning)page.selectPlaylist();
        dialog.show();w.setLayout(-1,-1);
        if(!returning)play(DiscMenuSounds.Cue.FORWARD);
        routePrompts(lowerDisplay);
    }
    private final Runnable playlistTimeout=this::onPlaylistTimeout;
    private void playlistUnavailable(){
        android.widget.Toast.makeText(activity,"EA Trax could not open. Return to the launcher and start the game again.",android.widget.Toast.LENGTH_LONG).show();
    }
    private void onPlaylistTimeout(){
        if(awaitingPlaylist){
            if(session.playlistOpening()){playlistChanged();return;}
            awaitingPlaylist=false;session.cancelPlaylistRequest();
            playlistUnavailable();
        }
    }
    void playlistChanged(){
        if(gallery==null || !musicHome || !awaitingPlaylist || !session.playlistOpening())return;
        awaitingPlaylist=false;top.removeCallbacks(playlistTimeout);
        if(gallery!=null)gallery.dismiss();
    }
    private void leave(){
        if(gallery==null || closing || awaitingPlaylist)return;
        session.cancelPlaylistRequest();
        closing=true;session.menuCancelSound();gallery.dismiss();
    }
    boolean handleLowerKey(android.view.KeyEvent key){
        if(galleryPage==null)return false;
        if(key.getKeyCode()==android.view.KeyEvent.KEYCODE_BACK){
            if(key.getAction()==android.view.KeyEvent.ACTION_UP && !key.isCanceled())activity.onBackPressed();
            return true;
        }
        return galleryPage.handleKey(key,closing || awaitingPlaylist);
    }
    private void routePrompts(FrameLayout host){
        if(promptHost==host)return;
        if(promptHost!=null)promptHost.removeView(promptOverlay);
        promptHost=host;promptOverlay=null;
        if(galleryPage==null)return;
        galleryPage.promptsOnLower(host!=null);
        if(host==null)return;
        promptOverlay=new FrameLayout(host.getContext());
        promptOverlay.setClickable(true);
        promptOverlay.addView(galleryPage.controls(host.getContext()),new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM));
        host.addView(promptOverlay,new FrameLayout.LayoutParams(-1,-1));
    }
    void refresh(FrameLayout lower){
        lowerDisplay=lower;
        if(session.takeMusicRequest())open(true,session.takePlaylistReturn());
        else if(session.takeCabinetRequest())open(false);
        if(gallery!=null)routePrompts(galleryPage.getVisibility()==View.VISIBLE?lower:null);
        if(banner!=null || gallery!=null || soundLoading)return;
        FrameLayout host=lower!=null?lower:top;
        if(!host.isAttachedToWindow() || host.getWidth()<=0)return;
        Achievements.Award award=awards.nextRibbon();if(award==null)return;
        View v=AchievementGallery.ribbon(host.getContext(),fonts,award,host.getWidth());
        FrameLayout.LayoutParams pos=new FrameLayout.LayoutParams(-2,-2,Gravity.RIGHT|Gravity.TOP);
        pos.topMargin=AchievementGallery.dp(host.getContext(),8);
        banner=v;host.addView(v,pos);
        boolean animate=ValueAnimator.areAnimatorsEnabled();
        if(animate){
            v.measure(View.MeasureSpec.makeMeasureSpec(host.getWidth(),View.MeasureSpec.AT_MOST),
                    View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));
            v.setTranslationX(v.getMeasuredWidth()*.85f);v.setAlpha(.15f);
            View badge=((android.view.ViewGroup)v).getChildAt(0);
            View label=((android.view.ViewGroup)v).getChildAt(1);
            badge.setScaleX(.72f);badge.setScaleY(.72f);
            badge.animate().scaleX(1).scaleY(1).setStartDelay(90).setDuration(320)
                    .setInterpolator(new android.view.animation.OvershootInterpolator(.85f)).start();
            label.setAlpha(0);label.setTranslationX(AchievementGallery.dp(host.getContext(),12));
            label.animate().alpha(1).translationX(0).setStartDelay(110).setDuration(240).start();
            v.animate().alpha(1).translationX(0).setDuration(280).setInterpolator(new DecelerateInterpolator()).start();
        }
        if(soundReady)sounds.play(chime,.26f,.26f,1,0,1);
        v.announceForAccessibility(v.getContentDescription());v.postDelayed(retire,4800);
    }
    private void slideOut(){
        if(banner==null)return;
        if(!ValueAnimator.areAnimatorsEnabled()){removeBanner();return;}
        banner.animate().alpha(0).translationX(banner.getWidth()*.64f).setDuration(200).withEndAction(this::removeBanner).start();
    }
    void detach(FrameLayout host){if(lowerDisplay==host)lowerDisplay=null;if(promptHost==host)routePrompts(null);if(banner!=null && banner.getParent()==host)removeBanner();}
    private void removeBanner(){
        View old=banner;if(old==null)return;
        banner=null;old.removeCallbacks(retire);old.animate().cancel();
        android.view.ViewGroup row=(android.view.ViewGroup)old;
        for(int i=0;i<row.getChildCount();i++)row.getChildAt(i).animate().cancel();
        if(old.getParent() instanceof FrameLayout)((FrameLayout)old.getParent()).removeView(old);
    }
    void rememberOpenPage(){
        if(gallery!=null && !session.browsingTrax)session.restoreCabinet(musicHome);
    }
    void close(){
        top.removeCallbacks(playlistTimeout);awaitingPlaylist=false;session.cancelPlaylistRequest();
        session.onCabinet(null);
        if(gallery!=null)gallery.dismiss();
        removeBanner();synchronized(this){released=true;sounds.release();}
    }
}
