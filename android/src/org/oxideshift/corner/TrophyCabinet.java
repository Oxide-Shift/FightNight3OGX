package org.oxideshift.corner;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.*;
import android.view.View;

/** The fourteen retail career trophies as a lit 2 x 7 cabinet (career directive pp. 10, 21). Only the game's CASE mask
 *  and TROPHY slot are drawn: earned plates carry a gold cup, locked ones a debossed outline, and the selected plate
 *  a separate frame and underline. Plates are numbered; there is no verified slot-to-trophy art or name map. */
final class TrophyCabinet extends View {
    static final int SLOTS=14,PER_ROW=7;
    private final Paint ink=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path cup=new Path();
    private final RectF plate=new RectF();
    private final Typeface stencil;
    private final float dp,sp;
    private final ValueAnimator pop=ValueAnimator.ofFloat(0f,1f);
    private float fraction=1f;
    private int mask=-1,slot=-1;

    TrophyCabinet(Context context,Typeface stencil){
        super(context);this.stencil=stencil;
        dp=getResources().getDisplayMetrics().density;sp=getResources().getDisplayMetrics().scaledDensity;
        pop.setDuration(700);
        pop.addUpdateListener(a->{fraction=(float)a.getAnimatedValue();invalidate();});
        raise.setDuration(320);
        raise.addUpdateListener(a->{hover=(float)a.getAnimatedValue();invalidate();});
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
    }
    void show(int earned,int selected){
        boolean entering=mask<0 && earned>=0;
        if(mask==earned && slot==selected)return;
        boolean moved=slot!=selected;
        if(moved){previous=slot;}
        mask=earned;slot=selected;
        setContentDescription(describe(earned,selected));
        if(entering && SheetMotion.on() && isShown()){pop.cancel();fraction=0;pop.setInterpolator(SheetMotion.arrive());pop.start();}
        // Hover: the new selection rises with a little overshoot while the previous one settles back.
        if(moved && SheetMotion.on() && isShown()){
            raise.cancel();hover=0;raise.setInterpolator(new android.view.animation.OvershootInterpolator(1.4f));raise.start();
        }else if(moved){hover=1f;previous=-1;}
        invalidate();
    }
    @Override protected void onDetachedFromWindow(){pop.cancel();raise.cancel();fraction=1f;hover=1f;previous=-1;super.onDetachedFromWindow();}

    /** "5 / 14 earned" for a valid mask (including 0 / 14), "" when the game has not reported one. */
    static String count(int mask){return mask<0?"":Integer.bitCount(mask&0x3fff)+" / 14 earned";}
    /** "Trophy 07  ·  Earned", or "" without a valid slot and mask. Never an invented trophy name. */
    static String caption(int mask,int slot){
        if(mask<0 || slot<0 || slot>=SLOTS)return "";
        return String.format(java.util.Locale.ROOT,"Trophy %02d  ·  %s",slot+1,(mask&(1<<slot))!=0?"Earned":"Locked");
    }
    static String describe(int mask,int slot){
        if(mask<0)return "Trophy cabinet.";
        String c=caption(mask,slot);
        return "Trophy cabinet, "+count(mask)+"."+(c.isEmpty()?"":" Selected "+c.replace("  ·  ",", ")+".");
    }

