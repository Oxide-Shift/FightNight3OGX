package org.oxideshift.corner;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.*;
import android.os.SystemClock;
import android.view.View;

/** Play Now lower screens built from the highlighted boxers' roster rows.
 *  TAPE (boxer select): a tale of the tape. Blue and red nameplates with record and weight class, height, reach,
 *  weight and knockout rate, then the eight retail ratings as mirrored bars racing out from the centre, the better
 *  side lit in its corner colour. A side that changes boxer slides its plate in and re-runs its bars.
 *  POSTER (venue select): tonight's fight poster for the chosen pair over a ring in perspective, with sweeping
 *  spotlights and flash bulbs in the crowd.
 *  Everything is drawn here; nothing comes from the disc. Still with animations off. */
final class PlayNowStage extends View {
    enum Mode {TAPE, POSTER, CORNER}
    static final String[] RATINGS={"POWER","SPEED","AGILITY","STAMINA","CHIN","HEART","CUTS","BODY"};
    /** Roster columns: height (in), weight (lb), reach (in), first rating (left power; right power follows). */
    static final int HEIGHT=11,WEIGHT=12,REACH=30,RATING0=16;
    static final long ENTER_MS=760;

    private final Paint ink=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path=new Path();
    private final RectF box=new RectF();
    private final Typeface stencil,body;
    private final float dp;
    private Mode mode=Mode.TAPE;
    private PlayNowPick pick;
    private String tip="";
    private final long[] changedAt={0,0};
    private long modeAt;

    PlayNowStage(Context c,DiscFonts fonts){
        super(c);stencil=fonts.stencil;body=fonts.body;dp=getResources().getDisplayMetrics().density;
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
    }

    /** Pure helpers, for tests. */
    static int rating(PlayNowPick p,int side,int k){
        if(p==null || k<0 || k>=RATINGS.length)return -1;
        if(k==0){int l=p.cell(side,RATING0),r=p.cell(side,RATING0+1);return l<0 || r<0 || l>100 || r>100?-1:(l+r)/2;}
        int v=p.cell(side,RATING0+k+1);return v<0 || v>100?-1:v;
    }
    static String feet(int inches){return inches<=0 || inches>100?"—":(inches/12)+"′"+(inches%12)+"″";}
    static String record(PlayNowPick p,int side){
        int w=p.wins(side),l=p.losses(side),d=p.draws(side),k=p.knockouts(side);
        if(w<0 || l<0 || d<0)return "";
        return w+"-"+l+"-"+d+(k>=0?"  ·  "+k+" KO":"");
    }
    /** How many of the eight ratings each side wins (ties count for neither). */
    static int[] edges(PlayNowPick p){
        int[] won=new int[2];
        for(int k=0;k<RATINGS.length;k++){int a=rating(p,0,k),b=rating(p,1,k);if(a<0 || b<0 || a==b)continue;won[a>b?0:1]++;}
        return won;
    }
    static String last(String name){if(name==null)return "";int s=name.lastIndexOf(' ');return s<0?name:name.substring(s+1);}
    static String first(String name){if(name==null)return "";int s=name.lastIndexOf(' ');return s<0?"":name.substring(0,s);}

    private String venue;private long venueAt;
    void show(Mode next,PlayNowPick p,String note){show(next,p,note,null);}
    void show(Mode next,PlayNowPick p,String note,String venueName){
        if(venueName==null?venue!=null:!venueName.equals(venue)){
            venue=venueName;venueAt=SystemClock.uptimeMillis();invalidate();
            if(next==Mode.POSTER && venueName!=null)setContentDescription("Pick your ring. Live from "+venueName+".");
        }
        long now=SystemClock.uptimeMillis();
        if(next!=mode || modeAt==0){modeAt=now;changedAt[0]=changedAt[1]=now;}
        for(int side=0;side<2;side++)
            if(p!=null && (pick==null || p.id[side]!=pick.id[side]))changedAt[side]=now;
        // Corner select: where the controller sits (-1 blue, 0 undecided, +1 red); the chip glides there.
        int seat=p==null?0:p.padIn(0)>=0?-1:p.padIn(1)>=0?1:0;
        if(seat!=seatTarget || modeAt==now){seatFrom=seatNow(now);seatTarget=seat;seatAt=now;}
        boolean same=next==mode && p==pick && (note==null?"":note).equals(tip);
        mode=next;pick=p;tip=note==null?"":note;
        if(same)return;
        if(p!=null && p.has(0) && p.has(1)){
            int[] e=edges(p);
            setContentDescription((mode==Mode.TAPE?"Tale of the tape. ":"Tonight. ")+p.name[0]+", "+record(p,0)+", versus "+p.name[1]+", "+record(p,1)
                    +". "+p.name[0]+" leads "+e[0]+" ratings, "+p.name[1]+" leads "+e[1]+".");
        }else setContentDescription(mode==Mode.TAPE?"Tale of the tape":"Pick your ring");
        if(mode==Mode.CORNER)setContentDescription(seatTarget<0?"Select corner. You are in the blue corner.":
                seatTarget>0?"Select corner. You are in the red corner.":"Select corner. Move left for blue or right for red.");
        invalidate();
    }
    private int seatTarget;private float seatFrom;private long seatAt;
    /** The controller chip's position, -1..1, overshooting slightly as it lands. */
    private float seatNow(long now){
        if(!moving())return seatTarget;
        float t=Math.min(1f,(now-seatAt)/420f);
        return seatFrom+(seatTarget-seatFrom)*Math.min(1.08f,FightRecordStage.overshoot(t));
    }
    private static boolean moving(){return ValueAnimator.areAnimatorsEnabled();}
    /** 0..1 since a side's boxer changed. */
    private float since(int side,long now){return moving()?Math.max(0f,Math.min(1f,(now-changedAt[side])/(float)ENTER_MS)):1f;}
    private static float ease(float t){return 1f-(1f-t)*(1f-t)*(1f-t);}
    private static float stagger(float f,float delay,float span){return Math.max(0f,Math.min(1f,(f-delay)/span));}
    private void text(Canvas c,String s,float x,float y,Typeface face,float size,int color,Paint.Align align){
        ink.setStyle(Paint.Style.FILL);ink.setShader(null);ink.setTypeface(face);ink.setTextSize(size*dp);ink.setColor(color);ink.setTextAlign(align);
        c.drawText(s,x,y,ink);
    }
    private float fit(String s,Typeface face,float size,float width){
        ink.setTypeface(face);ink.setTextSize(size*dp);float span=ink.measureText(s);
        return span>width && span>0?size*width/span:size;
    }
    private static int alpha(int color,float a){return (Math.round(255*Math.max(0f,Math.min(1f,a)))<<24)|(color&0xffffff);}

