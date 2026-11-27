package org.oxideshift.corner;

import android.graphics.*;

/** Mirrors the retail screen transition on the lower screen. The top screen plays the disc's transition movie (an
 *  orange fighter sweep, red and orange bars closing in, a hold while the next screen loads, then the bars pulling
 *  away). Each sampled top frame is matched against colour signatures of every movie frame, and the lower screen draws
 *  the same movie's bar layers (no fighter photo, logo or caption) at the matched frame, so both screens move
 *  together. The movie and signatures come from the player's own disc at run time. */
final class TransitionMirror {
    static final int COLS=32,ROWS=18,CELLS=COLS*ROWS;
    static final byte NONE=0,ORANGE=1,RED=2,ART=3;
    /** Two ways the 854x480 movie can sit in the scanout: filling it, or its centre 640 columns (4:3 output). */
    private static final float[] SPANS={1f,640f/854f};
    private volatile byte[][][] signatures;
    private volatile MenuTransition movie;
    private boolean building;
    private final byte[] seen=new byte[CELLS];
    private final Paint ink=new Paint(Paint.ANTI_ALIAS_FLAG);
    final Tracker tracker=new Tracker();

    static boolean orange(int c){return Math.abs(LiveFrame.red(c)-255)<=26 && Math.abs(LiveFrame.green(c)-138)<=30 && LiveFrame.blue(c)<=45;}
    static boolean red(int c){return Math.abs(LiveFrame.red(c)-160)<=30 && LiveFrame.green(c)<=52 && LiveFrame.blue(c)<=50;}
    static byte classify(int argb,boolean rendered){
        if(rendered && (argb>>>24)<128)return NONE;
        if(orange(argb))return ORANGE;
        if(red(argb))return RED;
        return rendered?ART:NONE;
    }

