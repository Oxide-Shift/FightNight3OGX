package org.oxideshift.corner;

import android.content.Context;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.os.SystemClock;
import java.io.*;
import java.nio.*;
import java.nio.channels.FileChannel;
import java.util.Locale;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/** Each complete Xbox frame is uploaded directly from its shared BGRA slot. */
public final class GameScreen extends GLSurfaceView implements GLSurfaceView.Renderer {
    static {System.loadLibrary("frame_reader");}
    private static native int sequence(ByteBuffer shared);
    private static native long waitForFrame(ByteBuffer shared,int observed);
    private static native int cameraBytes();
    private static native void measureCamera(ByteBuffer state,ByteBuffer slot,int w,int h,int sequence,int shot,float[] out);
    private final ByteBuffer camera=ByteBuffer.allocateDirect(cameraBytes());
    private final float[] offset=new float[2];
    private int steadyUniform;
    private final File mailbox;
    private final CornerSession session;
    private final int quality;
    private final FloatBuffer quad=ByteBuffer.allocateDirect(32).order(ByteOrder.nativeOrder()).asFloatBuffer();
    private volatile ByteBuffer shared;
    private ByteBuffer[] slots;
    private volatile int serial;
    private final int[] textures=new int[2], widths=new int[2], heights=new int[2];
    private int front,program, texel;
    private long since;
    private int presented;
    private volatile String output="Waiting for video";
    private volatile long lastPresented;
    private volatile String dimensions="";
    private volatile boolean hasFrame;
    private boolean scheduled,paused;
    private volatile int watchGeneration;
    private final boolean autoWide;
    private int surfaceWidth,surfaceHeight,cropUniform,centerUniform,patchBounds,ribbonStart,ribbonEnd;
    private final float[] starts=new float[4],ends=new float[4];
    private volatile int lowerRoute;
    private int renderedRoute;
    private int evaluatedRoute;
    private final BroadcastCues cues=new BroadcastCues();
    private final BoxerEditor editor=new BoxerEditor();
    private int tabBounds,fieldBounds,footerTop;
    final SceneFrameTimes timing=new SceneFrameTimes();
    private boolean routeRedraw;
    void lowerShows(int route){
        if(lowerRoute==route)return;
        lowerRoute=route;
        if(vsync)routeRedraw=true;else requestRender();
    }

