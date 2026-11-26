package org.oxideshift.corner;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.*;
import android.view.View;
import java.util.Locale;

/** The bottom of the training sheet: an eight-axis radar of the drill's changes with its best gain and biggest
 *  cost, or a trainer quote's stamp and wallet gauge. Everything drawn comes from the game's own numbers;
 *  with nothing to show it keeps only the decorative tape and perforations. */
final class CampPoster extends View {
    static final String[] AXES={"POW","SPD","AGI","STA","CHN","HRT","CUT","BOD"};
    static final String[] NAMES={"Power","Speed","Agility","Stamina","Chin","Heart","Cuts","Body"};
    private final Paint ink=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path shape=new Path();
    private final Typeface stencil,body;
    private final float dp,sp;
    private final ValueAnimator grow=ValueAnimator.ofFloat(0f,1f);
    private float fraction=1f;
    private int[] deltas;
    private boolean result;
    private CareerDesk.Budget quote;

    CampPoster(Context context,DiscFonts fonts){
        super(context);
        stencil=fonts.stencil;body=fonts.body;
        dp=getResources().getDisplayMetrics().density;sp=getResources().getDisplayMetrics().scaledDensity;
        // A slight overshoot: the shape swells just past its value and settles, the stamp lands with a bounce.
        grow.setDuration(560);grow.setInterpolator(new android.view.animation.OvershootInterpolator(1.3f));
        grow.addUpdateListener(a->{fraction=(float)a.getAnimatedValue();invalidate();});
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
    }
    void showTraining(int[] values,boolean actual){
        boolean changed=values!=null && (deltas==null || !java.util.Arrays.equals(deltas,values));
        deltas=values==null?null:values.clone();result=actual;quote=null;
        setContentDescription(values==null?null:summary(values,actual));
        if(changed && isShown() && isAttachedToWindow() && ValueAnimator.areAnimatorsEnabled()){grow.cancel();fraction=0;grow.start();}
        invalidate();
    }
    private boolean store;
    void showQuote(CareerDesk.Budget budget){showQuote(budget,false);}
    /** A trainer quote, or (store) the fight store's price against the career wallet. */
    void showQuote(CareerDesk.Budget budget,boolean forStore){
        boolean changed=budget!=null && (quote==null || quote.cost!=budget.cost || quote.cash!=budget.cash || store!=forStore);
        store=forStore;
        deltas=null;quote=budget;grow.cancel();fraction=1;
        if(changed && isShown() && isAttachedToWindow() && ValueAnimator.areAnimatorsEnabled()){fraction=0;grow.start();}
        setContentDescription(budget==null?null:stamp(budget,forStore));
        invalidate();
    }
    @Override protected void onDetachedFromWindow(){grow.cancel();fraction=1;super.onDetachedFromWindow();}

    /** Index of the largest gain (or -1 when nothing rises) and of the largest cost (or -1 when nothing falls). */
    static int best(int[] v){int k=-1;for(int i=0;i<v.length;i++)if(v[i]>0 && (k<0 || v[i]>v[k]))k=i;return k;}
    static int worst(int[] v){int k=-1;for(int i=0;i<v.length;i++)if(v[i]<0 && (k<0 || v[i]<v[k]))k=i;return k;}
    static String signed(int n){return (n>0?"+":"")+n+"%";}
    static String summary(int[] v,boolean actual){
        int g=best(v),c=worst(v);
        return (actual?"Session result. ":"Forecast. ")+(g<0?"No gains":"Best gain "+NAMES[g]+" "+signed(v[g]))+". "+
                (c<0?"No costs":"Biggest cost "+NAMES[c]+" "+signed(v[c]))+".";
    }
    /** The quote's stamp: a fee above the wallet, a genuine zero fee, or a fee the wallet covers. */
    static String stamp(CareerDesk.Budget b){return stamp(b,false);}
    static String stamp(CareerDesk.Budget b,boolean store){
        if(b.cost==0)return store?"FREE":"NO FEE";
        if(b.cost<0 || b.cash<0)return "";
        return b.cost>b.cash?"SHORT "+CareerDesk.money(b.cost-b.cash):"IN BUDGET";
    }
    /** Radius for a change on the locked scale: zero sits on the middle ring, gains reach out, losses pull in. */
    static float reach(int value){return .5f+.5f*TrainingSheet.fill(value);}

