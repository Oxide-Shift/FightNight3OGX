package org.oxideshift.corner;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.Dialog;
import android.media.AudioAttributes;
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
    private boolean closing;
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

    AchievementDisplay(Activity activity,FrameLayout top,DiscFonts fonts,java.util.function.Supplier<android.graphics.Bitmap[]> artwork,CornerSession session,File game,Runnable neutralPad){
        this.activity=activity;this.top=top;this.artwork=artwork;this.fonts=fonts;this.session=session;this.awards=session.achievements;this.neutralPad=neutralPad;
        sounds=new SoundPool.Builder().setMaxStreams(3).setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build();
        sounds.setOnLoadCompleteListener((pool,id,status)->loaded(id,status));
        chime=load("corner_unlock");soundLoading=chime!=0;
        new Thread(()->loadMenu(game),"Fight Night menu audio").start();
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
        if(cue==DiscMenuSounds.Cue.HORIZONTAL || cue==DiscMenuSounds.Cue.VERTICAL){
            navigationStream=sounds.play(id,.16f,.16f,1,0,1f);
        }else sounds.play(id,.45f,.45f,1,0,1f);
    }
    private void open(){
        if(gallery!=null)return;
        neutralPad.run();
        Dialog dialog=new Dialog(activity,android.R.style.Theme_Material_NoActionBar_Fullscreen);
        AchievementGallery page=new AchievementGallery(activity,fonts,awards,top.getWidth(),artwork.get(),this::leave,this::play);
        dialog.setContentView(page);
        dialog.setOnKeyListener((d,code,key)->{
            if(code==KeyEvent.KEYCODE_BUTTON_B || code==KeyEvent.KEYCODE_BACK){
                if(key.getAction()==KeyEvent.ACTION_UP)leave();
                return true;
            }
            if(!closing)page.navigate(key);
            return true;
        });
        dialog.setOnDismissListener(d->{if(gallery==dialog){routePrompts(null);galleryPage=null;gallery=null;closing=false;session.cabinetOpen=false;}});
        gallery=dialog;galleryPage=page;session.cabinetOpen=true;
        Window w=dialog.getWindow();
        w.setBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));
        w.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        w.setWindowAnimations(0);
        w.getDecorView().setSystemUiVisibility(activity.getWindow().getDecorView().getSystemUiVisibility());
        dialog.show();w.setLayout(-1,-1);play(DiscMenuSounds.Cue.FORWARD);
        if(ValueAnimator.areAnimatorsEnabled()){
            page.setTranslationX(top.getWidth());page.animate().translationX(0).setDuration(235)
                    .setInterpolator(new DecelerateInterpolator()).start();
        }
    }
    private void leave(){
        Dialog dialog=gallery;if(dialog==null || closing)return;
        closing=true;play(DiscMenuSounds.Cue.BACK);
        View page=dialog.findViewById(android.R.id.content);
        if(!ValueAnimator.areAnimatorsEnabled() || page==null){dialog.dismiss();return;}
        page.animate().translationX(top.getWidth()).setDuration(170).withEndAction(dialog::dismiss).start();
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
        if(session.takeCabinetRequest())open();
        if(gallery!=null)routePrompts(lower);
        if(banner!=null || gallery!=null || soundLoading)return;
        FrameLayout host=lower!=null?lower:top;
        if(!host.isAttachedToWindow() || host.getWidth()<=0)return;
        Achievements.Award award=awards.nextRibbon();if(award==null)return;
        View v=AchievementGallery.ribbon(host.getContext(),fonts,award);
        FrameLayout.LayoutParams pos=new FrameLayout.LayoutParams(-2,-2,Gravity.RIGHT|Gravity.TOP);
        pos.topMargin=AchievementGallery.dp(host.getContext(),8);
        banner=v;host.addView(v,pos);
        boolean animate=ValueAnimator.areAnimatorsEnabled();
        if(animate){
            v.measure(View.MeasureSpec.makeMeasureSpec(host.getWidth(),View.MeasureSpec.AT_MOST),
                    View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));
            v.setTranslationX(v.getMeasuredWidth());
            v.animate().translationX(0).setDuration(230).setInterpolator(new DecelerateInterpolator()).start();
        }
        if(soundReady)sounds.play(chime,.26f,.26f,1,0,1);
        v.announceForAccessibility(v.getContentDescription());v.postDelayed(retire,3200);
    }
    private void slideOut(){
        if(banner==null)return;
        if(!ValueAnimator.areAnimatorsEnabled()){removeBanner();return;}
        banner.animate().translationX(banner.getWidth()).setDuration(180).withEndAction(this::removeBanner).start();
    }
    void detach(FrameLayout host){if(promptHost==host)routePrompts(null);if(banner!=null && banner.getParent()==host)removeBanner();}
    private void removeBanner(){
        View old=banner;if(old==null)return;
        banner=null;old.removeCallbacks(retire);old.animate().cancel();
        if(old.getParent() instanceof FrameLayout)((FrameLayout)old.getParent()).removeView(old);
    }
    void close(){
        session.onCabinet(null);
        if(gallery!=null)gallery.dismiss();
        removeBanner();synchronized(this){released=true;sounds.release();}
    }
}