    @Override protected void onDraw(Canvas c){
        float w=getWidth(),h=getHeight();
        // Cabinet: dark back panel, two lit shelves, burnt-orange trim.
        ink.setStyle(Paint.Style.FILL);ink.setShader(new LinearGradient(0,0,0,h,0xff2b130d,0xff140906,Shader.TileMode.CLAMP));
        c.drawRect(0,0,w,h,ink);ink.setShader(null);
        ink.setColor(GameInk.ORANGE);c.drawRect(0,0,w,2*dp,ink);
        // No mask from the game: the cabinet introduces the collection without any earned or locked claim.
        boolean known=mask>=0;
        if(!known){
            ink.setTypeface(stencil);ink.setTextAlign(Paint.Align.CENTER);ink.setTextSize(Math.min(h*.20f,w*.11f));
            ink.setColor(0x16fff8e9);c.save();c.rotate(-6,w/2,h/2);c.drawText("CAREER TROPHIES",w/2,h/2+ink.getTextSize()*.35f,ink);c.restore();
        }
        float pad=10*dp,gap=6*dp,cell=(w-2*pad-(PER_ROW-1)*gap)/PER_ROW,rowH=(h-2*pad-gap)/2f;
        for(int row=0;row<2;row++){
            float shelf=pad+rowH*(row+1)+gap*row;
            // A lit shelf edge under each row.
            ink.setColor(0xff5a2a18);c.drawRect(pad-4*dp,shelf-4*dp,w-pad+4*dp,shelf,ink);
            ink.setColor(0x55ffd27a);c.drawRect(pad-4*dp,shelf-4*dp,w-pad+4*dp,shelf-3*dp,ink);
        }
        // Flat plates first; the settling and the selected plate are drawn last, lifted toward the viewer.
        for(int i=0;i<SLOTS;i++){
            if(known && (i==slot || i==previous))continue;
            plateAt(c,i,known,pad,gap,cell,rowH,0f);
        }
        if(known && previous>=0 && previous!=slot)plateAt(c,previous,known,pad,gap,cell,rowH,Math.max(0f,1f-hover));
        if(known && slot>=0 && slot<SLOTS)plateAt(c,slot,known,pad,gap,cell,rowH,hover);
    }
    /** One plate. lift 0 = flat on the shelf; 1 = raised: zoomed, tilted in 3D toward the cabinet's centre,
     *  casting a soft shadow, with a light glint sweeping across. */
    private void plateAt(Canvas c,int i,boolean known,float pad,float gap,float cell,float rowH,float lift){
        int row=i/PER_ROW,col=i%PER_ROW;
        float x=pad+col*(cell+gap),y=pad+row*(rowH+gap),bottom=y+rowH-6*dp;
        boolean earned=known && (mask&(1<<i))!=0,selected=known && i==slot;
        // Each plate pops in a beat after the one before it.
        float t=Math.max(0f,Math.min(1f,(fraction*1.6f-i*.045f)));
        float pcx=x+cell/2,pcy=(y+bottom)/2;
        c.save();
        if(lift>0f){
            // Soft shadow, growing and dropping as the plate rises.
            ink.setStyle(Paint.Style.FILL);ink.setColor(0x00000000);
            ink.setShadowLayer(6*dp+10*dp*lift,0,4*dp+8*dp*lift,(Math.round(0xb0*Math.min(1f,lift))<<24));
            ink.setColor(0xff140906);c.drawRoundRect(x+2*dp,y+2*dp,x+cell-2*dp,bottom,4*dp,4*dp,ink);
            ink.clearShadowLayer();
            camera.save();
            camera.rotateX(9f*lift);camera.rotateY(-12f*lift*(col-3)/3f);
            camera.getMatrix(tilt);camera.restore();
            tilt.preTranslate(-pcx,-pcy);tilt.postTranslate(pcx,pcy);
            c.concat(tilt);
            float zoom=1f+.14f*lift;
            c.translate(0,-6*dp*lift);c.scale(zoom,zoom,pcx,pcy);
        }
        plate.set(x,y,x+cell,bottom);
            ink.setStyle(Paint.Style.FILL);ink.setColor(earned?0xff3a1a10:0xff221009);c.drawRoundRect(plate,3*dp,3*dp,ink);
            float size=Math.min(cell*.62f,(bottom-y)*.52f)*(.6f+.4f*t),cx=x+cell/2,cy=y+(bottom-y)*.42f;
            shape(i,cx,cy,size);
            if(earned){
                ink.setColor(0x33ffd27a);c.drawCircle(cx,cy,size*.75f*t,ink);
                ink.setShader(new LinearGradient(cx-size/2,cy-size/2,cx+size/2,cy+size/2,0xffffe08a,0xffc98a22,Shader.TileMode.CLAMP));
                ink.setAlpha(Math.round(255*t));c.drawPath(cup,ink);ink.setShader(null);
                // Engraved details in darker bronze.
                ink.setColor(0xff8a5a14);ink.setAlpha(Math.round(255*t));c.drawPath(detail,ink);ink.setAlpha(255);
            }else{
                ink.setStyle(Paint.Style.STROKE);ink.setStrokeWidth(1.2f*dp);ink.setColor(0x66a58a6c);c.drawPath(cup,ink);
                ink.setColor(0x44a58a6c);c.drawPath(detail,ink);
            }
            // Number plate, and the state in words as well as ink.
            float label=Math.max(9f,Math.min(13f,cell/sp*.24f));
            ink.setStyle(Paint.Style.FILL);ink.setTypeface(stencil);ink.setTextAlign(Paint.Align.CENTER);
            ink.setTextSize(label*sp);ink.setColor(earned?GameInk.TEXT:GameInk.MUTED);
            c.drawText(String.format(java.util.Locale.ROOT,"%02d",i+1),cx,bottom-label*sp*.9f,ink);
            if(known && cell>34*dp){
                ink.setTextSize(Math.max(7.5f,label-4)*sp);ink.setColor(earned?GameInk.GOLD:0x99a58a6c);
                c.drawText(earned?"EARNED":"LOCKED",cx,bottom-2*dp,ink);
            }
            if(lift>0f){
                // A glint of light sweeping diagonally across the raised plate.
                float sweep=plate.left-cell+(cell*2.4f)*Math.min(1f,lift);
                c.save();c.clipRect(plate);
                ink.setStyle(Paint.Style.FILL);
                ink.setShader(new LinearGradient(sweep,plate.top,sweep+cell*.6f,plate.bottom,
                        new int[]{0x00fff2c4,(Math.round(0x40*Math.min(1f,lift))<<24)|0xfff2c4,0x00fff2c4},null,Shader.TileMode.CLAMP));
                c.drawRect(plate,ink);ink.setShader(null);c.restore();
            }
            if(selected){
                // Selection: its own frame and underline, independent of earned/locked.
                ink.setStyle(Paint.Style.STROKE);ink.setStrokeWidth(2*dp);ink.setColor(0xffffd98a);
                c.drawRoundRect(plate.left-2*dp,plate.top-2*dp,plate.right+2*dp,plate.bottom+2*dp,4*dp,4*dp,ink);
                ink.setStyle(Paint.Style.FILL);c.drawRect(plate.left,bottom+3*dp,plate.right,bottom+5*dp,ink);
            }
        c.restore();
    }
    private final Camera camera=new Camera();
    private final Matrix tilt=new Matrix();
    private final ValueAnimator raise=ValueAnimator.ofFloat(0f,1f);
    private float hover=1f;
    private int previous=-1;
    private final Path detail=new Path();
    static final String[] SHAPES={"cup","belt","star","medal","glove","shield","bell","crown","goblet","plaque","column","star medal","gem","grand cup"};
    /** A different silhouette per plate for variety. Decorative only: the game does not say which trophy sits in
     *  which slot, so plates stay numbered and unnamed. cup = outline/fill, detail = engraved marks. */
    private void shape(int slot,float cx,float cy,float s){
        cup.reset();detail.reset();
        float top=cy-s*.5f,bot=cy+s*.42f;
        switch(slot){
            case 0:cupShape(cx,cy,s);break;
            case 1:{ // championship belt: a curved strap with a big centre plate
                cup.addRoundRect(cx-s*.55f,cy-s*.12f,cx+s*.55f,cy+s*.14f,s*.08f,s*.08f,Path.Direction.CW);
                cup.addCircle(cx,cy,s*.28f,Path.Direction.CW);
                detail.addCircle(cx,cy,s*.16f,Path.Direction.CW);
                detail.addCircle(cx-s*.42f,cy+s*.01f,s*.04f,Path.Direction.CW);detail.addCircle(cx+s*.42f,cy+s*.01f,s*.04f,Path.Direction.CW);
                break;}
            case 2:{ // star on a pedestal
                star(cup,cx,cy-s*.12f,s*.34f,s*.15f);
                cup.addRect(cx-s*.06f,cy+s*.18f,cx+s*.06f,cy+s*.30f,Path.Direction.CW);
                cup.addRect(cx-s*.22f,cy+s*.30f,cx+s*.22f,bot,Path.Direction.CW);
                detail.addRect(cx-s*.16f,cy+s*.34f,cx+s*.16f,cy+s*.37f,Path.Direction.CW);break;}
            case 3:{ // medal on a V ribbon
                cup.moveTo(cx-s*.26f,top);cup.lineTo(cx-s*.06f,top);cup.lineTo(cx+s*.08f,cy-s*.02f);cup.lineTo(cx-s*.06f,cy+s*.02f);cup.close();
                cup.moveTo(cx+s*.26f,top);cup.lineTo(cx+s*.06f,top);cup.lineTo(cx-s*.08f,cy-s*.02f);cup.lineTo(cx+s*.06f,cy+s*.02f);cup.close();
                cup.addCircle(cx,cy+s*.16f,s*.26f,Path.Direction.CW);
                detail.addCircle(cx,cy+s*.16f,s*.16f,Path.Direction.CW);break;}
            case 4:{ // boxing glove with cuff and thumb
                cup.addOval(cx-s*.30f,top+s*.02f,cx+s*.30f,cy+s*.16f,Path.Direction.CW);
                cup.addOval(cx-s*.40f,cy-s*.18f,cx-s*.12f,cy+s*.10f,Path.Direction.CW);
                cup.addRoundRect(cx-s*.22f,cy+s*.10f,cx+s*.22f,bot,s*.05f,s*.05f,Path.Direction.CW);
                detail.addRect(cx-s*.22f,cy+s*.20f,cx+s*.22f,cy+s*.24f,Path.Direction.CW);break;}
            case 5:{ // shield with a chevron
                cup.moveTo(cx-s*.36f,top);cup.lineTo(cx+s*.36f,top);cup.lineTo(cx+s*.36f,cy);cup.quadTo(cx+s*.30f,cy+s*.32f,cx,bot);
                cup.quadTo(cx-s*.30f,cy+s*.32f,cx-s*.36f,cy);cup.close();
                detail.moveTo(cx-s*.22f,cy-s*.06f);detail.lineTo(cx,cy+s*.12f);detail.lineTo(cx+s*.22f,cy-s*.06f);
                detail.lineTo(cx+s*.22f,cy+s*.04f);detail.lineTo(cx,cy+s*.22f);detail.lineTo(cx-s*.22f,cy+s*.04f);detail.close();break;}
            case 6:{ // fight bell on a mount
                cup.moveTo(cx-s*.34f,cy+s*.18f);cup.quadTo(cx-s*.34f,top,cx,top);cup.quadTo(cx+s*.34f,top,cx+s*.34f,cy+s*.18f);cup.close();
                cup.addRect(cx-s*.42f,cy+s*.18f,cx+s*.42f,cy+s*.26f,Path.Direction.CW);
                cup.addRect(cx-s*.05f,cy+s*.26f,cx+s*.05f,cy+s*.34f,Path.Direction.CW);
                cup.addRect(cx-s*.26f,cy+s*.34f,cx+s*.26f,bot,Path.Direction.CW);
                detail.addCircle(cx,top+s*.04f,s*.05f,Path.Direction.CW);break;}
            case 7:{ // crown on a cushion
                cup.moveTo(cx-s*.40f,cy+s*.12f);cup.lineTo(cx-s*.40f,top+s*.10f);cup.lineTo(cx-s*.20f,cy-s*.12f);cup.lineTo(cx,top);
                cup.lineTo(cx+s*.20f,cy-s*.12f);cup.lineTo(cx+s*.40f,top+s*.10f);cup.lineTo(cx+s*.40f,cy+s*.12f);cup.close();
                cup.addRoundRect(cx-s*.44f,cy+s*.16f,cx+s*.44f,bot,s*.06f,s*.06f,Path.Direction.CW);
                detail.addCircle(cx,cy+s*.02f,s*.05f,Path.Direction.CW);detail.addCircle(cx-s*.22f,cy+s*.04f,s*.04f,Path.Direction.CW);
                detail.addCircle(cx+s*.22f,cy+s*.04f,s*.04f,Path.Direction.CW);break;}
            case 8:{ // tall narrow goblet
                cup.moveTo(cx-s*.20f,top);cup.lineTo(cx+s*.20f,top);cup.quadTo(cx+s*.18f,cy,cx+s*.04f,cy+s*.06f);
                cup.lineTo(cx+s*.04f,cy+s*.30f);cup.lineTo(cx+s*.18f,bot);cup.lineTo(cx-s*.18f,bot);cup.lineTo(cx-s*.04f,cy+s*.30f);
                cup.lineTo(cx-s*.04f,cy+s*.06f);cup.quadTo(cx-s*.18f,cy,cx-s*.20f,top);cup.close();
                detail.addRect(cx-s*.16f,top+s*.10f,cx+s*.16f,top+s*.14f,Path.Direction.CW);break;}
            case 9:{ // plaque on a stand
                cup.addRoundRect(cx-s*.34f,top,cx+s*.34f,cy+s*.20f,s*.05f,s*.05f,Path.Direction.CW);
                cup.addRect(cx-s*.24f,cy+s*.20f,cx+s*.24f,bot,Path.Direction.CW);
                detail.addRect(cx-s*.24f,top+s*.10f,cx+s*.24f,cy+s*.10f,Path.Direction.CW);break;}
            case 10:{ // column with a ball on top
                cup.addCircle(cx,top+s*.12f,s*.13f,Path.Direction.CW);
                cup.addRect(cx-s*.10f,top+s*.24f,cx+s*.10f,cy+s*.28f,Path.Direction.CW);
                cup.addRect(cx-s*.26f,cy+s*.28f,cx+s*.26f,bot,Path.Direction.CW);
                for(int k=0;k<3;k++)detail.addRect(cx-s*.10f,top+s*(.34f+.14f*k),cx+s*.10f,top+s*(.37f+.14f*k),Path.Direction.CW);break;}
            case 11:{ // star medal on a ribbon
                cup.addRect(cx-s*.08f,top,cx+s*.08f,cy-s*.14f,Path.Direction.CW);
                star(cup,cx,cy+s*.10f,s*.36f,s*.16f);
                detail.addCircle(cx,cy+s*.10f,s*.08f,Path.Direction.CW);break;}
            case 12:{ // gem on a base
                cup.moveTo(cx-s*.32f,cy-s*.12f);cup.lineTo(cx-s*.18f,top);cup.lineTo(cx+s*.18f,top);cup.lineTo(cx+s*.32f,cy-s*.12f);
                cup.lineTo(cx,cy+s*.24f);cup.close();
                cup.addRect(cx-s*.24f,cy+s*.28f,cx+s*.24f,bot,Path.Direction.CW);
                detail.moveTo(cx-s*.32f,cy-s*.12f);detail.lineTo(cx+s*.32f,cy-s*.12f);detail.lineTo(cx+s*.32f,cy-s*.09f);
                detail.lineTo(cx-s*.32f,cy-s*.09f);detail.close();break;}
            default:{ // grand cup with a lid
                cup.addCircle(cx,top+s*.02f,s*.06f,Path.Direction.CW);
                cup.addRoundRect(cx-s*.30f,top+s*.06f,cx+s*.30f,top+s*.14f,s*.04f,s*.04f,Path.Direction.CW);
                cup.moveTo(cx-s*.42f,top+s*.14f);cup.lineTo(cx+s*.42f,top+s*.14f);cup.quadTo(cx+s*.40f,cy+s*.06f,cx+s*.08f,cy+s*.12f);
                cup.lineTo(cx+s*.08f,cy+s*.26f);cup.lineTo(cx+s*.26f,bot);cup.lineTo(cx-s*.26f,bot);cup.lineTo(cx-s*.08f,cy+s*.26f);
                cup.lineTo(cx-s*.08f,cy+s*.12f);cup.quadTo(cx-s*.40f,cy+s*.06f,cx-s*.42f,top+s*.14f);cup.close();
                detail.addRect(cx-s*.30f,top+s*.26f,cx+s*.30f,top+s*.30f,Path.Direction.CW);}
        }
    }
    private static void star(Path p,float cx,float cy,float outer,float inner){
        for(int k=0;k<10;k++){
            double a=Math.toRadians(-90+36*k);float r=k%2==0?outer:inner;
            float x=cx+(float)Math.cos(a)*r,y=cy+(float)Math.sin(a)*r;
            if(k==0)p.moveTo(x,y);else p.lineTo(x,y);
        }
        p.close();
    }
    /** The classic two-handled cup. */
    private void cupShape(float cx,float cy,float s){
        float top=cy-s*.5f,bowl=s*.36f;
        cup.moveTo(cx-bowl,top);cup.lineTo(cx+bowl,top);
        cup.quadTo(cx+bowl,top+s*.42f,cx+s*.06f,top+s*.52f);
        cup.lineTo(cx+s*.06f,top+s*.70f);cup.lineTo(cx+s*.22f,top+s*.78f);cup.lineTo(cx+s*.22f,top+s*.88f);
        cup.lineTo(cx-s*.22f,top+s*.88f);cup.lineTo(cx-s*.22f,top+s*.78f);cup.lineTo(cx-s*.06f,top+s*.70f);
        cup.lineTo(cx-s*.06f,top+s*.52f);cup.quadTo(cx-bowl,top+s*.42f,cx-bowl,top);cup.close();
        // Handles.
        cup.addCircle(cx-bowl-s*.05f,top+s*.16f,s*.09f,Path.Direction.CW);
        cup.addCircle(cx+bowl+s*.05f,top+s*.16f,s*.09f,Path.Direction.CW);
    }
}