    @Override protected void onDraw(Canvas c){
        float w=getWidth(),h=getHeight();if(w<=0 || h<=0)return;
        long now=SystemClock.uptimeMillis();
        boolean live=moving() && isShown();
        if(mode==Mode.TAPE)tape(c,w,h,now,live);else if(mode==Mode.CORNER)corner(c,w,h,now,live);else poster(c,w,h,now,live);
        if(live)postInvalidateOnAnimation();
    }

    // ---------------------------------------------------------------- tale of the tape
    private void tape(Canvas c,float w,float h,long now,boolean live){
        ink.setColor(0xff000000);
        ink.setShader(new RadialGradient(w*.5f,h*.35f,Math.max(w,h)*.8f,0xff2e140c,0xff0e0605,Shader.TileMode.CLAMP));
        c.drawRect(0,0,w,h,ink);ink.setShader(null);
        // Corner glows: blue light from the left, red from the right, breathing slowly.
        float breathe=live?.5f+.5f*(float)Math.sin(now/1300.0):.5f;
        for(int side=0;side<2;side++){
            int tint=side==0?GameInk.BLUE_CORNER:GameInk.RED_CORNER;
            float gx=side==0?0:w;
            ink.setShader(new RadialGradient(gx,h*.3f,w*.6f,alpha(tint,.16f+.06f*breathe),tint&0xffffff,Shader.TileMode.CLAMP));
            c.drawRect(0,0,w,h,ink);ink.setShader(null);
        }
        if(live)beams(c,w,h,now);
        float pad=12*dp,cx=w/2f;
        PlayNowPick p=pick;
        if(p==null || !p.has(0) || !p.has(1)){
            text(c,"CHOOSE YOUR BOXERS",cx,h*.42f,stencil,24,GameInk.TEXT,Paint.Align.CENTER);
            text(c,"Their tale of the tape appears here.",cx,h*.42f+24*dp,body,14,GameInk.MUTED,Paint.Align.CENTER);
            return;
        }
        float[] a={since(0,now),since(1,now)};
        // Nameplates.
        float plateH=Math.min(78*dp,h*.2f),plateW=cx-pad-30*dp,top=pad*.6f;
        for(int side=0;side<2;side++)plate(c,side,side==0?pad:cx+30*dp,top,plateW,plateH,p,a[side],w);
        // VS medallion between the plates; it punches up when either side changes.
        float kick=Math.max(1f-a[0],1f-a[1]);
        float vr=24*dp*(1f+.22f*kick*kick*(float)Math.sin(Math.PI*Math.min(1f,(1f-kick)*2.2f+.01f))),vy=top+plateH/2f;
        ink.setStyle(Paint.Style.FILL);
        ink.setShader(new RadialGradient(cx,vy,vr*1.9f,0x66f6bf55,0x00f6bf55,Shader.TileMode.CLAMP));c.drawCircle(cx,vy,vr*1.9f,ink);ink.setShader(null);
        ink.setColor(0xff1a0b07);c.drawCircle(cx,vy,vr,ink);
        ink.setStyle(Paint.Style.STROKE);ink.setStrokeWidth(2.5f*dp);ink.setColor(GameInk.GOLD);c.drawCircle(cx,vy,vr,ink);
        ink.setStyle(Paint.Style.FILL);
        c.save();c.rotate(-8,cx,vy);
        text(c,"VS",cx,vy+8*dp*(vr/(24*dp)),stencil,22*(vr/(24*dp)),GameInk.GOLD,Paint.Align.CENTER);
        c.restore();
        // Physical tape: height, reach, weight, knockout rate.
        float y=top+plateH+14*dp;
        String[] labels={"HEIGHT","REACH","WEIGHT","KO RATE"};
        String[][] values=new String[2][4];
        for(int side=0;side<2;side++){
            int reach=p.cell(side,REACH),wt=p.cell(side,WEIGHT),ko=p.knockoutRate(side);
            values[side]=new String[]{feet(p.cell(side,HEIGHT)),reach>=50 && reach<100?reach+"″":"—",wt>0?wt+" lb":"—",ko>=0?ko+"%":"—"};
        }
        int[][] raw={{p.cell(0,HEIGHT),p.cell(1,HEIGHT)},{p.cell(0,REACH),p.cell(1,REACH)},{p.cell(0,WEIGHT),p.cell(1,WEIGHT)},{p.knockoutRate(0),p.knockoutRate(1)}};
        float rowT=Math.min(19*dp,h*.05f);
        for(int i=0;i<4;i++){
            float ry=y+i*rowT+rowT*.72f;
            ink.setColor(i%2==0?0x22ffffff:0x10ffffff);c.drawRect(pad,y+i*rowT,w-pad,y+(i+1)*rowT,ink);
            text(c,labels[i],cx,ry,stencil,10.5f,GameInk.MUTED,Paint.Align.CENTER);
            for(int side=0;side<2;side++){
                float in=ease(stagger(a[side],.15f+i*.05f,.4f));if(in<=0f)continue;
                boolean edge=raw[i][side]>raw[i][1-side] && raw[i][1-side]>0;
                float x=side==0?cx-46*dp-(1f-in)*30*dp:cx+46*dp+(1f-in)*30*dp;
                text(c,values[side][i],x,ry,stencil,13,alpha(edge?GameInk.GOLD:GameInk.TEXT,in),side==0?Paint.Align.RIGHT:Paint.Align.LEFT);
            }
        }
        y+=4*rowT+12*dp;
        // Ratings: mirrored bars from the centre line.
        int[] won=edges(p);
        float footer=26*dp,rowR=Math.max(14*dp,Math.min(25*dp,(h-y-footer)/RATINGS.length)),gap=34*dp,maxLen=cx-pad-gap-30*dp;
        text(c,"RATINGS",cx,y-2*dp,stencil,10,GameInk.GOLD,Paint.Align.CENTER);
        for(int k=0;k<RATINGS.length;k++){
            float ry=y+k*rowR+2*dp,bh=Math.max(5*dp,rowR*.46f),mid=ry+rowR*.5f;
            text(c,RATINGS[k],cx,mid+3.6f*dp,stencil,9.5f,GameInk.MUTED,Paint.Align.CENTER);
            int va=rating(p,0,k),vb=rating(p,1,k);
            for(int side=0;side<2;side++){
                int v=side==0?va:vb,o=side==0?vb:va;
                float g=stagger(a[side],.25f+k*.055f,.45f);
                float grow=g<=0f?0f:Math.min(1.05f,FightRecordStage.overshoot(g));
                float len=Math.max(0,v)/100f*maxLen*grow;
                boolean better=v>o && o>=0;
                int tint=side==0?0xff5aa6ff:0xffff5a44;
                float x0=side==0?cx-gap:cx+gap,x1=side==0?x0-len:x0+len;
                ink.setColor(0x26ffffff);
                if(side==0)c.drawRect(x0-maxLen,mid-bh/2f,x0,mid+bh/2f,ink);else c.drawRect(x0,mid-bh/2f,x0+maxLen,mid+bh/2f,ink);
                if(len>0){
                    int lit=better?tint:MenuArt.Switch.blend(tint,0xff606060,.55f);
                    ink.setShader(new LinearGradient(x0,0,x1,0,MenuArt.Switch.blend(lit,0xff000000,.5f),lit,Shader.TileMode.CLAMP));
                    c.drawRect(Math.min(x0,x1),mid-bh/2f,Math.max(x0,x1),mid+bh/2f,ink);ink.setShader(null);
                    // A sheen along the top edge.
                    ink.setColor(better?0x66ffffff:0x22ffffff);c.drawRect(Math.min(x0,x1),mid-bh/2f,Math.max(x0,x1),mid-bh/2f+Math.max(1f,bh*.22f),ink);
                    if(better && g>=1f){
                        // The winning side's tip carries a gold spark that glints now and then.
                        float glint=live?(float)Math.max(0,Math.sin(now/420.0+k*.9)):0f;
                        ink.setColor(alpha(0xfffff3d6,.55f+.45f*glint));c.drawRect(side==0?x1:x1-2.5f*dp,mid-bh/2f-1*dp,side==0?x1+2.5f*dp:x1,mid+bh/2f+1*dp,ink);
                    }
                }
                if(g>0f)text(c,v<0?"—":Integer.toString(Math.round(Math.max(0,v)*Math.min(1f,grow))),side==0?x1-5*dp:x1+5*dp,mid+4*dp,stencil,11.5f,
                        better?GameInk.GOLD:GameInk.TEXT,side==0?Paint.Align.RIGHT:Paint.Align.LEFT);
            }
        }
        // Who has the edge.
        float fy=h-8*dp,done=Math.min(a[0],a[1]);
        String verdict=won[0]==won[1]?"DEAD EVEN ON THE RATINGS":
                (last(p.name[won[0]>won[1]?0:1])+" LEADS "+Math.max(won[0],won[1])+" OF "+RATINGS.length).toUpperCase(java.util.Locale.ROOT);
        text(c,verdict,cx,fy,stencil,fit(verdict,stencil,13,w-2*pad),alpha(GameInk.GOLD,ease(stagger(done,.7f,.3f))),Paint.Align.CENTER);
    }
    private void plate(Canvas c,int side,float x,float y,float pw,float ph,PlayNowPick p,float a,float w){
        float slide=(1f-ease(a))*(w*.55f)*(side==0?-1:1);
        c.save();c.translate(slide,0);
        int tint=side==0?GameInk.BLUE_CORNER:GameInk.RED_CORNER;
        box.set(x,y,x+pw,y+ph);
        ink.setStyle(Paint.Style.FILL);ink.setColor(0xf01b0c08);c.drawRoundRect(box,6*dp,6*dp,ink);
        ink.setColor(tint);c.drawRect(x,y,x+pw,y+3*dp,ink);
        // Slanted corner tab with the corner name.
        path.reset();float tx=side==0?x:x+pw;float dir=side==0?1:-1;
        path.moveTo(tx,y+3*dp);path.lineTo(tx+dir*78*dp,y+3*dp);path.lineTo(tx+dir*70*dp,y+17*dp);path.lineTo(tx,y+17*dp);path.close();
        ink.setColor(MenuArt.Switch.blend(tint,0xff000000,.35f));c.drawPath(path,ink);
        Paint.Align align=side==0?Paint.Align.LEFT:Paint.Align.RIGHT;
        float ix=side==0?x+8*dp:x+pw-8*dp;
        text(c,side==0?"BLUE CORNER":"RED CORNER",ix,y+14*dp,stencil,9,0xffffe8c1,align);
        String fn=first(p.name[side]).toUpperCase(java.util.Locale.ROOT),ln=last(p.name[side]).toUpperCase(java.util.Locale.ROOT);
        // Sizes follow the plate's height so a short lower screen never stacks the names on top of each other.
        float room=ph-17*dp-16*dp,firstSize=Math.min(12,room*.22f/dp),lastSize=Math.min(26,room*.52f/dp);
        float firstBase=y+17*dp+firstSize*dp*1.05f,lastBase=firstBase+lastSize*dp*.95f;
        text(c,fn,ix,firstBase,stencil,fit(fn,stencil,firstSize,pw-16*dp),GameInk.MUTED,align);
        // The surname stamps down after the plate lands.
        float stamp=a>=1f?1f:1f+.4f*(1f-ease(stagger(a,.35f,.5f)));
        float size=fit(ln,stencil,lastSize,pw-16*dp);
        c.save();c.scale(stamp,stamp,ix,lastBase-size*dp*.4f);
        text(c,ln,ix,lastBase,stencil,size,GameInk.TEXT,align);
        c.restore();
        // Weight class is not shown: a boxer can be entered in more than one, and the game keeps that choice outside
        // the roster row (Holyfield reads light heavy or heavyweight depending on how he was reached).
        int bouts=Math.max(0,p.wins(side))+Math.max(0,p.losses(side))+Math.max(0,p.draws(side));
        String detail=record(p,side),cls=p.wins(side)<0?"":bouts+(bouts==1?" BOUT":" BOUTS");
        text(c,detail,ix,y+ph-7*dp,stencil,fit(detail,stencil,12,pw*.6f),GameInk.GOLD,align);
        text(c,cls,side==0?x+pw-8*dp:x+8*dp,y+ph-7*dp,stencil,fit(cls,stencil,9,pw*.38f),GameInk.MUTED,side==0?Paint.Align.RIGHT:Paint.Align.LEFT);
        c.restore();
    }
    private void beams(Canvas c,float w,float h,long t){
        for(int k=0;k<2;k++){
            float phase=(float)((t/(k==0?11000.0:15000.0)+k*.41)%1.0);
            float x=-w*.3f+phase*w*1.6f,spread=w*.16f;
            path.reset();path.moveTo(x-spread*.12f,0);path.lineTo(x+spread*.12f,0);path.lineTo(x+spread,h);path.lineTo(x-spread,h);path.close();
            ink.setShader(new LinearGradient(0,0,0,h,0x18ffd8a0,0x00ffd8a0,Shader.TileMode.CLAMP));
            c.drawPath(path,ink);ink.setShader(null);
        }
    }