    @Override protected void onDraw(Canvas c){
        float w=getWidth(),h=getHeight();
        decor(c,w,h);
        if(deltas!=null)radar(c,w,h);
        else if(quote!=null)receipt(c,w,h);
    }
    private void decor(Canvas c,float w,float h){
        // Film perforations on the left and burnt-orange hazard tape along the bottom edge.
        ink.setStyle(Paint.Style.FILL);ink.setColor(0xff681e14);c.drawRect(0,0,8*dp,h,ink);
        ink.setColor(0xff130c09);
        for(float y=6*dp;y<h-6*dp;y+=14*dp)c.drawRoundRect(2*dp,y,6*dp,y+7*dp,dp,dp,ink);
        float band=14*dp,top=h-band;
        c.save();c.clipRect(8*dp,top,w,h);
        ink.setColor(0xff2b120c);c.drawRect(8*dp,top,w,h,ink);
        ink.setColor(0x88af3927);
        for(float x=-band;x<w+band;x+=22*dp){
            shape.reset();shape.moveTo(x,h);shape.lineTo(x+band,top);shape.lineTo(x+band+10*dp,top);shape.lineTo(x+10*dp,h);shape.close();
            c.drawPath(shape,ink);
        }
        c.restore();
        ink.setColor(GameInk.ORANGE);c.drawRect(8*dp,top,w,top+dp,ink);
    }
    private void text(Canvas c,String s,float x,float y,Typeface face,float size,int color,Paint.Align align){
        ink.setStyle(Paint.Style.FILL);ink.setTypeface(face);ink.setTextSize(size*sp);ink.setColor(color);ink.setTextAlign(align);
        c.drawText(s,x,y,ink);
    }
    private void radar(Canvas c,float w,float h){
        float usable=h-14*dp;
        int g=best(deltas),k=worst(deltas);
        // Watermark: the attribute that gains most, huge and faint behind everything.
        if(g>=0){
            c.save();c.rotate(-6,w*.62f,usable*.55f);
            ink.setTypeface(stencil);ink.setTextAlign(Paint.Align.CENTER);ink.setTextSize(usable*.42f);
            float span=ink.measureText(NAMES[g].toUpperCase(Locale.ROOT));
            if(span>w*.9f)ink.setTextSize(ink.getTextSize()*w*.9f/span);
            ink.setColor(0x14fff8e9);c.drawText(NAMES[g].toUpperCase(Locale.ROOT),w*.62f,usable*.55f+ink.getTextSize()*.35f,ink);
            c.restore();
        }
        // Keep the radar a comfortable size when the poster stretches to fill a tall screen; leave room for the legend.
        float radius=Math.min(Math.min((usable-30*dp)*.40f,w*.19f),110*dp),cx=8*dp+radius+28*dp,cy=(usable-22*dp)*.52f;
        // Rings at the locked scale's floor, zero and ceiling.
        ink.setStyle(Paint.Style.STROKE);ink.setStrokeWidth(dp);
        for(float ring:new float[]{.5f,1f}){
            ink.setColor(ring==.5f?0x99a58a6c:0x55a58a6c);polygon(cx,cy,radius*ring,null);c.drawPath(shape,ink);
        }
        for(int i=0;i<8;i++){
            double a=Math.toRadians(-90+45*i);
            c.drawLine(cx,cy,cx+(float)Math.cos(a)*radius,cy+(float)Math.sin(a)*radius,ink);
        }
        // NOW: the boxer's current stats as the faded even octagon on the zero ring. The game reports changes,
        // not absolute levels, so every axis starts level here.
        polygon(cx,cy,radius*reach(0),null);
        ink.setStyle(Paint.Style.FILL);ink.setColor(0x30fff8e9);c.drawPath(shape,ink);
        ink.setStyle(Paint.Style.STROKE);ink.setStrokeWidth(1.5f*dp);ink.setColor(0xa0fff8e9);
        ink.setPathEffect(new DashPathEffect(new float[]{4*dp,3*dp},0));c.drawPath(shape,ink);ink.setPathEffect(null);
        // POTENTIAL / AFTER: the bright shape on top, each axis moved by its real change (exaggerated by the locked
        // scale so small changes read), with a soft glow under a crisp gold edge.
        polygon(cx,cy,radius,deltas);
        ink.setStyle(Paint.Style.FILL);ink.setColor(0x4cf6bf55);c.drawPath(shape,ink);
        ink.setStyle(Paint.Style.STROKE);ink.setStrokeJoin(Paint.Join.ROUND);
        ink.setStrokeWidth(7*dp);ink.setColor(0x33f6bf55);c.drawPath(shape,ink);
        ink.setStrokeWidth(4*dp);ink.setColor(0x55ffd27a);c.drawPath(shape,ink);
        ink.setStrokeWidth(2.2f*dp);ink.setColor(0xffffd98a);c.drawPath(shape,ink);
        ink.setStyle(Paint.Style.FILL);
        for(int i=0;i<8;i++){
            double a=Math.toRadians(-90+45*i);float r=radius*(.5f+(reach(deltas[i])-.5f)*fraction);
            ink.setColor(deltas[i]>0?TrainingSheet.GAIN:deltas[i]<0?TrainingSheet.LOSS:GameInk.MUTED);
            c.drawCircle(cx+(float)Math.cos(a)*r,cy+(float)Math.sin(a)*r,3*dp,ink);
            float lx=cx+(float)Math.cos(a)*(radius+15*dp),ly=cy+(float)Math.sin(a)*(radius+13*dp)+4.5f*sp;
            text(c,AXES[i],lx,ly,stencil,12,deltas[i]==0?GameInk.MUTED:GameInk.TEXT,Paint.Align.CENTER);
        }
        // Callouts: the clearest gain beside the largest cost.
        float x=cx+radius+30*dp,line=usable/4.6f,size=Math.min(30,Math.max(18,line/sp*.85f));
        float room=w-x-10*dp;
        text(c,result?"BEST GAIN":"PROJECTED GAIN",x,line*1.05f,stencil,12,GameInk.GOLD,Paint.Align.LEFT);
        callout(c,g<0?"NO GAINS":signed(deltas[g])+"  "+NAMES[g].toUpperCase(Locale.ROOT),x,line*2.0f,size,room,
                g<0?GameInk.MUTED:TrainingSheet.GAIN);
        text(c,result?"BIGGEST COST":"PROJECTED COST",x,line*3.0f,stencil,12,GameInk.GOLD,Paint.Align.LEFT);
        callout(c,k<0?"NO COSTS":signed(deltas[k])+"  "+NAMES[k].toUpperCase(Locale.ROOT),x,line*3.95f,size,room,
                k<0?GameInk.MUTED:TrainingSheet.LOSS);
        // Legend under the radar, only where it clears the bottom label and the tape.
        float keyY=cy+radius+32*dp,keyX=cx-radius;
        if(keyY<=usable-4*dp)legend(c,keyX,keyY);
    }
    private void legend(Canvas c,float keyX,float keyY){
        ink.setStyle(Paint.Style.FILL);ink.setColor(0x30fff8e9);c.drawRect(keyX,keyY-9*dp,keyX+12*dp,keyY,ink);
        ink.setStyle(Paint.Style.STROKE);ink.setStrokeWidth(dp);ink.setColor(0xa0fff8e9);c.drawRect(keyX,keyY-9*dp,keyX+12*dp,keyY,ink);
        text(c,"NOW",keyX+16*dp,keyY,stencil,11,GameInk.MUTED,Paint.Align.LEFT);
        ink.setTypeface(stencil);ink.setTextSize(11*sp);float gap=keyX+16*dp+ink.measureText("NOW")+12*dp;
        ink.setStyle(Paint.Style.FILL);ink.setColor(0xffffd98a);c.drawRect(gap,keyY-9*dp,gap+12*dp,keyY,ink);
        text(c,result?"AFTER":"POTENTIAL",gap+16*dp,keyY,stencil,11,GameInk.GOLD,Paint.Align.LEFT);
    }
    private void callout(Canvas c,String s,float x,float y,float size,float room,int color){
        ink.setTypeface(stencil);ink.setTextSize(size*sp);
        float span=ink.measureText(s);
        float fitted=span>room && span>0?size*room/span:size;
        ink.setShadowLayer(2*dp,0,dp,0xff0a0504);
        text(c,s,x,y,stencil,fitted,color,Paint.Align.LEFT);
        ink.clearShadowLayer();
    }
    private void polygon(float cx,float cy,float radius,int[] values){
        shape.reset();
        for(int i=0;i<8;i++){
            double a=Math.toRadians(-90+45*i);
            float r=values==null?radius:radius*(.5f+(reach(values[i])-.5f)*fraction);
            float px=cx+(float)Math.cos(a)*r,py=cy+(float)Math.sin(a)*r;
            if(i==0)shape.moveTo(px,py);else shape.lineTo(px,py);
        }
        shape.close();
    }
    /** Fee as a whole percentage of camp cash, or -1 when either is unknown or the wallet is empty. */
    static int share(CareerDesk.Budget b){
        if(b.cost<=0 || b.cash<=0)return -1;
        return (int)Math.round(100.0*b.cost/b.cash);
    }
    private void receipt(Canvas c,float w,float h){
        float usable=h-14*dp;
        String mark=stamp(quote,store);
        boolean shortfall=quote.cost>0 && quote.cash>=0 && quote.cost>quote.cash;
        int color=shortfall?TrainingSheet.LOSS:quote.cost==0?GameInk.GOLD:TrainingSheet.GAIN;
        // Faint stencil watermark across the poster.
        c.save();c.rotate(-6,w*.55f,usable*.5f);
        ink.setStyle(Paint.Style.FILL);ink.setTypeface(stencil);ink.setTextAlign(Paint.Align.CENTER);
        ink.setTextSize(Math.min(usable*.30f,w*.16f));ink.setColor(0x12fff8e9);
        c.drawText(store?"FIGHT STORE":"CAMP STAFF",w*.55f,usable*.5f+ink.getTextSize()*.35f,ink);
        c.restore();
        // Narrow screens stack the stamp above a full-width gauge; wide ones place them side by side.
        boolean narrow=w-8*dp<400*dp;
        float y=Math.min(usable*(narrow?.70f:.58f),usable-36*dp);
        // Stacked: the stamp is centred in, and sized to, the space above the gauge's title.
        float above=Math.max(24*dp,y-30*dp-20*dp);
        if(!mark.isEmpty()){
            // A tilted rubber stamp: double outline, stencil lettering.
            float cx=narrow?8*dp+(w-8*dp)*.5f:8*dp+(w-8*dp)*.30f,cy=narrow?above*.55f:usable*.5f;
            c.save();c.rotate(-8,cx,cy);
            // Slam: lands oversized and transparent, then settles with the overshoot.
            float land=1f+.45f*(1f-fraction);c.scale(land,land,cx,cy);
            int alpha=Math.max(0,Math.min(255,Math.round(fraction*2f*255)));
            color=(color&0x00ffffff)|(alpha<<24);
            ink.setTypeface(stencil);ink.setTextSize(narrow?Math.min(above/1.9f,44*sp):Math.min(usable*.24f,44*sp));ink.setTextAlign(Paint.Align.CENTER);
            float tw=ink.measureText(mark),maxW=(w-8*dp)*(narrow?.70f:.52f);
            if(tw>maxW)ink.setTextSize(ink.getTextSize()*maxW/tw);
            tw=ink.measureText(mark);float th=ink.getTextSize();
            ink.setStyle(Paint.Style.STROKE);ink.setColor(color);ink.setStrokeWidth(2.5f*dp);
            c.drawRoundRect(cx-tw/2-12*dp,cy-th*.85f,cx+tw/2+12*dp,cy+th*.55f,4*dp,4*dp,ink);
            ink.setStrokeWidth(dp);
            c.drawRoundRect(cx-tw/2-7*dp,cy-th*.72f,cx+tw/2+7*dp,cy+th*.42f,3*dp,3*dp,ink);
            ink.setStyle(Paint.Style.FILL);c.drawText(mark,cx,cy+th*.15f,ink);
            c.restore();
        }
        if(quote.cash<0 || quote.cost<0)return;
        // Wallet gauge: camp cash as the bar, the fee as a marker on the same scale.
        float x0=narrow?20*dp:8*dp+(w-8*dp)*.60f,x1=w-14*dp,top=Math.max(quote.cash,quote.cost);
        // (y keeps the labels under the bar clear of the hazard tape on short posters.)
        if(top<=0 || x1-x0<40*dp || y-30*dp<12*dp)return;
        text(c,store?"WALLET VS PRICE":"WALLET VS FEE",x0,y-30*dp,stencil,14,GameInk.GOLD,Paint.Align.LEFT);
        ink.setStyle(Paint.Style.FILL);ink.setColor(0xff4a2419);c.drawRect(x0,y-8*dp,x1,y+8*dp,ink);
        float fill=Math.min(1f,Math.max(0f,fraction));
        float cash=x0+(x1-x0)*quote.cash/top*fill,fee=x0+(x1-x0)*quote.cost/top;
        ink.setColor(0xffe8ac32);c.drawRect(x0,y-8*dp,cash,y+8*dp,ink);
        if(shortfall){ink.setColor(0x99ef6b4a);c.drawRect(cash,y-8*dp,fee,y+8*dp,ink);}
        ink.setColor(GameInk.TEXT);c.drawRect(fee-1.5f*dp,y-14*dp,fee+1.5f*dp,y+14*dp,ink);
        text(c,(store?"wallet ":"cash ")+CareerDesk.money(quote.cash),x0,y+30*dp,body,14,GameInk.MUTED,Paint.Align.LEFT);
        text(c,(store?"price ":"fee ")+CareerDesk.money(quote.cost),x1,y+30*dp,body,14,GameInk.TEXT,Paint.Align.RIGHT);
        int pct=share(quote);
        if(pct>=0 && y+56*dp<usable)
            text(c,store?"PRICE = "+pct+"% OF YOUR WALLET":"FEE = "+pct+"% OF CAMP CASH",x0,y+54*dp,stencil,13,shortfall?TrainingSheet.LOSS:GameInk.MUTED,Paint.Align.LEFT);
    }
}