    /** Build signatures once the shared movie is available (off the UI thread). */
    private void prepare(){
        MenuTransition m=MenuTransition.loaded;
        if(m==null || building || signatures!=null)return;
        building=true;
        new Thread(()->{
            byte[][][] sig=new byte[SPANS.length][m.frames][CELLS];
            int bw=COLS*4,bh=ROWS*4;
            Bitmap b=Bitmap.createBitmap(bw,bh,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(b);
            int[] px=new int[bw*bh];
            for(int s=0;s<SPANS.length;s++){
                for(int f=0;f<m.frames;f++){
                    b.eraseColor(0);
                    // A narrower span shows the centre of the movie stretched across the whole scanout.
                    float full=bw/SPANS[s];
                    c.save();c.translate(-(full-bw)/2f,0);
                    synchronized(m){m.draw(c,f,Math.round(full),bh);}
                    c.restore();
                    b.getPixels(px,0,bw,0,0,bw,bh);
                    for(int y=0;y<ROWS;y++)for(int x=0;x<COLS;x++)sig[s][f][y*COLS+x]=classify(px[(y*4+2)*bw+x*4+2],true);
                }
            }
            b.recycle();
            tracker.setup(m.frames,m.changeFrame);
            movie=m;signatures=sig;
        },"Transition mirror").start();
    }

    /** Sample the latest top frame. Returns true while the mirror wants a sample every display frame. */
    boolean observe(LiveFrame f,long now){
        byte[][][] sig=signatures;
        if(sig==null){prepare();return false;}
        if(f==null){tracker.observe(-1,now);return tracker.active();}
        int hits=0;
        for(int y=0;y<ROWS;y++)for(int x=0;x<COLS;x++){
            int px=Math.min(f.width-1,(x*2+1)*f.width/(COLS*2)),py=Math.min(f.height-1,(y*2+1)*f.height/(ROWS*2));
            byte k=classify(f.pixels.getInt(8+4*(py*f.width+px))|0xff000000,false);
            seen[y*COLS+x]=k;if(k!=NONE)hits++;
        }
        if(hits<CELLS/25){tracker.observe(-1,now);return tracker.active();}
        float predicted=tracker.active()?tracker.position(now):-1;
        int best=-1;float bestScore=0f,bestDistance=Float.MAX_VALUE;
        for(byte[][] span:sig){
            for(int fr=0;fr<span.length;fr++){
                byte[] s=span[fr];
                int counted=0,agree=0,movieCells=0;
                for(int i=0;i<CELLS;i++){
                    byte want=s[i];
                    if(want==ART){movieCells++;continue;}
                    counted++;
                    if(want!=NONE)movieCells++;
                    if(want==seen[i])agree++;
                }
                if(counted==0)continue;
                // A lock needs the movie to be clearly present, not a stray orange menu panel.
                if(movieCells*100<CELLS*(tracker.active()?3:10))continue;
                float score=agree/(float)counted;
                float distance=predicted<0?fr:Math.abs(fr-predicted);
                if(score>bestScore+.004f || (Math.abs(score-bestScore)<=.004f && distance<bestDistance)){
                    best=fr;bestScore=score;bestDistance=distance;
                }
            }
        }
        tracker.observe(bestScore>=(tracker.active()?.86f:.9f)?best:-1,now);
        return tracker.active();
    }
    boolean active(){return tracker.active();}
    void clear(){tracker.clear();}

    /** Draw the bars at the tracked frame, cropped to cover the lower screen. Returns true while still playing. */
    boolean draw(Canvas c,int w,int h,Typeface stencil,float dp,long now,boolean reduced){
        MenuTransition m=movie;
        if(m==null || !tracker.active() || w<=0 || h<=0)return false;
        float pos=tracker.position(now);
        if(pos<0)return false;
        drawFrame(c,w,h,stencil,dp,now,m,reduced?m.changeFrame:Math.round(pos),reduced);
        return true;
    }
    /** One frame of the bar layers, cover-cropped to w x h, with the orange fill and LOADING plate. Also used by the
     *  Achievements and Music pages, which play the transition themselves rather than through the game. */
    void drawFrame(Canvas c,int w,int h,Typeface stencil,float dp,long now,MenuTransition m,int frame,boolean reduced){
        // Cover: the movie's height fills the screen and its centre is kept, like the top screen's own framing.
        float mh=h,mw=mh*m.width/m.height;
        if(mw<w){mw=w;mh=mw*m.height/m.width;}
        // Where the fighter silhouette covered the screen, the hold shows flat orange instead of the photo.
        // It slides in from the left as the bars close and back out to the left with them (a fade would show brown).
        float fill=frame<38?0f:frame<=m.changeFrame?Math.min(1f,(frame-38)/10f):Math.max(0f,1f-(frame-m.changeFrame)/10f);
        if(fill>0f){
            float eased=1f-(1f-fill)*(1f-fill);
            ink.setShader(null);ink.setColor(0xffff8a00);ink.setAlpha(255);c.drawRect(0,0,w*eased,h,ink);
        }
        c.save();c.translate((w-mw)/2f,(h-mh)/2f);
        synchronized(m){m.drawBars(c,frame,Math.round(mw),Math.round(mh));}
        c.restore();
        // While the top waits for the next screen, a LOADING plate opens across the middle of the bars.
        float plate=frame<56?0f:frame<=m.changeFrame?Math.min(1f,(frame-56)/10f):Math.max(0f,1f-(frame-m.changeFrame)/4f);
        if(plate>0f)plate(c,w,h,stencil,dp,now,plate,reduced);
    }
    /** The retail plate's palette: the transition movie's own bar orange and red (as its geometry draws them), with
     *  near-black figures. Picking colours from a screenshot instead left the plate paler than the bars, because the
     *  Thor's lower display mutes saturated colours: these source values come out exactly like the bars beside them. */
    static final int PLATE_ORANGE=0xffff8a00,PLATE_RED=0xffa01614,FIGURE=0xff120705,FIGURE_RIM=0xffb5672a;
    private final Sparring sparring=new Sparring();
    /** In the retail style: two boxers sparring in silhouette on an orange strip, LOADING in orange stencil on the red
     *  strip below. The strips open from the centre; the boxers rise in and the letters stamp down once it is open. */
    private void plate(Canvas c,int w,int h,Typeface stencil,float dp,long now,float open,boolean reduced){
        float ph=Math.max(92*dp,h*.24f),py=(h-ph)/2f,split=py+ph*.6f;
        float half=w*.5f*(1f-(1f-open)*(1f-open)*(1f-open));
        ink.setShader(null);ink.setStyle(Paint.Style.FILL);ink.setAlpha(255);
        ink.setColor(PLATE_ORANGE);c.drawRect(w/2f-half,py,w/2f+half,split,ink);
        ink.setColor(PLATE_RED);c.drawRect(w/2f-half,split,w/2f+half,py+ph,ink);
        c.save();c.clipRect(w/2f-half,py,w/2f+half,py+ph);
        // The pair rises from the floor line as the strip opens, then spars.
        float rise=Math.max(0f,Math.min(1f,(open-.35f)/.65f)),eased=1f-(1f-rise)*(1f-rise);
        float figH=(split-py)*.86f,floor=split-(split-py)*.04f+(1f-eased)*figH*.5f;
        c.save();c.clipRect(0,py,w,split);
        sparring.draw(c,w/2f,floor,figH,reduced?0:now,FIGURE,FIGURE_RIM);
        c.restore();
        // LOADING: retail-style condensed stencil, orange on red, stamping from oversized to size as the plate opens.
        float size=(py+ph-split)*.78f,stamp=open>=1f?1f:1f+(1f-open)*.35f;
        ink.setTypeface(stencil);ink.setTextAlign(Paint.Align.CENTER);ink.setLetterSpacing(.02f);ink.setTextScaleX(.86f);
        ink.setTextSize(size*stamp);ink.setColor(PLATE_ORANGE);ink.setAlpha(Math.round(255*Math.min(1f,open*1.4f)));
        float base=split+(py+ph-split)/2f-(ink.descent()+ink.ascent())/2f;
        c.drawText("LOADING",w/2f,base,ink);
        // A faint heat shimmer runs through the letters while the screen holds.
        if(!reduced && open>=1f){
            float phase=(now%3000)/1200f;
            if(phase<1f){
                float word=ink.measureText("LOADING"),sx=w/2f-word/2f-size+phase*(word+size*2);
                ink.setShader(new LinearGradient(sx-size,0,sx+size,0,new int[]{0x00ffd9a0,0x90ffd9a0,0x00ffd9a0},null,Shader.TileMode.CLAMP));
                c.drawText("LOADING",w/2f,base,ink);ink.setShader(null);
            }
        }
        ink.setTextScaleX(1f);ink.setLetterSpacing(0f);ink.setAlpha(255);c.restore();
    }

    /** Playback position, kept in step with observations. Pure logic so it can be tested off device. */
    static final class Tracker {
        static final int MISSES=5,COVERED=50;
        private int frames=141,change=119;
        private boolean active,released;
        private float anchorFrame;private long anchorTime;
        private float fps=60f;
        private int misses;
        private int lastFrame=-1;private long lastTime;
        void setup(int frames,int change){this.frames=frames;this.change=change;}
        boolean active(){return active;}
        void clear(){active=false;released=false;misses=0;lastFrame=-1;}
        private void anchor(float frame,long t){anchorFrame=frame;anchorTime=t;}
        /** Fractional movie frame at time t, or -1 when idle. Holds at the change frame until the top moves on. */
        float position(long t){
            if(!active)return -1;
            float f=anchorFrame+(t-anchorTime)*fps/1000f;
            if(!released && f>change)f=change;
            if(f>=frames-1){active=false;return -1;}
            return f;
        }
        void observe(int frame,long t){
            if(frame<0){
                if(active && ++misses>=MISSES && !released){
                    // The top left the movie early or the read was lost: let the bars pull away from here.
                    float held=position(t);
                    if(!active)return;
                    released=true;anchor(Math.max(change+1,held),t);
                }
                return;
            }
            misses=0;
            if(!active){
                if(frame>=frames-4)return;
                active=true;released=frame>change;fps=60f;lastFrame=-1;lockFrame=frame;anchor(frame,t);
            }else if(frame>change && !released){
                // The top has left the hold: continue from its frame, not from the stale pre-hold clock.
                released=true;anchor(frame,t);
            }else{
                // Reads at least eight movie frames apart, during the distinct entry, give the top's actual rate.
                if(lastFrame>=0 && frame>=lastFrame+12 && frame<46 && t>lastTime){
                    float rate=(frame-lastFrame)*1000f/(t-lastTime);
                    fps=Math.abs(rate-30f)<7f?30f:Math.abs(rate-60f)<14f?60f:Math.max(20f,Math.min(75f,rate));
                    lastFrame=frame;lastTime=t;
                }
                float now=position(t);
                if(!active)return;
                // Readings agree within a frame: keep the smooth clock; otherwise follow the top.
                // From frame 50 to the change the bars are identical; reads there only differ in the photo and logo,
                // which the lower screen does not draw, so they never move the clock.
                boolean covered=frame>=COVERED && frame<=change && now>=COVERED && now<=change && !released;
                if(Math.abs(frame-now)>1.5f && !covered)anchor(frame,t);
            }
            // The rate reference is the first frame change seen after the lock, timed by per-frame reads (the
            // lock itself may come from a slower idle read).
            if(lastFrame<0 && frame>lockFrame && frame<46){lastFrame=frame;lastTime=t;}
        }
        private int lockFrame;
    }
}
