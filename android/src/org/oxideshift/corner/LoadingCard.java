package org.oxideshift.corner;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.*;
import android.os.SystemClock;
import android.view.View;

/** Lower screen while the top screen loads (and while the game starts): a heavy bag swinging on its chain under a
 *  gym light, taking the odd shot, over drifting ring ropes, with the transition plate's LOADING and chevrons and a
 *  rotating tip. One persistent instance: RingsideView re-adds it on every rebuild, and every motion runs on the
 *  absolute clock, so a rebuild never restarts the swing. With animations off it is a still card that still changes
 *  tips. All art is drawn here; nothing comes from the disc. */
final class LoadingCard extends View {
    static final String[] TIPS={
            "Press Back on the Thor to open Fight Setup without ending the bout.",
            "Remap any button in Fight Setup → Controller buttons.",
            "Between rounds, choose Manual and sweep the lower screen to treat cuts.",
            "The damage map heats from green to red as a boxer's health falls.",
            "Career saves back up automatically to Downloads / Fight Night Round 3.",
            "What the D-pad does is set in the game's Options → Controller.",
            "Check for updates in Fight Setup for the newest build."};
    static final long TIP_MS=4800, FADE_MS=420, SWING_MS=2600, HIT_MS=3700;
    private final Paint ink=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path=new Path();
    private final RectF box=new RectF();
    private Typeface stencil=Typeface.DEFAULT_BOLD,body=Typeface.DEFAULT;
    private String title="Loading";
    private long since=-1,lastSeen;
    private int firstTip;
    /** Fixed clock for previews and tests; -1 follows the system clock. */
    long frozenAt=-1;

    LoadingCard(Context c){super(c);setContentDescription("Loading");}
    void fonts(Typeface stencil,Typeface body){if(stencil!=null)this.stencil=stencil;if(body!=null)this.body=body;invalidate();}
    void title(String text){if(!text.equals(title)){title=text;setContentDescription(text);invalidate();}}

    private float dp(float n){return n*getResources().getDisplayMetrics().density;}
    private long now(){return frozenAt>=0?frozenAt:SystemClock.uptimeMillis();}
    private static boolean moving(){return ValueAnimator.areAnimatorsEnabled();}

    /** Bag angle in degrees: a slow pendulum plus a decaying kick from the latest shot. Pure, for tests. */
    static float swing(long t){
        double base=4.0*Math.sin(2*Math.PI*(t%SWING_MS)/SWING_MS);
        long sinceHit=t%HIT_MS;
        double kick=sinceHit<1600?14.0*Math.exp(-sinceHit/420.0)*Math.sin(sinceHit/1000.0*2*Math.PI*1.6):0;
        return (float)(base+kick);
    }
    /** Tip index and its opacity at time t into a loading hold. Pure, for tests. */
    static int tip(long elapsed,int first){return (int)((first+elapsed/TIP_MS)%TIPS.length);}
    static float tipAlpha(long elapsed){
        long in=elapsed%TIP_MS;
        if(in<FADE_MS)return in/(float)FADE_MS;
        if(in>TIP_MS-FADE_MS)return (TIP_MS-in)/(float)FADE_MS;
        return 1f;
    }

    @Override protected void onDraw(Canvas c){
        int w=getWidth(),h=getHeight();if(w<=0||h<=0)return;
        long t=now();
        // A fresh hold after a pause starts its tips again (one ordinary rebuild keeps the same hold).
        if(since<0 || t-lastSeen>2000){since=t;firstTip=(int)((t/TIP_MS)%TIPS.length);}
        lastSeen=t;
        boolean live=moving() && frozenAt<0;
        long motion=live||frozenAt>=0?t:0;
        backdrop(c,w,h,motion);
        float unit=Math.min(w,h*1.15f);
        float cx=w*.5f,top=h*.03f,bagH=Math.min(h*.56f,unit*.62f);
        bag(c,cx,top,bagH,motion,live||frozenAt>=0);
        float textY=top+bagH+h*.12f;
        loading(c,w,textY,unit,motion);
        tipLine(c,w,h,textY,t-since);
        if(live)postInvalidateOnAnimation();
    }

