package org.oxideshift.corner;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.SoundPool;
import android.view.*;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
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
    private boolean musicHome,changing,backPending,openPending;
    private MenuTransition transition;
    private boolean transitionLoaded;
    private Runnable waitingForTransition;
    private FrameLayout cabinetLayers;
    private View previousFocus;
    private int menuDirection;
    private final boolean wide;
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
    private AchievementGallery galleryPage;
    private FrameLayout promptHost;
    private AchievementGallery.LowerPage promptOverlay;
    private View banner;
    private final Runnable retire=this::slideOut;

    AchievementDisplay(Activity activity,FrameLayout top,DiscFonts fonts,java.util.function.Supplier<android.graphics.Bitmap[]> artwork,CornerSession session,File game,boolean wide,java.util.function.BooleanSupplier enterPlaylist,Runnable neutralPad){
        this.enterPlaylist=enterPlaylist;this.wide=wide;
        this.activity=activity;this.top=top;this.artwork=artwork;this.fonts=fonts==DiscFonts.FALLBACK?DiscFonts.load(game):fonts;this.session=session;this.awards=session.achievements;this.neutralPad=neutralPad;
        sounds=new SoundPool.Builder().setMaxStreams(3).setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build();
        sounds.setOnLoadCompleteListener((pool,id,status)->loaded(id,status));
        chime=load("corner_unlock");soundLoading=chime!=0;
        new Thread(()->{
            MenuTransition loaded=null;
            try{loaded=new MenuTransition(game,this.fonts.stencil);}
            catch(IOException | RuntimeException e){android.util.Log.w("FightNight","Disc menu transition unavailable",e);}
            MenuTransition movie=loaded;
            activity.runOnUiThread(()->{
                transition=movie;transitionLoaded=true;
                Runnable waiting=waitingForTransition;waitingForTransition=null;
                if(waiting!=null)waiting.run();
            });
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
        open(music,false);
    }
    private void open(boolean music,boolean returning){
        if(galleryPage!=null)return;
        neutralPad.run();musicHome=music;
        android.content.SharedPreferences prefs=activity.getSharedPreferences("fight-setup",Activity.MODE_PRIVATE);
        AchievementGallery.MusicSettings options=music?new AchievementGallery.MusicSettings(){
            private String key(int row){return row==0?"fightMusic":"expandedMusic";}
            public boolean enabled(int row){return prefs.getBoolean(key(row),false);}
            public void openTrax(){
                if(galleryPage==null || closing || session.waitingForPlaylist())return;
                if(changing){openPending=true;return;}
                if(enterPlaylist.getAsBoolean())playlistChanged();
            }
            public void toggle(int row){
                if(galleryPage==null || closing || session.waitingForPlaylist())return;
                prefs.edit().putBoolean(key(row),!enabled(row)).apply();
                session.musicOptions(enabled(0),enabled(1));
            }
        }:null;
        AchievementGallery page=new AchievementGallery(activity,fonts,awards,top.getWidth(),artwork.get(),this::leave,this::play,options);
        FrameLayout layers=new FrameLayout(activity);
        layers.addView(page);
        // Keep the original game surface in its visible window throughout the handoff.
        // This layer owns touch and controller input only while its page is on screen.
        layers.setClickable(true);
        galleryPage=page;cabinetLayers=layers;session.cabinetOpen=true;
        previousFocus=top.findFocus();menuDirection=0;
        layers.setDefaultFocusHighlightEnabled(false);
        layers.setFocusableInTouchMode(true);
        top.addView(layers,new FrameLayout.LayoutParams(-1,-1));
        layers.requestFocus();
        if(returning){page.selectPlaylist();routePrompts(lowerDisplay);return;}
        play(DiscMenuSounds.Cue.FORWARD);
        page.setVisibility(View.INVISIBLE);
        swap(()->{page.setVisibility(View.VISIBLE);routePrompts(lowerDisplay);},()->{
            if(backPending){backPending=false;openPending=false;leave();}
            else if(openPending){
                openPending=false;
                if(page.playlistSelected() && enterPlaylist.getAsBoolean())playlistChanged();
            }
        });
    }
    private void swap(Runnable change,Runnable done){
        FrameLayout parent=cabinetLayers;
        // This is a movie from the disc, played on the same clock as the other game menus.
        if(!ValueAnimator.areAnimatorsEnabled()){
            change.run();done.run();return;
        }
        if(!transitionLoaded){
            changing=true;
            waitingForTransition=()->{
                if(parent!=cabinetLayers)return;
                changing=false;swap(change,done);
            };
            return;
        }
        MenuTransition movie=transition;
        if(movie==null){change.run();done.run();return;}
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
                if(cabinetLayers!=layers)return;
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
    private void removePage(){
        if(cabinetLayers==null)return;
        routePrompts(null);
        top.removeView(cabinetLayers);
        if(previousFocus!=null && previousFocus.isAttachedToWindow())previousFocus.requestFocus();
        previousFocus=null;menuDirection=0;
        cabinetLayers=null;galleryPage=null;waitingForTransition=null;
        changing=false;closing=false;backPending=false;openPending=false;session.cabinetOpen=false;
    }
    void playlistChanged(){
        // The guest begins its own transition after consuming Open.
        if(musicHome && galleryPage!=null && session.playlistOpening())removePage();
    }
    private void leave(){
        if(galleryPage==null || closing || session.waitingForPlaylist())return;
        if(changing){backPending=true;return;}
        if(galleryPage.closeAward()){session.menuCancelSound();return;}
        session.leaveMusicParent();
        closing=true;session.menuCancelSound();
        swap(()->{galleryPage.setVisibility(View.INVISIBLE);routePrompts(null);},this::removePage);
    }
    boolean handlePageKey(android.view.KeyEvent key){
        if(galleryPage==null)return false;
        if(closing || session.waitingForPlaylist())return !key.isSystem();
        int code=key.getKeyCode();
        if(key.getAction()==KeyEvent.ACTION_DOWN && (code==KeyEvent.KEYCODE_DPAD_UP || code==KeyEvent.KEYCODE_DPAD_DOWN ||
                code==KeyEvent.KEYCODE_BUTTON_L1 || code==KeyEvent.KEYCODE_BUTTON_R1))openPending=false;
        return galleryPage.handleKey(key,changing && galleryPage.getVisibility()!=View.VISIBLE);
    }
    boolean handleMotion(MotionEvent event){
        if(galleryPage==null || !FightPad.directionalMotion(event))return false;
        if(event.getActionMasked()==MotionEvent.ACTION_CANCEL){menuDirection=0;return true;}
        if(event.getActionMasked()!=MotionEvent.ACTION_MOVE)return true;
        float x=event.getAxisValue(MotionEvent.AXIS_HAT_X),y=event.getAxisValue(MotionEvent.AXIS_HAT_Y);
        if(Math.abs(x)<.5f && Math.abs(y)<.5f){
            x=event.getAxisValue(MotionEvent.AXIS_X);y=event.getAxisValue(MotionEvent.AXIS_Y);
        }
        int direction=Math.max(Math.abs(x),Math.abs(y))<.55f?0:
                Math.abs(y)>=Math.abs(x)?(y<0?KeyEvent.KEYCODE_DPAD_UP:KeyEvent.KEYCODE_DPAD_DOWN):
                (x<0?KeyEvent.KEYCODE_DPAD_LEFT:KeyEvent.KEYCODE_DPAD_RIGHT);
        if(direction!=menuDirection && direction!=0){
            openPending=false;
            handlePageKey(new KeyEvent(KeyEvent.ACTION_DOWN,direction));
        }
        menuDirection=direction;return true;
    }
    private void routePrompts(FrameLayout host){
        if(promptHost==host){if(promptOverlay!=null)promptOverlay.update(session.song());return;}
        if(promptHost!=null)promptHost.removeView(promptOverlay);
        promptHost=host;promptOverlay=null;
        if(galleryPage==null)return;
        galleryPage.promptsOnLower(host!=null);
        if(host==null)return;
        promptOverlay=galleryPage.new LowerPage(host.getContext());
        promptOverlay.update(session.song());
        host.addView(promptOverlay,new FrameLayout.LayoutParams(-1,-1));
    }
    boolean showingAward(){return banner!=null && banner.getVisibility()==View.VISIBLE;}
    void refresh(FrameLayout lower){
        lowerDisplay=lower;
        session.checkPlaylistArrival();
        if(galleryPage==null && session.takeMusicRequest())open(true,session.takePlaylistReturn());
        else if(galleryPage==null && session.takeCabinetRequest())open(false);
        if(galleryPage!=null)routePrompts(galleryPage.getVisibility()==View.VISIBLE?lower:null);
        if(session.fightCueVisible()){
            if(banner!=null)banner.setVisibility(View.INVISIBLE);
            return;
        }
        if(banner!=null){banner.setVisibility(View.VISIBLE);return;}
        if(galleryPage!=null || soundLoading)return;
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
            badge.animate().scaleX(1).scaleY(1).setDuration(160).start();
            label.setAlpha(0);label.setTranslationX(AchievementGallery.dp(host.getContext(),12));
            label.animate().alpha(1).translationX(0).setStartDelay(40).setDuration(120).start();
            v.animate().alpha(1).translationX(0).setDuration(160).setInterpolator(new DecelerateInterpolator()).start();
        }
        if(soundReady)sounds.play(chime,.26f,.26f,1,0,1);
        v.announceForAccessibility(v.getContentDescription());v.postDelayed(retire,animate?2160:2000);
    }
    private void slideOut(){
        if(banner==null)return;
        if(!ValueAnimator.areAnimatorsEnabled()){removeBanner();return;}
        banner.animate().alpha(0).translationX(banner.getWidth()*.64f).setDuration(140).withEndAction(this::removeBanner).start();
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
        if(galleryPage!=null && !session.browsingTrax)session.restoreCabinet(musicHome);
    }
    void close(){
        session.onCabinet(null);
        removePage();
        removeBanner();synchronized(this){released=true;sounds.release();}
    }
}
