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
    private boolean closing,changing;
    private volatile MenuTransition transition;
    private FrameLayout cabinetLayers,lowerDisplay;
    private final boolean wide;
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

    AchievementDisplay(Activity activity,FrameLayout top,DiscFonts fonts,java.util.function.Supplier<android.graphics.Bitmap[]> artwork,CornerSession session,File game,boolean wide,Runnable neutralPad){
        this.wide=wide;
        this.activity=activity;this.top=top;this.artwork=artwork;this.fonts=fonts;this.session=session;this.awards=session.achievements;this.neutralPad=neutralPad;
        sounds=new SoundPool.Builder().setMaxStreams(3).setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build();
        sounds.setOnLoadCompleteListener((pool,id,status)->loaded(id,status));
        chime=load("corner_unlock");soundLoading=chime!=0;
        new Thread(()->{
            try{transition=new MenuTransition(game,DiscFonts.load(game).stencil);}
            catch(IOException | RuntimeException e){android.util.Log.w("FightNight","Disc menu transition unavailable",e);}
            loadMenu(game);
        },"Fight Night front end").start();
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
        if(gallery!=null)return;
        neutralPad.run();
        Dialog dialog=new Dialog(activity,android.R.style.Theme_Material_NoActionBar_Fullscreen);
        android.content.SharedPreferences prefs=activity.getSharedPreferences("fight-setup",Activity.MODE_PRIVATE);
        AchievementGallery.MusicSettings options=music?new AchievementGallery.MusicSettings(){
            private String key(int row){return row==0?"fightMusic":"expandedMusic";}
            public boolean enabled(int row){return prefs.getBoolean(key(row),false);}
            public void toggle(int row){
                prefs.edit().putBoolean(key(row),!enabled(row)).apply();
                session.musicOptions(enabled(0),enabled(1));
            }
        }:null;
        AchievementGallery page=new AchievementGallery(activity,fonts,awards,top.getWidth(),artwork.get(),this::leave,this::play,options);
        FrameLayout layers=new FrameLayout(activity);
        layers.addView(page);
        dialog.setContentView(layers);
        dialog.setOnKeyListener((d,code,key)->page.handleKey(key,closing || changing));
        dialog.setOnDismissListener(d->{if(gallery==dialog){routePrompts(null);galleryPage=null;gallery=null;cabinetLayers=null;closing=false;changing=false;session.cabinetOpen=false;}});
        gallery=dialog;galleryPage=page;cabinetLayers=layers;session.cabinetOpen=true;
        Window w=dialog.getWindow();
        w.setBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));
        w.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        w.setVolumeControlStream(AudioManager.STREAM_MUSIC);
        w.setWindowAnimations(0);
        w.getDecorView().setSystemUiVisibility(activity.getWindow().getDecorView().getSystemUiVisibility());
        dialog.show();w.setLayout(-1,-1);play(DiscMenuSounds.Cue.FORWARD);
        page.setVisibility(View.INVISIBLE);
        swap(()->{page.setVisibility(View.VISIBLE);routePrompts(lowerDisplay);},()->{});
    }
    private void swap(Runnable change,Runnable done){
        MenuTransition movie=transition;
        if(movie==null || !ValueAnimator.areAnimatorsEnabled()){change.run();done.run();return;}
        FrameLayout layers=cabinetLayers;
        changing=true;
        View film=new View(activity){
            long start=-1;boolean switched;
            @Override protected void onDraw(android.graphics.Canvas c){
                long now=android.os.SystemClock.uptimeMillis();
                if(start<0)start=now;
                int frame=(int)((now-start)*60/1000);
                int w=getWidth(),h=getHeight(),aw=wide?16:4,ah=wide?9:3;
                int vw=Math.min(w,h*aw/ah),vh=Math.min(h,w*ah/aw);
                c.save();c.translate((w-vw)/2f,(h-vh)/2f);c.clipRect(0,0,vw,vh);
                movie.draw(c,frame,vw,vh);c.restore();
                if(!switched && frame>=movie.changeFrame){switched=true;change.run();}
                if(frame<movie.frames-1)postInvalidateOnAnimation();
                else post(()->{
                    if(getParent()!=layers || cabinetLayers!=layers)return;
                    layers.removeView(this);changing=false;done.run();
                });
            }
        };
        film.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        film.setClickable(true);film.setFocusable(false);
        layers.addView(film,new FrameLayout.LayoutParams(-1,-1));
    }
    private void leave(){
        Dialog dialog=gallery;if(dialog==null || closing || changing)return;
        closing=true;session.menuCancelSound();
        swap(()->{if(galleryPage!=null)galleryPage.setVisibility(View.INVISIBLE);routePrompts(null);},dialog::dismiss);
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
        if(session.takeMusicRequest())open(true);
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
    void close(){
        session.onCabinet(null);
        if(gallery!=null)gallery.dismiss();
        removeBanner();synchronized(this){released=true;sounds.release();}
    }
}