    LiveFrame latestFrame(){
        ByteBuffer map=shared;
        if(map==null)return null;
        int seq=sequence(map);
        if(seq==0)return null;
        ByteBuffer p=slots[(int)(Integer.toUnsignedLong(seq)%3)];
        int w=p.getInt(0),h=p.getInt(4);
        if(w<1||h<1||w>1440||h>1080||8L+4L*w*h>p.capacity())return null;
        return new LiveFrame(p,w,h,seq,autoWide);
    }
    boolean current(LiveFrame f){return shared!=null && Integer.toUnsignedLong(sequence(shared)-f.sequence)<2;}
    public boolean hasFrame(){return hasFrame;}
    public String output(){
        return hasFrame && SystemClock.elapsedRealtime()-lastPresented>1500?dimensions+" \u00b7 0 fps":output;
    }
    private final DisplayCadence cadence;
    private final int targetFps;
    private final boolean vsync;
    private final android.app.Activity activity;
    private final android.view.Display.Mode paceMode;
    private int oldMode;
    private boolean requestedMode;
    private static android.view.Display.Mode modeFor(Context context,int fps){
        android.view.Display display=context.getDisplay();
        if(display==null)return null;
        android.view.Display.Mode current=display.getMode(),best=null;
        for(android.view.Display.Mode mode:display.getSupportedModes()){
            if(mode.getPhysicalWidth()!=current.getPhysicalWidth() || mode.getPhysicalHeight()!=current.getPhysicalHeight()
                    || !DisplayCadence.compatible(mode.getRefreshRate(),fps))continue;
            if(mode.getModeId()==current.getModeId())return current;
            if(best==null || mode.getRefreshRate()<best.getRefreshRate())best=mode;
        }
        return best;
    }
    private final android.view.Choreographer.FrameCallback tick=new android.view.Choreographer.FrameCallback(){
        public void doFrame(long frameTime){
            if(!scheduled)return;
            ByteBuffer map=shared;
            if(cadence.due(frameTime) && (routeRedraw || map==null || sequence(map)!=serial)){
                routeRedraw=false;requestRender();
            }
            android.view.Choreographer.getInstance().postFrameCallback(this);
        }
    };
    public GameScreen(android.app.Activity ctx,File file,int quality,boolean autoWide,int renderHeight,int targetFps,boolean vsync,CornerSession session){
        super(ctx);activity=ctx;this.session=session;this.targetFps=targetFps;this.vsync=vsync;cadence=new DisplayCadence(targetFps);
        paceMode=vsync?modeFor(ctx,targetFps):null;mailbox=file;this.quality=quality;this.autoWide=autoWide;
        quad.put(new float[]{-1,-1,1,-1,-1,1,1,1}).position(0);
        if(quality<2)addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{
            int w=r-l,h=b-t;
            if(w<=0 || h<=0 || (w==or-ol && h==ob-ot))return;
            int target=Math.min(h,renderHeight);
            int width=Math.max(1,(int)Math.round((double)w*target/h));
            getHolder().setFixedSize(width,target);
        });
        setEGLContextClientVersion(2);setRenderer(this);setRenderMode(RENDERMODE_WHEN_DIRTY);
    }
    private static int shader(int type,String src){
        int id=GLES20.glCreateShader(type);GLES20.glShaderSource(id,src);GLES20.glCompileShader(id);
        int[] ok=new int[1];GLES20.glGetShaderiv(id,GLES20.GL_COMPILE_STATUS,ok,0);
        if(ok[0]==0)throw new IllegalStateException(GLES20.glGetShaderInfoLog(id));
        return id;
    }
    @Override public void onSurfaceCreated(GL10 gl,EGLConfig config){
        String vertex="attribute vec2 pos;uniform float crop,center;uniform vec3 steady;varying vec2 uv;void main(){gl_Position=vec4(pos,0.,1.);uv=vec2(.5+.5*pos.x,center-.5*pos.y*crop);uv=.5+(uv-.5)*(1.-steady.z)+steady.xy;}";
        String fragment="precision highp float;varying vec2 uv;uniform sampler2D tex;uniform vec2 stepUV;uniform vec4 patchUV,tabUV,fieldUV;uniform vec4 starts,ends;uniform float footerTop;void main(){vec2 at=uv;"
            +"if(uv.x>tabUV.x && uv.x<tabUV.y && uv.y>tabUV.z && uv.y<tabUV.w)at.x=tabUV.y;"
            +"if(uv.x>fieldUV.x && uv.x<fieldUV.y && uv.y>fieldUV.z && uv.y<fieldUV.w)at.x=fieldUV.y;"
            +"if(uv.y>footerTop && uv.y<.9167){vec4 x=vec4(uv.x);if(any(bvec4((x.x>starts.x&&x.x<ends.x),(x.y>starts.y&&x.y<ends.y),(x.z>starts.z&&x.z<ends.z),(x.w>starts.w&&x.w<ends.w))))at.y=1.8334-uv.y;}"
            +"vec3 c=texture2D(tex,at).bgr;";
        if(quality==2 || quality==3) fragment+="vec3 n=texture2D(tex,at+vec2(stepUV.x,0.)).bgr+texture2D(tex,at-vec2(stepUV.x,0.)).bgr+texture2D(tex,at+vec2(0.,stepUV.y)).bgr+texture2D(tex,at-vec2(0.,stepUV.y)).bgr;c=clamp(c*1.32-n*.08,0.,1.);";
        // Reconstruct the paper from both clean edges at the same horizontal position.
        // Feather the rim after sharpening so the old prompt leaves no rectangular shadow.
        fragment+="if(uv.x>patchUV.x && uv.x<patchUV.y && uv.y>patchUV.z && uv.y<patchUV.w){"
                +"vec3 a=texture2D(tex,vec2(uv.x,patchUV.z)).bgr,b=texture2D(tex,vec2(uv.x,patchUV.w)).bgr;"
                +"vec2 rim=min(uv-vec2(patchUV.x,patchUV.z),vec2(patchUV.y,patchUV.w)-uv);"
                +"float blend=smoothstep(0.,1.,min(rim.x*240.,rim.y*180.));"
                +"c=mix(c,mix(a,b,(uv.y-patchUV.z)/(patchUV.w-patchUV.z)),blend);}gl_FragColor=vec4(c,1.);}";
        int v=shader(GLES20.GL_VERTEX_SHADER,vertex),f=shader(GLES20.GL_FRAGMENT_SHADER,fragment);
        program=GLES20.glCreateProgram();GLES20.glAttachShader(program,v);GLES20.glAttachShader(program,f);
        GLES20.glBindAttribLocation(program,0,"pos");GLES20.glLinkProgram(program);
        int[] status=new int[1];GLES20.glGetProgramiv(program,GLES20.GL_LINK_STATUS,status,0);
        if(status[0]==0)throw new IllegalStateException(GLES20.glGetProgramInfoLog(program));
        GLES20.glDeleteShader(v);GLES20.glDeleteShader(f);GLES20.glUseProgram(program);
        texel=GLES20.glGetUniformLocation(program,"stepUV");
        steadyUniform=GLES20.glGetUniformLocation(program,"steady");
        cropUniform=GLES20.glGetUniformLocation(program,"crop");
        centerUniform=GLES20.glGetUniformLocation(program,"center");
        patchBounds=GLES20.glGetUniformLocation(program,"patchUV");
        tabBounds=GLES20.glGetUniformLocation(program,"tabUV");fieldBounds=GLES20.glGetUniformLocation(program,"fieldUV");
        footerTop=GLES20.glGetUniformLocation(program,"footerTop");
        ribbonStart=GLES20.glGetUniformLocation(program,"starts");ribbonEnd=GLES20.glGetUniformLocation(program,"ends");
        GLES20.glGenTextures(2,textures,0);
        int target=GLES20.GL_TEXTURE_2D,mode=quality==0?GLES20.GL_NEAREST:GLES20.GL_LINEAR;
        for(int t:textures){
            GLES20.glBindTexture(target,t);GLES20.glTexParameteri(target,GLES20.GL_TEXTURE_MAG_FILTER,mode);
            GLES20.glTexParameteri(target,GLES20.GL_TEXTURE_MIN_FILTER,mode);
            GLES20.glTexParameteri(target,GLES20.GL_TEXTURE_WRAP_S,GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexParameteri(target,GLES20.GL_TEXTURE_WRAP_T,GLES20.GL_CLAMP_TO_EDGE);
        }
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        evaluatedRoute=renderedRoute=BroadcastCues.NONE;
        widths[0]=widths[1]=0;serial=0;hasFrame=false;presented=0;since=SystemClock.elapsedRealtime();
        GLES20.glVertexAttribPointer(0,2,GLES20.GL_FLOAT,false,0,quad);GLES20.glEnableVertexAttribArray(0);
        GLES20.glClearColor(0,0,0,1);
    }
    @Override public void onSurfaceChanged(GL10 gl,int w,int h){
        // GLSurfaceView has made the window surface current on this thread.
        // Reapply the interval when Android replaces the surface on wake.
        android.opengl.EGL14.eglSwapInterval(android.opengl.EGL14.eglGetCurrentDisplay(),vsync?1:0);
        surfaceWidth=w;surfaceHeight=h;updateViewport();
        android.view.Surface surface=getHolder().getSurface();
        if(surface.isValid())surface.setFrameRate(targetFps==60?60000f/1001f:targetFps,android.view.Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE);
    }
    private void updateViewport(){
        int w=surfaceWidth,h=surfaceHeight;
        int top=0,bottom=480;
        // Healing controls downstairs share the normal, full-height Xbox framing.
        int lines=bottom-top;
        int aspectWidth=autoWide?16:4,aspectHeight=autoWide?9:3;
        int vw=Math.min(w,h*aspectWidth/aspectHeight),vh=Math.min(h,w*aspectHeight/aspectWidth);
        GLES20.glViewport((w-vw)/2,(h-vh)/2,vw,vh);
        GLES20.glUniform1f(cropUniform,lines/480f);
        GLES20.glUniform1f(centerUniform,(top+bottom)/960f);
    }
    private void matchLower(LiveFrame frame){
        int request=lowerRoute, want=request&255, visible=0, actual=BroadcastCues.NONE;
        if(want!=BroadcastCues.NONE && frame!=null){
            if(want==BroadcastCues.EDITOR_DIALOG){
                CareerDesk.Page page=session.career.page;
                if(BoxerEditor.handles(page) && page.index==(request>>>8) && editor.dialog(frame))actual=want;
            }else if(want==BroadcastCues.EDITOR){
                CareerDesk.Page page=session.career.page;
                cues.read(frame);
                if(BoxerEditor.handles(page) && page.index==(request>>>8) && BoxerEditor.showing(page,frame,cues)){
                    actual=want;visible=page.index==1?cues.mask&11:8;
                }
            }else if(want==BroadcastCues.FOOTER){
                cues.read(frame);visible=cues.knownPrompts(frame,request>>>8);
                if(visible!=0)actual=want;
            }else{
                LiveFrame.Scene phase=frame.scene();
                if((want==BroadcastCues.CUTMAN && phase==LiveFrame.Scene.CORNER) ||
                   (want==BroadcastCues.MANUAL && phase==LiveFrame.Scene.TREATMENT))actual=want;
            }
        }
        if(actual==BroadcastCues.FOOTER && (request&BroadcastCues.SCOUT_CARD)!=0 && frame.scoutPrompt()){
            // Include the shadow fringe while leaving the record above it intact.
            GLES20.glUniform4f(patchBounds,frame.sourceX(66)/640f,frame.sourceX(169)/640f,344/480f,376/480f);
        }else GLES20.glUniform4f(patchBounds,0,0,0,0);
        // The retail command row is printed on the textured menu footer.
        // Continue its unprinted portion across only the commands sent below.
        for(int i=0;i<4;i++){
            int legend=i==2 && (visible&(1<<BroadcastCues.RETIRE))!=0?BroadcastCues.RETIRE:i;
            boolean routed=(actual==BroadcastCues.FOOTER || actual==BroadcastCues.EDITOR) && (visible&(1<<legend))!=0;
            starts[i]=routed?frame.sourceX(cues.centers[legend]-12)/640f:0;
            ends[i]=routed?frame.sourceX(Math.min(640,cues.centers[legend]+FooterLegend.RIGHT[legend]))/640f:0;
        }
        boolean editing=actual==BroadcastCues.EDITOR;
        GLES20.glUniform1f(footerTop,editing?418/480f:.8645f);
        boolean tabs=editing && (request>>>8)>1;
        if(tabs){
            int start=BoxerEditor.startCenter(cues.centers[3]);
            starts[0]=frame.sourceX(Math.max(start-13,cues.trackRight))/640f;
            ends[0]=frame.sourceX(start+91)/640f;
        }
        boolean field=editing && editor.field(frame,request>>>8);
        if(tabs && BoxerEditor.shoulders(frame))
            GLES20.glUniform4f(tabBounds,frame.sourceX(57)/640f,frame.sourceX(132)/640f,140/480f,158/480f);
        else if(field && editor.closingArrow>0)
            GLES20.glUniform4f(tabBounds,frame.sourceX(editor.closingArrow-11)/640f,frame.sourceX(editor.closingArrow+6)/640f,editor.top/480f,editor.bottom/480f);
        else GLES20.glUniform4f(tabBounds,0,0,0,0);
        if(field || actual==BroadcastCues.EDITOR_DIALOG)
            GLES20.glUniform4f(fieldBounds,frame.sourceX(editor.left)/640f,frame.sourceX(editor.right)/640f,editor.top/480f,editor.bottom/480f);
        else GLES20.glUniform4f(fieldBounds,0,0,0,0);
        GLES20.glUniform4fv(ribbonStart,1,starts,0);GLES20.glUniform4fv(ribbonEnd,1,ends,0);
        evaluatedRoute=request;renderedRoute=actual;
    }
    private void mapFile(){
        ByteBuffer memory=session.sharedVideo();
        if(memory!=null && sequence(memory)!=0){useMapping(memory,memory.capacity());return;}
        try(FileInputStream input=new FileInputStream(mailbox)){
            FileChannel ch=input.getChannel();long size=ch.size();
            if(size<16 || size>24L*1024*1024)return;
            ByteBuffer map=ch.map(FileChannel.MapMode.READ_ONLY,0,size).order(ByteOrder.LITTLE_ENDIAN);
            useMapping(map,size);
        }catch(IOException ignored){/* Host is still starting. */}
    }
    private void useMapping(ByteBuffer map,long size){
        if(map.getInt(12)!=0x32425246 || map.getInt(8)!=3)return;
        int stride=map.getInt(4);
        if(stride<640*480*4+8 || 16+3L*stride!=size)return;
        ByteBuffer[] views=new ByteBuffer[3];
        for(int i=0;i<3;i++){
            ByteBuffer view=map.duplicate();view.position(16+i*stride);view.limit(16+(i+1)*stride);
            views[i]=view.slice().order(ByteOrder.LITTLE_ENDIAN);
        }
        slots=views;shared=map;
    }
    @Override public void onDrawFrame(GL10 gl){
        if(shared==null)mapFile();
        int seq=shared==null?0:sequence(shared);
        if(seq!=0 && seq!=serial){
            ByteBuffer pixels=slots[(int)(Integer.toUnsignedLong(seq)%3)];
            int w=pixels.getInt(0),h=pixels.getInt(4);
            if(w>0 && h>0 && w<=1440 && h<=1080 && 8L+(long)w*h*4<=pixels.capacity()){
                int back=1-front,target=GLES20.GL_TEXTURE_2D;
                GLES20.glBindTexture(target,textures[back]);pixels.position(8);
                if(widths[back]!=w || heights[back]!=h){
                    GLES20.glTexImage2D(target,0,GLES20.GL_RGBA,w,h,0,GLES20.GL_RGBA,GLES20.GL_UNSIGNED_BYTE,pixels);
                    widths[back]=w;heights[back]=h;
                }else GLES20.glTexSubImage2D(target,0,0,0,w,h,GLES20.GL_RGBA,GLES20.GL_UNSIGNED_BYTE,pixels);
                if(Integer.toUnsignedLong(sequence(shared)-seq)<2){
                    if(lowerRoute!=BroadcastCues.NONE || renderedRoute!=BroadcastCues.NONE)
                        matchLower(new LiveFrame(pixels,w,h,seq,autoWide));
                    if(!hasFrame || widths[front]!=w || heights[front]!=h)dimensions=w+" \u00d7 "+h;
                    int shot=lowerRoute==BroadcastCues.NONE?session.cameraShot():0;
                    if(shot!=0){
                        measureCamera(camera,pixels,w,h,seq,shot,offset);
                        GLES20.glUniform3f(steadyUniform,offset[0],offset[1],.034f);
                    }else GLES20.glUniform3f(steadyUniform,0,0,0);
                    front=back;serial=seq;hasFrame=true;presented++;timing.frame(System.nanoTime(),seq);
                    long now=SystemClock.elapsedRealtime(),elapsed=now-since;
                    lastPresented=now;
                    if(elapsed>=1000){
                        output=String.format(Locale.ROOT,"%d \u00d7 %d \u00b7 %.1f fps",w,h,presented*1000.0/elapsed);
                        presented=0;since=now;
                    }
                }
            }
        }
        if(hasFrame && lowerRoute!=evaluatedRoute){
            LiveFrame still=lowerRoute==BroadcastCues.NONE?null:latestFrame();
            if(still==null || still.sequence==serial)matchLower(still);
        }
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        if(hasFrame){
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,textures[front]);
            GLES20.glUniform2f(texel,1f/widths[front],1f/heights[front]);
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4);
        }
    }
    private void schedule(boolean on){
        on=on && isAttachedToWindow() && !paused;
        if(scheduled==on)return;
        android.view.Choreographer clock=android.view.Choreographer.getInstance();
        clock.removeFrameCallback(tick);scheduled=on;cadence.reset();
        int generation=++watchGeneration;
        if(paceMode!=null){
            android.view.Window window=activity.getWindow();
            android.view.WindowManager.LayoutParams params=window.getAttributes();
            if(on && !requestedMode){
                oldMode=params.preferredDisplayModeId;params.preferredDisplayModeId=paceMode.getModeId();
                requestedMode=true;window.setAttributes(params);
            }else if(!on && requestedMode){
                if(params.preferredDisplayModeId==paceMode.getModeId()){params.preferredDisplayModeId=oldMode;window.setAttributes(params);}
                requestedMode=false;
            }
        }
        if(!on)return;
        if(vsync)clock.postFrameCallback(tick);
        else new Thread(()->watchFrames(generation),"Fight Night frame ready").start();
    }
    private void watchFrames(int generation){
        int observed=0;
        DisplayCadence arrivals=new DisplayCadence(targetFps);
        while(watchGeneration==generation){
            ByteBuffer map=shared;
            if(map==null){
                requestRender(); // The GL thread opens the mailbox once the host creates it.
                SystemClock.sleep(16);
                continue;
            }
            long result=waitForFrame(map,observed);
            if(result<0){
                post(()->{
                    if(watchGeneration==generation)
                        android.view.Choreographer.getInstance().postFrameCallback(tick);
                });
                return;
            }
            int next=(int)result;
            if(next!=observed && watchGeneration==generation){
                observed=next;
                // Full rate follows the NTSC producer. Lower targets still cap submissions.
                if(targetFps==60 || arrivals.due(System.nanoTime()))requestRender();
            }
        }
    }
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();schedule(true);}
    @Override protected void onDetachedFromWindow(){schedule(false);super.onDetachedFromWindow();}
    @Override public void onResume(){super.onResume();paused=false;schedule(true);}
    @Override public void onPause(){paused=true;timing.suspend();schedule(false);super.onPause();}
}