    private Shader floor,light,vignette;
    @Override protected void onSizeChanged(int w,int h,int oldW,int oldH){
        if(w<=0||h<=0)return;
        floor=new LinearGradient(0,0,0,h,0xff1d1410,0xff0f0b09,Shader.TileMode.CLAMP);
        // Gym light: a warm cone from above the bag.
        light=new RadialGradient(w*.5f,h*.12f,Math.max(w,h)*.62f,new int[]{0x44f6bf55,0x14af3927,0x00000000},
                new float[]{0,.45f,1},Shader.TileMode.CLAMP);
        vignette=new RadialGradient(w*.5f,h*.45f,Math.max(w,h)*.75f,0x00000000,0xc0000000,Shader.TileMode.CLAMP);
    }
    private void backdrop(Canvas c,int w,int h,long t){
        if(floor==null)onSizeChanged(w,h,0,0);
        ink.setStyle(Paint.Style.FILL);
        ink.setShader(floor);c.drawRect(0,0,w,h,ink);
        ink.setShader(light);c.drawRect(0,0,w,h,ink);
        ink.setShader(null);
        // Three ring ropes drifting slowly across the lower half, nearest last.
        float drift=(t%24000)/24000f;
        for(int i=0;i<3;i++){
            float y0=h*(.58f+i*.12f),slope=h*.05f,shift=(float)Math.sin(2*Math.PI*(drift+i*.21f))*h*.012f;
            ink.setStyle(Paint.Style.STROKE);ink.setStrokeCap(Paint.Cap.ROUND);
            ink.setStrokeWidth(Math.max(2f,h*(.008f+i*.003f)));
            ink.setColor(i==2?0x3cff7865:0x22baafa0);
            c.drawLine(-w*.05f,y0+shift,w*1.05f,y0-slope+shift,ink);
        }
        // Vignette.
        ink.setStyle(Paint.Style.FILL);
        ink.setShader(vignette);c.drawRect(0,0,w,h,ink);ink.setShader(null);
    }

    private void bag(Canvas c,float cx,float top,float height,long t,boolean animate){
        float angle=animate?swing(t):0f;
        float chain=height*.26f,bagW=height*.36f,bagH=height-chain;
        // Ceiling bracket.
        ink.setStyle(Paint.Style.FILL);ink.setColor(0xff3a302a);
        c.drawRect(cx-bagW*.4f,top-dp(2),cx+bagW*.4f,top+height*.025f,ink);
        c.save();c.rotate(angle,cx,top+height*.02f);
        // Chain: alternating links.
        ink.setStyle(Paint.Style.STROKE);ink.setStrokeWidth(Math.max(1.5f,height*.012f));
        int links=6;float link=chain/links;
        for(int i=0;i<links;i++){
            float y=top+height*.02f+i*link;
            ink.setColor(i%2==0?0xff8d8378:0xff6a625a);
            if(i%2==0){box.set(cx-link*.18f,y,cx+link*.18f,y+link*1.05f);}
            else{box.set(cx-link*.06f,y,cx+link*.06f,y+link*1.05f);}
            c.drawRoundRect(box,link*.2f,link*.2f,ink);
        }
        float y0=top+height*.02f+chain;
        // Straps fanning to the cap.
        ink.setColor(0xff4a3b32);
        c.drawLine(cx,y0,cx-bagW*.42f,y0+bagH*.07f,ink);c.drawLine(cx,y0,cx+bagW*.42f,y0+bagH*.07f,ink);
        // Bag body: leather with a light from the upper left.
        ink.setStyle(Paint.Style.FILL);
        box.set(cx-bagW/2f,y0+bagH*.06f,cx+bagW/2f,y0+bagH);
        ink.setShader(new LinearGradient(box.left,0,box.right,0,new int[]{0xff6b1d14,0xffa3352a,0xff7a2219,0xff3c0f0a},
                new float[]{0,.32f,.66f,1},Shader.TileMode.CLAMP));
        c.drawRoundRect(box,bagW*.22f,bagW*.22f,ink);ink.setShader(null);
        // Top cap and bottom band.
        ink.setColor(0xff221a16);
        float left=box.left,right=box.right,bodyTop=box.top,bottom=box.bottom;
        box.set(left,bodyTop,right,bodyTop+bagH*.07f);c.drawRoundRect(box,bagW*.2f,bagW*.12f,ink);
        box.set(left,bottom-bagH*.11f,right,bottom);c.drawRoundRect(box,bagW*.22f,bagW*.22f,ink);
        box.set(left,bodyTop,right,bottom);
        // Stitched seams.
        ink.setStyle(Paint.Style.STROKE);ink.setStrokeWidth(Math.max(1f,bagW*.012f));
        ink.setColor(0x66f2e6ca);ink.setPathEffect(new DashPathEffect(new float[]{bagW*.04f,bagW*.035f},0));
        for(float f:new float[]{.3f,.7f})c.drawLine(box.left+bagW*f,box.top+bagH*.09f,box.left+bagW*f,box.bottom-bagH*.13f,ink);
        ink.setPathEffect(null);
        // A worn patch where most shots land.
        ink.setStyle(Paint.Style.FILL);
        ink.setShader(new RadialGradient(box.left+bagW*.3f,box.top+bagH*.45f,bagW*.32f,0x40000000,0x00000000,Shader.TileMode.CLAMP));
        c.drawRect(box.left,box.top+bagH*.1f,box.right,box.bottom-bagH*.12f,ink);ink.setShader(null);
        // Specular strip, shifting with the swing.
        ink.setStyle(Paint.Style.FILL);
        float glint=box.left+bagW*(.22f-angle*.006f);
        ink.setShader(new LinearGradient(glint,0,glint+bagW*.12f,0,0x00ffffff,0x30ffffff,Shader.TileMode.MIRROR));
        c.drawRect(glint,box.top+bagH*.1f,glint+bagW*.24f,box.bottom-bagH*.14f,ink);ink.setShader(null);
        c.restore();
        float hy=y0+bagH*.45f,hx=cx-bagW*.5f;
        if(animate){
            long sinceHit=t%HIT_MS;
            // The jab: a blue-corner glove drives in over 160 ms, lands, and comes back over 300 ms.
            long toHit=HIT_MS-sinceHit;
            float reach=toHit<=160?1f-toHit/160f:sinceHit<300?1f-sinceHit/300f:0f;
            if(reach>0f){
                float eased=1f-(1f-reach)*(1f-reach);
                glove(c,hx-bagW*.02f-(1f-eased)*bagW*1.6f,hy,bagW*.62f,eased);
            }
            // Dust off the bag, outside the rotation so it hangs in the air.
            if(sinceHit<900){
                float fade=1f-sinceHit/900f,spread=bagW*.25f+height*.22f*(sinceHit/900f);
                ink.setColor(0xffe8d9bd);
                for(int i=0;i<9;i++){
                    double a=Math.PI*(.58+i*.105);
                    float r=dp(1.2f)+dp(1.8f)*((i*37)%5)/4f;
                    ink.setAlpha(Math.round(150*fade*(.5f+.5f*((i*53)%7)/6f)));
                    c.drawCircle(hx+(float)Math.cos(a)*spread,hy-(float)Math.sin(a)*spread*.6f,r,ink);
                }
                ink.setAlpha(255);
            }
        }
        // Shadow under the bag.
        float sway=(float)Math.sin(Math.toRadians(angle))*height;
        ink.setShader(new RadialGradient(cx+sway*.6f,top+height*1.07f,bagW*.75f,0x66000000,0x00000000,Shader.TileMode.CLAMP));
        c.drawOval(cx+sway*.6f-bagW*.75f,top+height*1.04f,cx+sway*.6f+bagW*.75f,top+height*1.1f,ink);ink.setShader(null);
    }

