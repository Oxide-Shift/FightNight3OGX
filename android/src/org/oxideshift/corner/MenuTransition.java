package org.oxideshift.corner;

import android.graphics.*;
import java.io.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Reads the retail transition movie, including its nested clips and GPU geometry, from the imported disc. */
final class MenuTransition {
    private interface Art {void draw(Canvas c,Paint p,int age,int alpha);}
    private static final class Placed {
        final Art art;
        final Matrix transform;
        final int alpha,born;
        Placed(Art art,Matrix transform,int alpha,int born){
            this.art=art;this.transform=transform;this.alpha=alpha;this.born=born;
        }
        void draw(Canvas c,Paint p,int frame,int inherited){
            int opacity=alpha*inherited/255;
            if(opacity==0)return;
            c.save();c.concat(transform);art.draw(c,p,frame-born,opacity);c.restore();
        }
    }
    private final Art[] characters;
    private final ByteBuffer apt;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    final int width,height,frames,changeFrame;
    private static ByteBuffer buffer(byte[] bytes){return ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);}
    private static String string(ByteBuffer b,int at){
        int end=at;while(b.get(end)!=0)end++;
        return new String(b.array(),b.arrayOffset()+at,end-at,StandardCharsets.US_ASCII);
    }
    private static byte[] member(File game,String suffix,String hash)throws IOException{
        byte[] b=TraxArt.textureBank(game,"custom.viv","controls/transition.big","transition."+suffix,2*1024*1024);
        RetailMenu.verify(b,hash);return b;
    }
    MenuTransition(File game,Typeface stencil)throws IOException{
        // Only interpret the known Xbox front end's layouts. Other revisions can still open the cabinet.
        apt=buffer(member(game,"apt","722ab61ea7ae00f6e772b8697f968984f8b17e2af842e935d5c3ae7ceafa3c4c"));
        Geometry geometry=new Geometry(member(game,"o","c5e4a8cebaf78fdec49f3006d862c5d1b48192382e44881c96716696aff58165"));
        byte[] bank=member(game,"xsh","7b306d23c301f9fbb5cf0cc346a4862b04bdc8f9201b3feb81300387d84eb816");
        ByteBuffer textures=buffer(bank);
        Map<Integer,Bitmap> bitmaps=new HashMap<>();
        for(int i=0;i<textures.getInt(8);i++){
            int row=16+8*i,at=textures.getInt(row+4);
            int id=Integer.parseInt(new String(bank,row,4,StandardCharsets.US_ASCII).trim());
            int w=textures.getShort(at+4),h=textures.getShort(at+6);
            bitmaps.put(id,Bitmap.createBitmap(TraxArt.bc2(bank,at+16,w,h),w,h,Bitmap.Config.ARGB_8888));
        }
        int root=apt.getInt(12);
        width=apt.getInt(root+28);height=apt.getInt(root+32);frames=apt.getInt(root+8);
        characters=new Art[apt.getInt(root+20)];
        for(int id=1;id<characters.length;id++){
            int at=apt.getInt(12+4*id);
            switch(apt.getInt(at)){
                case 1:
                    int model=apt.getInt(at+24);
                    Bitmap sprite=bitmaps.get(model-1);
                    if(sprite==null)characters[id]=geometry.shape(model);
                    else{
                        RectF bounds=new RectF(apt.getFloat(at+8),apt.getFloat(at+12),apt.getFloat(at+16),apt.getFloat(at+20));
                        characters[id]=(c,p,age,alpha)->{p.setColor(0xffffffff);p.setAlpha(alpha);c.drawBitmap(sprite,null,bounds,p);};
                    }
                    break;
                case 2:
                    String text=string(apt,apt.getInt(at+52));
                    String caption=text.equals("$18000")?"LOADING":text;
                    int color=apt.getInt(at+32);
                    float size=apt.getFloat(at+36),x=apt.getFloat(at+8)+2,y=apt.getFloat(at+12)+2;
                    characters[id]=(c,p,age,alpha)->{
                        p.setTypeface(stencil);p.setTextSize(size);p.setColor(color);p.setAlpha(alpha);
                        c.drawText(caption,x,y-p.ascent(),p);
                    };
                    break;
                case 5: characters[id]=clip(at);break;
                default: break; // Resource declarations have no display-list entry.
            }
        }
        characters[0]=clip(root);
        int callback=-1,items=apt.getInt(root+12);
        for(int i=1;i<frames-1;i++){
            for(int j=0;j<apt.getInt(items+8*i);j++){
                int item=apt.getInt(apt.getInt(items+8*i+4)+4*j);
                if(apt.getInt(item)==1)callback=i;
            }
        }
        if(callback<0)throw new IOException("Missing transition callback");
        changeFrame=callback;
    }
    private Art clip(int at){
        int count=apt.getInt(at+8),items=apt.getInt(at+12);
        Placed[][] timeline=new Placed[count][];
        TreeMap<Integer,Placed> depths=new TreeMap<>();
        for(int f=0;f<count;f++){
            int n=apt.getInt(items+8*f),entries=apt.getInt(items+8*f+4);
            for(int j=0;j<n;j++){
                int it=apt.getInt(entries+4*j),tag=apt.getInt(it);
                if(tag==4){depths.remove(apt.getInt(it+4));continue;}
                if(tag!=3)continue;
                int flags=apt.getInt(it+4),depth=apt.getInt(it+8);
                Placed prior=depths.get(depth);
                boolean place=(flags&2)!=0;
                Art art=place?characters[apt.getInt(it+12)]:prior.art;
                Matrix transform=prior==null?new Matrix():prior.transform;
                if((flags&4)!=0){
                    transform=new Matrix();
                    transform.setValues(new float[]{apt.getFloat(it+16),apt.getFloat(it+24),apt.getFloat(it+32),
                            apt.getFloat(it+20),apt.getFloat(it+28),apt.getFloat(it+36),0,0,1});
                }
                int alpha=(flags&8)!=0?(apt.getInt(it+40)>>>24):prior==null?255:prior.alpha;
                depths.put(depth,new Placed(art,transform,alpha,place?f:prior.born));
            }
            timeline[f]=depths.values().toArray(new Placed[0]);
        }
        return (c,p,age,opacity)->{
            int f=age%count;
            for(Placed obj:timeline[f])obj.draw(c,p,f,opacity);
        };
    }
    void draw(Canvas canvas,int frame,int w,int h){
        canvas.save();canvas.scale(w/(float)width,h/(float)height);
        characters[0].draw(canvas,paint,Math.max(0,Math.min(frames-1,frame)),255);canvas.restore();
    }

    /** These ELF objects contain named material groups, indexed strips and Xbox packed positions. */
    private static final class Geometry {
        final ByteBuffer data;
        final Map<String,Integer> symbols=new HashMap<>();
        final Map<Integer,String> bindings=new HashMap<>();
        Geometry(byte[] elf){
            ByteBuffer object=buffer(elf);int sections=object.getInt(32);
            int base=object.getInt(sections+40+16);
            object.position(base);data=object.slice().order(ByteOrder.LITTLE_ENDIAN);
            int symbolsAt=object.getInt(sections+4*40+16),count=object.getInt(sections+4*40+20)/16;
            int strings=object.getInt(sections+5*40+16);String[] names=new String[count];
            for(int i=0;i<count;i++){
                int row=symbolsAt+16*i;names[i]=string(object,strings+object.getInt(row));
                symbols.put(names[i],object.getInt(row+4));
            }
            int rel=object.getInt(sections+2*40+16),end=rel+object.getInt(sections+2*40+20);
            for(int i=rel;i<end;i+=8)bindings.put(object.getInt(i),names[object.getInt(i+4)>>>8]);
        }
        Art shape(int id){
            List<Art> groups=new ArrayList<>();
            int model=symbols.get("__Model:::"+id);
            for(int group=0;;group++){
                Integer material=symbols.get("__RM:::__GP_RM_"+id+"_"+group);
                if(material==null)break;
                int draw=material+56,indices=data.getInt(draw+32),n=data.getInt(draw+28);
                int vertex=data.getInt(draw+64),color=data.getInt(draw+56);
                int argb=Color.argb(channel(color+12),channel(color),channel(color+4),channel(color+8));
                boolean lines=bindings.get(draw+8).contains("PT_LINELIST");
                Path path=new Path();path.setFillType(Path.FillType.WINDING);
                for(int k=lines?1:2;k<n;k+=lines?2:1){
                    int stop=k+1,start=lines?k-1:k-2;
                    // Triangle strips alternate winding. Give every triangle the same winding for a single fill.
                    for(int j=start;j<stop;j++){
                        int index=!lines && (k&1)!=0 && j<k?start+1-(j-start):j;
                        int v=vertex+6*(data.getShort(indices+2*index)&65535);
                        float x=data.getShort(v)*data.getFloat(model+76)/32768f+data.getFloat(model+92);
                        float y=data.getShort(v+2)*data.getFloat(model+80)/32768f+data.getFloat(model+96);
                        if(j==start)path.moveTo(x,y);else path.lineTo(x,y);
                    }
                    if(!lines)path.close();
                }
                groups.add((c,p,age,opacity)->{
                    p.setColor(argb);p.setAlpha(Color.alpha(argb)*opacity/255);
                    p.setStyle(lines?Paint.Style.STROKE:Paint.Style.FILL);p.setStrokeWidth(1);
                    c.drawPath(path,p);p.setStyle(Paint.Style.FILL);
                });
            }
            return (c,p,age,alpha)->{for(Art group:groups)group.draw(c,p,age,alpha);};
        }
        int channel(int at){return Math.round(data.getFloat(at)*255);}
    }
}