    // ---------------------------------------------------------------- corner select
    /** Blue and red halves, each with its corner post, ropes, stool and what that corner means on the fight screen.
     *  The corner the controller takes (on the top screen) lights up and reads Player 1; the other dims. */
    private void corner(Canvas c,float w,float h,long now,boolean live){
        float cx=w/2f,pad=12*dp,seat=seatNow(now);
        float enter=moving()?ease(Math.max(0f,Math.min(1f,(now-modeAt)/700f))):1f;
        for(int side=0;side<2;side++){
            int tint=side==0?GameInk.BLUE_CORNER:GameInk.RED_CORNER;
            // How much this half is "taken": 1 when the chip sits here, 0 on the far side.
            float taken=Math.max(0f,Math.min(1f,side==0?-seat:seat));
            float x0=side==0?0:cx,x1=side==0?cx:w;
            float slide=(1f-enter)*cx*(side==0?-1:1);
            c.save();c.translate(slide,0);c.clipRect(x0,0,x1,h);
            int base=MenuArt.Switch.blend(tint,0xff000000,.82f-.22f*taken);
            ink.setColor(0xff000000);
            ink.setShader(new RadialGradient(side==0?x0+cx*.35f:x1-cx*.35f,h*.42f,cx*1.1f,MenuArt.Switch.blend(tint,0xff000000,.55f-.25f*taken),base,Shader.TileMode.CLAMP));
            c.drawRect(x0,0,x1,h,ink);ink.setShader(null);
            // A spotlight drops on the taken corner.
            if(taken>0f){
                float bx=side==0?x0+cx*.42f:x1-cx*.42f,breathe=live?.85f+.15f*(float)Math.sin(now/700.0):1f;
                path.reset();path.moveTo(bx-6*dp,0);path.lineTo(bx+6*dp,0);path.lineTo(bx+cx*.42f,h*.78f);path.lineTo(bx-cx*.42f,h*.78f);path.close();
                ink.setShader(new LinearGradient(0,0,0,h*.78f,alpha(0xffffe8c0,.26f*taken*breathe),0x00ffe8c0,Shader.TileMode.CLAMP));
                c.drawPath(path,ink);ink.setShader(null);
            }
            cornerArt(c,side,x0,x1,h,tint,taken,now,live);
            // Name and meaning.
            float tx=side==0?x0+pad:x1-pad;Paint.Align align=side==0?Paint.Align.LEFT:Paint.Align.RIGHT;
            float lift=taken*taken*4*dp;
            text(c,side==0?"BLUE":"RED",tx,pad+26*dp-lift,stencil,30,alpha(MenuArt.Switch.blend(tint,0xffffffff,.35f),.55f+.45f*taken),align);
            text(c,"CORNER",tx,pad+44*dp-lift,stencil,15,alpha(GameInk.TEXT,.5f+.5f*taken),align);
            String[] lines=side==0?new String[]{"Your health and stamina","on the LEFT of the fight screen"}
                    :new String[]{"Your health and stamina","on the RIGHT of the fight screen"};
            for(int i=0;i<lines.length;i++)
                text(c,lines[i],tx,pad+64*dp+i*16*dp,body,12.5f,alpha(i==1?GameInk.GOLD:GameInk.MUTED,.45f+.55f*taken),align);
            // Who fights from here.
            String who=taken>.5f?"PLAYER 1":"COMPUTER";
            float wy=h-pad-4*dp;
            ink.setStyle(Paint.Style.FILL);
            text(c,who,tx,wy,stencil,13,alpha(taken>.5f?GameInk.GOLD:GameInk.MUTED,.6f+.4f*taken),align);
            c.restore();
        }
        // Centre seam: a gold rule with a sheen running down it.
        ink.setColor(0xff120806);c.drawRect(cx-2*dp,0,cx+2*dp,h,ink);
        float sheen=live?((now%2600)/2600f)*h*1.4f-h*.2f:-h;
        ink.setShader(new LinearGradient(0,sheen-40*dp,0,sheen+40*dp,new int[]{0x00f6bf55,0xc0f6bf55,0x00f6bf55},null,Shader.TileMode.CLAMP));
        c.drawRect(cx-1.5f*dp,0,cx+1.5f*dp,h,ink);ink.setShader(null);
        // The top screen already moves the device icon between corners, so this screen only answers with light.
    }
    /** A padded corner post in the corner colour with three ropes running toward the centre, and a stool. */
    private void cornerArt(Canvas c,int side,float x0,float x1,float h,int tint,float taken,long now,boolean live){
        float dir=side==0?1:-1,postX=side==0?x0+(x1-x0)*.16f:x1-(x1-x0)*.16f;
        float top=h*.3f,bottom=h*.86f,postW=9*dp;
        // Ropes: red, white, blue, sagging slightly and swaying when this corner is taken.
        int[] ropes={0xffc23a2a,0xffeee4d4,0xff2f6fb5};
        float sway=live?(float)Math.sin(now/650.0+side)*2.5f*dp*taken:0f;
        ink.setStyle(Paint.Style.STROKE);ink.setStrokeCap(Paint.Cap.ROUND);
        for(int r=0;r<3;r++){
            float ry=top+(bottom-top)*(.12f+r*.2f),ex=side==0?x1+2*dp:x0-2*dp,ey=ry+(bottom-top)*.18f;
            ink.setStrokeWidth(3.2f*dp);ink.setColor(alpha(ropes[r],.55f+.45f*taken));
            path.reset();path.moveTo(postX,ry);path.quadTo((postX+ex)/2f,(ry+ey)/2f+6*dp+sway,ex,ey);c.drawPath(path,ink);
        }
        ink.setStyle(Paint.Style.FILL);
        // Post with the corner pad.
        ink.setColor(0xff3a3530);c.drawRect(postX-postW*.4f,top-6*dp,postX+postW*.4f,bottom,ink);
        float glow=taken*(live?.8f+.2f*(float)Math.sin(now/500.0):1f);
        if(glow>0f){ink.setShader(new RadialGradient(postX,top+(bottom-top)*.3f,40*dp,alpha(tint,.45f*glow),tint&0xffffff,Shader.TileMode.CLAMP));c.drawCircle(postX,top+(bottom-top)*.3f,40*dp,ink);ink.setShader(null);}
        box.set(postX-postW,top,postX+postW,top+(bottom-top)*.62f);
        ink.setShader(new LinearGradient(box.left,0,box.right,0,MenuArt.Switch.blend(tint,0xff000000,.4f),MenuArt.Switch.blend(tint,0xffffffff,.2f),Shader.TileMode.MIRROR));
        c.drawRoundRect(box,postW*.6f,postW*.6f,ink);ink.setShader(null);
        ink.setColor(0x55ffffff);c.drawRect(box.left+postW*.35f,box.top+3*dp,box.left+postW*.6f,box.bottom-3*dp,ink);
        // Stool and bucket at the foot of the post.
        float sx=postX+dir*30*dp,sy=bottom-2*dp;
        ink.setColor(0xff4a2a1a);c.drawRoundRect(sx-14*dp,sy-24*dp,sx+14*dp,sy-18*dp,3*dp,3*dp,ink);
        ink.setStrokeWidth(2.5f*dp);ink.setStyle(Paint.Style.STROKE);
        c.drawLine(sx-10*dp,sy-18*dp,sx-13*dp,sy,ink);c.drawLine(sx+10*dp,sy-18*dp,sx+13*dp,sy,ink);
        ink.setStyle(Paint.Style.FILL);
        float bx=sx+dir*26*dp;
        path.reset();path.moveTo(bx-8*dp,sy-16*dp);path.lineTo(bx+8*dp,sy-16*dp);path.lineTo(bx+6*dp,sy);path.lineTo(bx-6*dp,sy);path.close();
        ink.setColor(0xff8a8a8a);c.drawPath(path,ink);ink.setColor(0xffb0b0b0);c.drawRect(bx-8*dp,sy-17*dp,bx+8*dp,sy-14.5f*dp,ink);
        // Canvas edge.
        ink.setColor(0x40000000);c.drawRect(x0,bottom,x1,h,ink);
    }
    // ---------------------------------------------------------------- venue poster
    private void poster(Canvas c,float w,float h,long now,boolean live){
        float a=moving()?Math.max(0f,Math.min(1f,(now-modeAt)/900f)):1f;
        // Night sky over the arena, warm at the ring.
        ink.setColor(0xff000000);
        ink.setShader(new LinearGradient(0,0,0,h,new int[]{0xff0c0505,0xff2a100a,0xff4a1a0e},new float[]{0f,.55f,1f},Shader.TileMode.CLAMP));
        c.drawRect(0,0,w,h,ink);ink.setShader(null);
        float cx=w/2f,pad=12*dp;
        // Billing: PLAY NOW banner, names stacked with VS, records.
        float banner=26*dp;
        ink.setColor(0xffaf3927);c.drawRect(0,pad*.4f,w,pad*.4f+banner,ink);
        ink.setColor(GameInk.ORANGE);c.drawRect(0,pad*.4f+banner,w,pad*.4f+banner+2*dp,ink);
        text(c,"PLAY NOW  ·  TONIGHT",cx,pad*.4f+banner*.7f,stencil,14,0xffffe8c1,Paint.Align.CENTER);
        PlayNowPick p=pick;
        boolean known=p!=null && p.has(0) && p.has(1);
        String blue=known?last(p.name[0]).toUpperCase(java.util.Locale.ROOT):"BLUE CORNER",red=known?last(p.name[1]).toUpperCase(java.util.Locale.ROOT):"RED CORNER";
        float nameY=pad+banner+44*dp,nameSize=Math.min(40,fit(blue.length()>red.length()?blue:red,stencil,40,w*.4f));
        for(int side=0;side<2;side++){
            float in=ease(stagger(a,.1f+side*.12f,.45f));if(in<=0f)continue;
            float s=1f+.5f*(1f-in),x=side==0?cx-26*dp:cx+26*dp;
            c.save();c.scale(s,s,x,nameY-12*dp);
            ink.setShadowLayer(4*dp,0,2*dp,0xff000000);
            text(c,side==0?blue:red,x,nameY,stencil,nameSize,alpha(side==0?0xff9ccaf5:0xffff9a8a,in),side==0?Paint.Align.RIGHT:Paint.Align.LEFT);
            ink.clearShadowLayer();
            c.restore();
            if(known)text(c,record(p,side),x,nameY+18*dp,stencil,11,alpha(GameInk.GOLD,in),side==0?Paint.Align.RIGHT:Paint.Align.LEFT);
        }
        float vs=ease(stagger(a,.32f,.35f));
        if(vs>0f){c.save();c.scale(1.6f-.6f*vs,1.6f-.6f*vs,cx,nameY-12*dp);text(c,"VS",cx,nameY-4*dp,stencil,18,alpha(GameInk.GOLD,vs),Paint.Align.CENTER);c.restore();}
        if(known){
            int wa=p.cell(0,WEIGHT),wb=p.cell(1,WEIGHT);
            String classes=wa>0 && wb>0?wa+" LB  ·  "+wb+" LB":"";
            text(c,classes,cx,nameY+38*dp,stencil,fit(classes,stencil,11,w-2*pad),alpha(GameInk.MUTED,vs),Paint.Align.CENTER);
        }
        // The ring, with the highlighted venue billed over it ("LIVE FROM ..."), sliding in whenever it changes.
        float ringTop=nameY+56*dp,tipH=46*dp,ringBottom=h-tipH;
        ring(c,w,ringTop,ringBottom,now,live,a);
        if(venue!=null){
            float in=moving()?ease(Math.max(0f,Math.min(1f,(now-venueAt)/520f))):1f;
            float vy=ringTop+20*dp,dx=(1f-in)*40*dp;
            String name=venue.toUpperCase(java.util.Locale.ROOT);
            text(c,"LIVE FROM",cx-dx,vy,stencil,10,alpha(GameInk.MUTED,in),Paint.Align.CENTER);
            ink.setShadowLayer(4*dp,0,2*dp,0xff000000);
            text(c,name,cx+dx,vy+22*dp,stencil,fit(name,stencil,22,w-2*pad),alpha(GameInk.GOLD,in),Paint.Align.CENTER);
            ink.clearShadowLayer();
        }
        // Prompt and tip.
        ink.setColor(0xe0140806);c.drawRect(0,h-tipH,w,h,ink);
        ink.setColor(GameInk.ORANGE);c.drawRect(0,h-tipH,w,h-tipH+1.5f*dp,ink);
        text(c,"PICK YOUR RING",pad,h-tipH+17*dp,stencil,12,GameInk.GOLD,Paint.Align.LEFT);
        String note=tip;
        float size=fit(note,body,13,w-2*pad);
        text(c,note,pad,h-tipH+35*dp,body,Math.max(10.5f,size),GameInk.TEXT,Paint.Align.LEFT);
    }
    private void ring(Canvas c,float w,float top,float bottom,long now,boolean live,float a){
        float cx=w/2f,hgt=bottom-top;if(hgt<40*dp)return;
        // Crowd: rows of heads in silhouette with flash bulbs popping.
        float crowdY=top+hgt*.18f;
        // Arena rig lights along the top, then the crowd, each row nearer and warmer, rim-lit by the ring.
        for(int i=0;i<9;i++){
            float lx=w*(i+.5f)/9f,ly=top+hgt*.04f;
            ink.setShader(new RadialGradient(lx,ly,7*dp,0xccffe2b0,0x00ffe2b0,Shader.TileMode.CLAMP));c.drawCircle(lx,ly,7*dp,ink);ink.setShader(null);
            ink.setColor(0xfffff0d0);c.drawCircle(lx,ly,1.6f*dp,ink);
        }
        int[] rows={0xff221009,0xff2e140b,0xff3a190d};
        for(int row=0;row<3;row++){
            float ry=crowdY+row*hgt*.07f,r=hgt*(.035f+row*.008f);
            ink.setColor(rows[row]);
            for(float x=-r+(row%2)*r;x<w+r;x+=r*1.9f)c.drawCircle(x,ry,r,ink);
            c.drawRect(0,ry,w,ry+hgt*.08f,ink);
            ink.setColor(0x30ff9a50);c.drawRect(0,ry+hgt*.075f,w,ry+hgt*.08f,ink);
        }
        if(live){
            for(int i=0;i<14;i++){
                long slot=(now/170+i*7)%37;
                if(slot>1)continue;
                float fx=(float)(((i*73+(now/1700)*31)%97)/97.0)*w,fy=crowdY+((i*29)%3)*hgt*.07f-hgt*.02f;
                float fade=slot==0?1f:.4f;
                ink.setShader(new RadialGradient(fx,fy,10*dp,alpha(0xfffff6e0,fade),0x00fff6e0,Shader.TileMode.CLAMP));
                c.drawCircle(fx,fy,10*dp,ink);ink.setShader(null);
            }
        }
        // Canvas in perspective with an apron.
        float backY=top+hgt*.42f,frontY=bottom-hgt*.08f,backW=w*.32f,frontW=w*.47f;
        float rise=ease(stagger(a,0f,.6f)),lift=(1f-rise)*hgt*.25f;
        c.save();c.translate(0,lift);
        path.reset();path.moveTo(cx-backW,backY);path.lineTo(cx+backW,backY);path.lineTo(cx+frontW,frontY);path.lineTo(cx-frontW,frontY);path.close();
        ink.setShader(new LinearGradient(0,backY,0,frontY,0xff8a7a68,0xffd8c8b0,Shader.TileMode.CLAMP));c.drawPath(path,ink);ink.setShader(null);
        ink.setColor(0xff2a1410);c.drawRect(cx-frontW,frontY,cx+frontW,frontY+hgt*.07f,ink);
        ink.setColor(GameInk.ORANGE);c.drawRect(cx-frontW,frontY+hgt*.02f,cx+frontW,frontY+hgt*.03f,ink);
        // Spotlight pools sweeping across the canvas.
        if(live){
            for(int k=0;k<2;k++){
                float sx=cx+(float)Math.sin(now/(k==0?1700.0:2300.0)+k*2)*backW*.7f,sy=(backY+frontY)/2f;
                ink.setShader(new RadialGradient(sx,sy,frontW*.45f,0x70fff3d6,0x00fff3d6,Shader.TileMode.CLAMP));
                c.save();c.scale(1f,.42f,sx,sy);c.drawCircle(sx,sy,frontW*.45f,ink);c.restore();ink.setShader(null);
            }
        }
        // Posts and ropes: back ropes first, the front ropes over the canvas.
        float backPost=hgt*.2f,frontPost=hgt*.3f;
        float[][] posts={{cx-backW,backY,backPost},{cx+backW,backY,backPost},{cx+frontW,frontY,frontPost},{cx-frontW,frontY,frontPost}};
        int[] ropes={0xffc23a2a,0xffeee4d4,0xff2f6fb5};
        float sway=live?(float)Math.sin(now/900.0)*1.2f*dp:0f;
        for(int pass=0;pass<2;pass++){
            for(int r=0;r<3;r++){
                ink.setStyle(Paint.Style.STROKE);ink.setStrokeWidth((pass==0?2f:3f)*dp);ink.setColor(ropes[r]);
                float f=(r+1)/4f;
                int[][] links=pass==0?new int[][]{{0,1},{1,2},{3,0}}:new int[][]{{3,2}};
                for(int[] l:links){
                    float[] p0=posts[l[0]],p1=posts[l[1]];
                    float y0=p0[1]-p0[2]*f,y1=p1[1]-p1[2]*f;
                    path.reset();path.moveTo(p0[0],y0);path.quadTo((p0[0]+p1[0])/2f,(y0+y1)/2f+sway+2*dp,p1[0],y1);c.drawPath(path,ink);
                }
            }
            ink.setStyle(Paint.Style.FILL);
            if(pass==0){
                for(int i=0;i<2;i++){float[] pp=posts[i];ink.setColor(0xff3a3530);c.drawRect(pp[0]-2*dp,pp[1]-pp[2],pp[0]+2*dp,pp[1],ink);
                    ink.setColor(i==0?GameInk.BLUE_CORNER:GameInk.RED_CORNER);c.drawRect(pp[0]-3.5f*dp,pp[1]-pp[2],pp[0]+3.5f*dp,pp[1]-pp[2]*.25f,ink);}
            }else{
                for(int i=2;i<4;i++){float[] pp=posts[i];ink.setColor(0xff4a443c);c.drawRect(pp[0]-3*dp,pp[1]-pp[2],pp[0]+3*dp,pp[1],ink);
                    ink.setColor(i==3?GameInk.BLUE_CORNER:GameInk.RED_CORNER);c.drawRect(pp[0]-5*dp,pp[1]-pp[2],pp[0]+5*dp,pp[1]-pp[2]*.22f,ink);}
            }
        }
        c.restore();
        // Light shafts from the rig above.
        if(live){
            for(int k=0;k<3;k++){
                float x=cx+(k-1)*w*.28f,spread=w*.1f;
                path.reset();path.moveTo(x-4*dp,top);path.lineTo(x+4*dp,top);path.lineTo(x+spread,frontY);path.lineTo(x-spread,frontY);path.close();
                float pulse=.6f+.4f*(float)Math.sin(now/1100.0+k);
                ink.setShader(new LinearGradient(0,top,0,frontY,alpha(0xffffd8a0,.12f*pulse),0x00ffd8a0,Shader.TileMode.CLAMP));
                c.drawPath(path,ink);ink.setShader(null);
            }
        }
    }
}