    /** A boxing glove seen from the side, knuckles at x facing right, with its laced cuff and forearm trailing left. */
    private void glove(Canvas c,float x,float y,float size,float reach){
        float mittW=size,mittH=size*.86f,left=x-mittW;
        ink.setStyle(Paint.Style.FILL);
        // Forearm running off to the left, widening toward the elbow, lit from above.
        path.reset();
        path.moveTo(left+mittW*.1f,y-mittH*.24f);path.lineTo(left-size*2.6f,y-mittH*.36f);
        path.lineTo(left-size*2.6f,y+mittH*.38f);path.lineTo(left+mittW*.1f,y+mittH*.26f);path.close();
        ink.setShader(new LinearGradient(0,y-mittH*.36f,0,y+mittH*.38f,new int[]{0xffb07a55,0xff7d5036,0xff4a2d1e},
                new float[]{0,.5f,1},Shader.TileMode.CLAMP));
        c.drawPath(path,ink);ink.setShader(null);
        // Cuff.
        ink.setColor(0xff1d3f6e);
        box.set(left-mittW*.22f,y-mittH*.34f,left+mittW*.18f,y+mittH*.36f);c.drawRoundRect(box,size*.08f,size*.08f,ink);
        // Mitt: corner blue with a light along the top.
        ink.setShader(new LinearGradient(0,y-mittH/2f,0,y+mittH/2f,new int[]{0xff89bced,0xff2f6fb5,0xff163a66},
                new float[]{0,.45f,1},Shader.TileMode.CLAMP));
        box.set(left,y-mittH/2f,x,y+mittH/2f);c.drawRoundRect(box,mittW*.42f,mittH*.46f,ink);ink.setShader(null);
        // Thumb along the top edge.
        ink.setColor(0xff2a62a3);
        box.set(left+mittW*.18f,y-mittH*.62f,left+mittW*.7f,y-mittH*.22f);c.drawRoundRect(box,size*.2f,size*.2f,ink);
        // Lace line and a white logo patch on the cuff.
        ink.setStyle(Paint.Style.STROKE);ink.setStrokeWidth(Math.max(1f,size*.03f));ink.setColor(0xccf2e6ca);
        c.drawLine(left-mittW*.16f,y-mittH*.12f,left+mittW*.12f,y-mittH*.12f,ink);
        c.drawLine(left-mittW*.16f,y+mittH*.1f,left+mittW*.12f,y+mittH*.1f,ink);
        ink.setStyle(Paint.Style.FILL);
        // Contact flash at full reach.
        if(reach>.92f){
            ink.setShader(new RadialGradient(x,y,size*.7f,0x90fff8e9,0x00fff8e9,Shader.TileMode.CLAMP));
            c.drawCircle(x,y,size*.7f,ink);ink.setShader(null);
        }
    }

    private void loading(Canvas c,int w,float y,float unit,long t){
        float size=Math.max(dp(18),unit*.085f);
        ink.setStyle(Paint.Style.FILL);ink.setTypeface(stencil);ink.setTextAlign(Paint.Align.CENTER);
        ink.setTextSize(size);ink.setLetterSpacing(.14f);
        String word=title.toUpperCase(java.util.Locale.ROOT);
        float tw=ink.measureText(word),chev=size*.62f*3,tx=w/2f-chev/2f;
        ink.setColor(GameInk.GOLD);
        c.drawText(word,tx,y,ink);
        // A light sweep across the letters every few seconds.
        if(t>0){
            float phase=(t%3400)/1400f;
            if(phase<1f){
                float sx=tx-tw/2f-size+phase*(tw+size*2);
                ink.setShader(new LinearGradient(sx-size,0,sx+size,0,new int[]{0x00fff8e9,0xd0fff8e9,0x00fff8e9},null,Shader.TileMode.CLAMP));
                c.drawText(word,tx,y,ink);ink.setShader(null);
            }
        }
        // Chevrons, as on the transition plate.
        ink.setLetterSpacing(0f);ink.setColor(GameInk.GOLD);
        float cx=tx+tw/2f+size*.55f,ch=size*.34f,base=y;
        for(int k=0;k<3;k++){
            float pulse=t<=0?.7f:.25f+.75f*Math.max(0f,1f-Math.abs(((t%900)/900f)*3f-k-.5f));
            ink.setAlpha(Math.round(255*pulse));
            float x0=cx+k*size*.48f;
            path.reset();path.moveTo(x0,base-ch*2.2f);path.lineTo(x0+ch,base-ch*1.1f);path.lineTo(x0,base);
            path.lineTo(x0-ch*.55f,base);path.lineTo(x0+ch*.45f,base-ch*1.1f);path.lineTo(x0-ch*.55f,base-ch*2.2f);path.close();
            c.drawPath(path,ink);
        }
        ink.setAlpha(255);
        // Rule under the word.
        ink.setColor(0xffaa642a);
        float rw=Math.min(w*.7f,tw+chev+size*2);
        c.drawRect(w/2f-rw/2f,y+size*.38f,w/2f+rw/2f,y+size*.38f+Math.max(1f,dp(1.5f)),ink);
    }

    private void tipLine(Canvas c,int w,int h,float y,long elapsed){
        int index=tip(elapsed,firstTip);
        float alpha=moving() || frozenAt>=0?tipAlpha(elapsed):1f;
        float size=Math.max(dp(12),Math.min(w,h)*.042f);
        ink.setTypeface(stencil);ink.setTextAlign(Paint.Align.CENTER);ink.setTextSize(size*.82f);ink.setLetterSpacing(.12f);
        ink.setColor(0xffaa642a);ink.setAlpha(Math.round(255*alpha));
        float tipY=y+size*2.4f;
        c.drawText("CORNER TIP",w/2f,tipY,ink);
        ink.setTypeface(body);ink.setTextSize(size);ink.setLetterSpacing(0f);
        ink.setColor(GameInk.TEXT);ink.setAlpha(Math.round(235*alpha));
        // Wrap to at most two lines inside 86% of the width.
        String[] words=TIPS[index].split(" ");
        StringBuilder line=new StringBuilder();float max=w*.86f;float ly=tipY+size*1.55f;int lines=0;
        for(String word:words){
            String next=line.length()==0?word:line+" "+word;
            if(ink.measureText(next)>max && line.length()>0){
                c.drawText(line.toString(),w/2f,ly,ink);ly+=size*1.35f;line=new StringBuilder(word);
                if(++lines==2)break;
            }else line=new StringBuilder(next);
        }
        if(lines<2 && line.length()>0)c.drawText(line.toString(),w/2f,ly,ink);
        ink.setAlpha(255);
    }
}
