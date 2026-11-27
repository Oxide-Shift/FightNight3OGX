package org.oxideshift.corner;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.*;
import android.view.*;
import android.widget.*;
import java.io.File;
import java.io.IOException;

/** Retail title artwork beside the player's disc and bout settings. */
final class FightMenu extends LinearLayout {
    static final int INK=GameInk.DARK, PAPER=GameInk.TEXT, MUTED=GameInk.MUTED;
    static final int[] HEIGHTS={480,720,1080,0,900};
    private static final int GOLD=GameInk.GOLD, LINE=0xff71604a, TILE=0xff302821, RED=0xff873a2c;
    private final SharedPreferences prefs;
    private final Poster poster;
    private final ScrollView settingsPane;
    private final SetupBackdrop setupArt=new SetupBackdrop();
    private final LinearLayout panel,details,graphicsDetails;
    private final TextView heading,state,qualityHint;
    private final ProgressBar progress;
    private final BuildStages stages;
    private final TextView progressDetail;
    private static final java.util.regex.Pattern COUNTS=java.util.regex.Pattern.compile("(\\d+) / (\\d+)");
    private final Button play,disc,save,exportSave,settings,advanced;
    final Button card,defaultSave,updates;
    final Switch soundtrack;
    final TextView backupStatus;
    final Button saveFolder;
    final Spinner rumble;
    final Spinner renderer,resolution,aspect,displayRoute;
    Runnable displayRouteChanged;
    /** Runs after the player changes the button map; the new map is already in prefs ("padMap"). */
    Runnable padMapChanged;
    final PresetStrip quality,pacing;
    final Switch antiAlias;
    final ToggleButton vsync;
    private GraphicsPreset preset;
    private boolean sessionActive;
    private DiscFonts faces;
    private boolean landscape,requestedArt;
    private Bitmap cornerArt;
    private Bitmap[] mainMenuArt;
    Bitmap[] mainMenuArt(){return mainMenuArt;}
    private Bitmap[] rootArt;
    Bitmap[] rootArt(){return rootArt;}
    private Bitmap[] trainingArt;
    private CareerArt careerArt;
    CareerArt careerArt(){return careerArt;}
    private Bitmap[] traxArt;
    Bitmap[] traxArt(){return traxArt;}

    FightMenu(Context c,SharedPreferences prefs,DiscFonts initialFonts,String version,Runnable pick,Runnable importSave,Runnable writeSave,Runnable launch,
              Runnable onCard,Runnable useDefault,Runnable checkUpdates){
        super(c);this.prefs=prefs;faces=initialFonts;setBackgroundColor(INK);
        poster=new Poster(c);addView(poster);
        settingsPane=new ScrollView(c);settingsPane.setFillViewport(true);addView(settingsPane);
        panel=column();panel.setGravity(Gravity.CENTER_VERTICAL);
        panel.setPadding(dp(22),dp(13),dp(22),dp(13));settingsPane.addView(panel);
        // Sections opening and closing, and rows that come and go with the session, move instead of jumping.
        android.animation.LayoutTransition moves=new android.animation.LayoutTransition();
        moves.enableTransitionType(android.animation.LayoutTransition.CHANGING);
        moves.setDuration(180);panel.setLayoutTransition(moves);
        // The rail lives on the panel's overlay and follows controller focus (touch mode hides it).
        panel.getOverlay().add(rail);
        panel.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->rail.setBounds(0,0,r-l,b-t));
        getViewTreeObserver().addOnGlobalFocusChangeListener((oldFocus,newFocus)->{
            if(newFocus!=null && !newFocus.isInTouchMode() && isDescendant(newFocus))rail.moveTo(newFocus);
            else rail.fade(0f);
        });
        getViewTreeObserver().addOnTouchModeChangeListener(touch->{if(touch)rail.fade(0f);});
        settingsPane.setBackground(setupArt);
        LinearLayout title=row();title.setGravity(Gravity.CENTER_VERTICAL);
        heading=label("Fight setup",31,PAPER);heading.setTypeface(faces.stencil);title.addView(heading,new LayoutParams(0,-2,1));
        if(!version.isEmpty())title.addView(label(version,13,MUTED));
        state=label("",15,MUTED);state.setPadding(0,0,0,dp(8));panel.addView(state);
          stages=new BuildStages(c);stages.setVisibility(GONE);
          LayoutParams stageSlot=new LayoutParams(-1,dp(44));stageSlot.bottomMargin=dp(6);panel.addView(stages,stageSlot);
          progress=new ProgressBar(c,null,android.R.attr.progressBarStyleHorizontal);
          progress.setMax(1000);progress.setProgressTintList(ColorStateList.valueOf(GOLD));
          progress.setIndeterminateTintList(ColorStateList.valueOf(GOLD));progress.setVisibility(GONE);
          panel.addView(progress,new LayoutParams(-1,dp(8)));
          progressDetail=label("",13,MUTED);progressDetail.setPadding(0,dp(4),0,dp(8));
          progressDetail.setVisibility(GONE);panel.addView(progressDetail);
        play=button("Enter the ring",launch,true);panel.addView(play);
        disc=button("Choose original Xbox ISO, ZIP or 7z",pick,true);panel.addView(disc);
        updates=button("Check for updates",checkUpdates,false);panel.addView(updates);

        LinearLayout rateHeader=row();rateHeader.setGravity(Gravity.CENTER_VERTICAL);
        TextView rateLabel=sectionLabel("FRAME RATE");
        rateHeader.addView(rateLabel,new LayoutParams(0,-2,1));
        vsync=new ToggleButton(c,null,android.R.attr.borderlessButtonStyle);
        vsync.setAllCaps(false);vsync.setTextOn("VSync  On");vsync.setTextOff("VSync  Off");
        vsync.setTextSize(15);vsync.setTextColor(PAPER);vsync.setTypeface(faces.body);
        vsync.setPadding(dp(10),0,dp(10),0);vsync.setBackground(states(TILE,0xff4c3c2c));
        vsync.setChecked(prefs.getBoolean("vsync",true));vsync.setText(vsync.isChecked()?vsync.getTextOn():vsync.getTextOff());
        vsync.setOnCheckedChangeListener((button,on)->prefs.edit().putBoolean("vsync",on).apply());
        LayoutParams syncSlot=new LayoutParams(dp(100),dp(48));syncSlot.setMargins(0,dp(4),0,dp(3));
        rateHeader.addView(vsync,syncSlot);
        int savedTarget=prefs.getInt("frameTargetIndex",DisplayCadence.legacyIndex(prefs.getInt("pacing",0)));
        pacing=new PresetStrip(c,"Frame rate",new String[]{"30 FPS","45 FPS","60 FPS"},
                savedTarget>=0 && savedTarget<DisplayCadence.TARGETS.length?savedTarget:2,
                index->prefs.edit().putInt("frameTargetIndex",index).apply());
        panel.addView(pacing);
        section("GRAPHICS QUALITY");
        preset=GraphicsPreset.stored(prefs.getInt("quality",1));
        quality=new PresetStrip(c,"Graphics quality",new String[]{"Low","Medium","High"},preset.tier,
                tier->savePreset(GraphicsPreset.select(tier,tier==2)));
        panel.addView(quality);
        qualityHint=label("",14,MUTED);qualityHint.setPadding(0,dp(4),0,dp(6));panel.addView(qualityHint);
        LinearLayout format=row();
        resolution=choice(format,"Resolution",new String[]{"480p","720p","1080p","Fit screen","900p upscaled"},"resolution",0);
        aspect=choice(format,"Aspect ratio",new String[]{"16:9 widescreen","4:3 original"},"aspect",0);

        graphicsDetails=column();advanced=disclosure("Advanced graphics",graphicsDetails);
        LinearLayout backend=new LinearLayout(c);graphicsDetails.addView(backend);
        renderer=choice(backend,"Renderer",new String[]{"Vulkan","Software (480p)","Vulkan (general shaders)","Vulkan (shared GPU frames)"},"renderer",0);
        LinearLayout route=new LinearLayout(c);graphicsDetails.addView(route);
        displayRoute=choice(route,"Companion display",new String[]{"Use second screen when available","Use main screen only"},"displayRoute",0);
        antiAlias=new Switch(c);antiAlias.setText("Edge smoothing (2\u00d7 MSAA)");antiAlias.setTextColor(PAPER);
        antiAlias.setPadding(dp(9),0,dp(6),0);antiAlias.setMinHeight(dp(48));graphicsDetails.addView(antiAlias);
        antiAlias.setOnCheckedChangeListener((v,on)->{
            if(v.isEnabled() && on!=(preset.samples==2))savePreset(GraphicsPreset.select(preset.tier,on));
        });
        Switch counter=new Switch(c);
        counter.setText("Show FPS counter");counter.setTextColor(PAPER);counter.setTypeface(faces.body);
        counter.setPadding(dp(9),0,dp(6),0);counter.setMinHeight(dp(48));
        counter.setChecked(prefs.getBoolean("showFpsCounter",false));
        counter.setOnCheckedChangeListener((button,on)->prefs.edit().putBoolean("showFpsCounter",on).apply());
        graphicsDetails.addView(counter);
        // Alpha 80: GPU renders one frame while the game builds the next. Higher mean FPS on the Thor, one extra
        // frame (~17 ms) before a frame is shown, and a worse single worst frame at Palace, so it is opt-in.
        Switch pipelined=new Switch(c);
        // Thor, 2026-11-25: commentary audio chops with this on and is clean without it.
        pipelined.setText("Pipelined frames (experimental: smoother video, +1 frame delay, can make commentary choppy)");pipelined.setTextColor(PAPER);pipelined.setTypeface(faces.body);
        pipelined.setPadding(dp(9),0,dp(6),0);pipelined.setMinHeight(dp(48));
        pipelined.setChecked(prefs.getBoolean("pipelinedFrames",false));
        pipelined.setOnCheckedChangeListener((button,on)->prefs.edit().putBoolean("pipelinedFrames",on).apply());
        graphicsDetails.addView(pipelined);
        updateGraphics();

        details=column();settings=disclosure("Save & sound",details);
        save=button("Import Xbox save",importSave,false);details.addView(save);
        exportSave=button("Export save",writeSave,false);details.addView(exportSave);
        defaultSave=button("Use default save",useDefault,false);details.addView(defaultSave);
        soundtrack=new Switch(c);soundtrack.setText("Menu music");soundtrack.setTextColor(PAPER);
        soundtrack.setTypeface(faces.body);soundtrack.setMinHeight(dp(46));
        soundtrack.setChecked(prefs.getBoolean("music",true));details.addView(soundtrack);
        saveFolder=button("Choose save backup folder",()->{},false);details.addView(saveFolder);
        backupStatus=label("Automatic backups \u00b7 Downloads / Fight Night Round 3",13,MUTED);details.addView(backupStatus);
        LinearLayout strength=new LinearLayout(c);details.addView(strength);
        rumble=choice(strength,"Controller rumble",new String[]{"Off","Light","Medium","Full"},
                "rumbleLevel",prefs.getBoolean("rumble",true)?3:0);
        disclosure("Controller buttons",new ButtonMapSection(c,prefs,faces.body,()->{if(padMapChanged!=null)padMapChanged.run();}));
        LinearLayout privacy=column();disclosure("Privacy",privacy);
        Switch usage=new Switch(c);
        usage.setText("Anonymous player count");usage.setTextColor(PAPER);usage.setTypeface(faces.body);
        usage.setPadding(dp(9),0,dp(6),0);usage.setMinHeight(dp(48));
        usage.setChecked(DeviceCheckin.enabled(prefs));
        usage.setOnCheckedChangeListener((button,on)->prefs.edit().putBoolean(DeviceCheckin.SETTING,on).apply());
        privacy.addView(usage);
        TextView usageNote=label("While the app is open, it tells the project server every 10 minutes that a copy is running: a random "
                +"install ID, the app and Android versions, the device type (Thor, phone, tablet…) and whether the game is running. "
                +"No name, account, location, model or saves.",13,MUTED);
        usageNote.setPadding(dp(9),0,dp(6),dp(6));privacy.addView(usageNote);
        card=button("Open scorecard",onCard,false);panel.addView(card);
        applyFonts(panel);
        arrange(c.getResources().getConfiguration().orientation==2);
    }

    private int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private LinearLayout column(){
        LinearLayout box=new LinearLayout(getContext());box.setOrientation(VERTICAL);return box;
    }
    private LinearLayout row(){
        LinearLayout row=new LinearLayout(getContext());row.setBaselineAligned(false);panel.addView(row);return row;
    }
    private TextView label(String s,int size,int color){
        TextView view=new TextView(getContext());view.setText(s);view.setTextSize(size);
        view.setTextColor(color);view.setTypeface(faces.body);return view;
    }
    private TextView sectionLabel(String text){
        TextView caption=label(text,12,GOLD);caption.setLetterSpacing(.10f);
        caption.setTag("section");caption.setTypeface(faces.stencil);return caption;
    }
    private void section(String text){
        TextView caption=sectionLabel(text);
        caption.setPadding(0,dp(10),0,dp(4));panel.addView(caption);
    }
    private Button disclosure(String text,LinearLayout contents){
        Button b=button(text,null,false);
        Runnable update=()->{
            boolean expanded=contents.getVisibility()==VISIBLE;
            b.setText(text);b.setStateDescription(expanded?"Expanded":"Collapsed");
            Drawable arrow=new Drawable(){
                final Paint pen=new Paint(Paint.ANTI_ALIAS_FLAG);
                @Override public void draw(Canvas canvas){
                    Rect r=getBounds();float y1=expanded?r.bottom*.63f:r.bottom*.37f,y2=r.bottom-y1;
                    pen.setColor(GOLD);pen.setStrokeWidth(dp(2));pen.setStrokeCap(Paint.Cap.ROUND);
                    canvas.drawLine(r.right*.2f,y1,r.right*.5f,y2,pen);canvas.drawLine(r.right*.5f,y2,r.right*.8f,y1,pen);
                }
                @Override public void setAlpha(int value){}
                @Override public void setColorFilter(ColorFilter filter){}
                @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
            };
            arrow.setBounds(0,0,dp(16),dp(16));b.setCompoundDrawablesRelative(null,null,arrow,null);
        };
        b.setOnClickListener(v->{contents.setVisibility(contents.getVisibility()==VISIBLE?GONE:VISIBLE);update.run();});
        LayoutParams lp=new LayoutParams(-1,-2);lp.topMargin=dp(5);panel.addView(b,lp);
        contents.setPadding(dp(4),0,dp(4),dp(4));panel.addView(contents);contents.setVisibility(GONE);update.run();return b;
    }
    int qualityValue(){return preset.ordinal();}
    private void savePreset(GraphicsPreset selected){
        preset=selected;prefs.edit().putInt("quality",qualityValue()).apply();updateGraphics();
    }
    private void updateGraphics(){
        boolean software=renderer.getSelectedItemPosition()==1;
        resolution.setEnabled(!sessionActive && !software);
        antiAlias.setEnabled(!sessionActive && !software && preset.tier!=0);
        antiAlias.setChecked(!software && preset.samples==2);
        String hint;
        if(software)hint="Software renderer \u00b7 480p";
        else if(preset==GraphicsPreset.LOW)hint="Nearest-neighbor scaling \u00b7 edge smoothing off";
        else hint=(preset.tier==2?"Sharpened image \u00b7 4\u00d7 filtering":"Linear scaling \u00b7 2\u00d7 filtering")
                +(preset.samples==2?" \u00b7 2\u00d7 MSAA":" \u00b7 edge smoothing off");
        if(renderer.getSelectedItemPosition()==3)hint+=" \u00b7 Experimental shared presentation";
        if(!hint.contentEquals(qualityHint.getText()))qualityHint.setText(hint);
    }
    private GradientDrawable surface(int fill,int outline){
        GradientDrawable drawable=new GradientDrawable();
        drawable.setColor(fill);drawable.setStroke(dp(1),outline);return drawable;
    }
    private StateListDrawable states(int fill,int selected){
        StateListDrawable bg=new StateListDrawable();
        bg.addState(new int[]{-android.R.attr.state_enabled},surface(0xff27231f,0xff443b32));
        bg.addState(new int[]{android.R.attr.state_focused},surface(selected,GOLD));
        bg.addState(new int[]{android.R.attr.state_pressed},surface(selected,GOLD));
        bg.addState(new int[0],surface(fill,fill==RED?0xffc16a4d:LINE));return bg;
    }
    private Button button(String text,Runnable action,boolean primary){
        Button b=new Button(getContext(),null,android.R.attr.borderlessButtonStyle);
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x44f8d58a),
                new BillSelect(getResources().getDisplayMetrics().density,primary),null));
        b.setText(text);b.setAllCaps(false);b.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);
        b.setMinHeight(dp(48));b.setMinimumHeight(dp(48));
        b.setTextSize(primary?20:16);b.setTypeface(primary?faces.stencil:faces.body);
        b.setPadding(dp(19),dp(3),dp(12),dp(4));b.setTextColor(PAPER);
        b.setStateListAnimator(pressAndLift());
        if(action!=null)b.setOnClickListener(v->action.run());return b;
    }
    /** Pressed rows dip, controller-focused rows lift slightly, everything else rests (the framework skips the
     *  animation when animations are off). */
    static android.animation.StateListAnimator pressAndLift(){
        android.animation.StateListAnimator states=new android.animation.StateListAnimator();
        states.addState(new int[]{android.R.attr.state_pressed},scaleTo(.975f,70));
        states.addState(new int[]{android.R.attr.state_focused},scaleTo(1.018f,140));
        states.addState(new int[0],scaleTo(1f,110));
        return states;
    }
    private static android.animation.Animator scaleTo(float scale,long ms){
        android.animation.ObjectAnimator a=android.animation.ObjectAnimator.ofPropertyValuesHolder((Object)null,
                android.animation.PropertyValuesHolder.ofFloat("scaleX",scale),android.animation.PropertyValuesHolder.ofFloat("scaleY",scale));
        a.setDuration(ms);a.setInterpolator(SheetMotion.settle());return a;
    }
    /** A gold rail on the left edge that glides to whichever row has controller focus. */
    private final FocusRail rail=new FocusRail();
    private final class FocusRail extends Drawable {
        final Paint pen=new Paint(Paint.ANTI_ALIAS_FLAG);
        final RectF at=new RectF(),from=new RectF(),to=new RectF();
        float shown;android.animation.ValueAnimator glide;
        void moveTo(View row){
            if(row==null || row.getParent()==null){fade(0f);return;}
            Rect r=new Rect();row.getDrawingRect(r);
            try{panel.offsetDescendantRectToMyCoords(row,r);}catch(IllegalArgumentException notInside){fade(0f);return;}
            to.set(r.left-dp(9),r.top+dp(4),r.left-dp(5),r.bottom-dp(4));
            if(shown<=0f || !SheetMotion.on()){at.set(to);fade(1f);invalidateSelf();return;}
            from.set(at);
            if(glide!=null)glide.cancel();
            glide=android.animation.ValueAnimator.ofFloat(0f,1f).setDuration(170);
            glide.setInterpolator(SheetMotion.settle());
            glide.addUpdateListener(a->{float f=(float)a.getAnimatedValue();
                at.set(from.left+(to.left-from.left)*f,from.top+(to.top-from.top)*f,from.right+(to.right-from.right)*f,from.bottom+(to.bottom-from.bottom)*f);
                invalidateSelf();});
            glide.start();fade(1f);
        }
        void fade(float target){shown=target;invalidateSelf();}
        @Override public void draw(Canvas c){
            if(shown<=0f || at.isEmpty())return;
            pen.setShader(new LinearGradient(0,at.top,0,at.bottom,0xfff6bf55,0xffaf3927,Shader.TileMode.CLAMP));
            c.drawRoundRect(at,dp(2),dp(2),pen);pen.setShader(null);
        }
        @Override public void setAlpha(int a){}
        @Override public void setColorFilter(ColorFilter f){}
        @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
    }
    private boolean isDescendant(View v){
        for(android.view.ViewParent p=v.getParent();p!=null;p=p.getParent())if(p==panel)return true;
        return false;
    }
    private boolean entered;
    /** First time the setup screen is on screen: rows rise in, top to bottom (capped so the last ones are not late). */
    @Override protected void onAttachedToWindow(){
        super.onAttachedToWindow();
        if(entered || !SheetMotion.on())return;
        entered=true;
        post(()->{
            int k=0;
            for(int i=0;i<panel.getChildCount();i++){
                View child=panel.getChildAt(i);
                if(child.getVisibility()!=VISIBLE)continue;
                child.setAlpha(0f);child.setTranslationY(dp(14));
                child.animate().alpha(1f).translationY(0).setStartDelay(30L*Math.min(k++,12)+60)
                        .setDuration(SheetMotion.ENTER_MS).setInterpolator(SheetMotion.settle()).start();
            }
        });
    }
    private void arrange(boolean wide){
        landscape=wide;setOrientation(wide?HORIZONTAL:VERTICAL);
        poster.setLayoutParams(wide?new LayoutParams(0,-1,.44f):new LayoutParams(-1,dp(146)));
        settingsPane.setLayoutParams(wide?new LayoutParams(0,-1,.56f):new LayoutParams(-1,0,1));
    }
    @Override protected void onMeasure(int w,int h){
        boolean wide=MeasureSpec.getSize(w)>MeasureSpec.getSize(h);
        if(wide!=landscape)arrange(wide);
        super.onMeasure(w,h);
    }
    private Spinner choice(LinearLayout row,String title,String[] options,String key,int initial){
        LinearLayout tile=column();tile.setAddStatesFromChildren(true);tile.setGravity(Gravity.CENTER_VERTICAL);
        tile.setBackground(states(TILE,0xff55432f));tile.setPadding(dp(9),dp(4),dp(4),dp(3));
        LayoutParams lp=new LayoutParams(0,-2,1);lp.topMargin=dp(4);
        if(row.getChildCount()>0)lp.leftMargin=dp(5);row.addView(tile,lp);
        TextView caption=label(title,13,MUTED);tile.addView(caption);
        Spinner picker=new Spinner(getContext(),Spinner.MODE_DROPDOWN);picker.setContentDescription(title);
        picker.setBackgroundTintList(ColorStateList.valueOf(GOLD));picker.setMinimumHeight(dp(30));
        picker.setPopupBackgroundDrawable(surface(TILE,LINE));tile.addView(picker,new LayoutParams(-1,-2));
        caption.setOnClickListener(v->{if(picker.isEnabled())picker.performClick();});caption.setFocusable(false);
        ArrayAdapter<String> adapter=new ArrayAdapter<String>(getContext(),android.R.layout.simple_spinner_item,options){
            private View option(int index,View recycled,boolean dropdown){
                TextView t=recycled instanceof TextView?(TextView)recycled:new TextView(getContext());
                t.setGravity(Gravity.CENTER_VERTICAL);t.setPadding(dp(4),0,dp(4),0);
                t.setText(getItem(index));t.setTextColor(PAPER);t.setTextSize(16);t.setTypeface(faces.body);
                t.setSingleLine(false);
                if(dropdown){t.setMinHeight(dp(44));t.setGravity(Gravity.CENTER_VERTICAL);}
                return t;
            }
            @Override public View getView(int index,View old,ViewGroup parent){
                return option(index,old,false);
            }
            @Override public View getDropDownView(int index,View old,ViewGroup parent){
                return option(index,old,true);
            }
        };
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);picker.setAdapter(adapter);
        picker.setSelection(Math.max(0,Math.min(options.length-1,prefs.getInt(key,initial))));
        picker.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
            public void onItemSelected(AdapterView<?> p,View v,int index,long id){
                boolean changed=prefs.getInt(key,initial)!=index;
                prefs.edit().putInt(key,index).apply();
                if(changed && "displayRoute".equals(key) && displayRouteChanged!=null)displayRouteChanged.run();
                if("renderer".equals(key) && antiAlias!=null)updateGraphics();
            }
            public void onNothingSelected(AdapterView<?> p){}
        });
        return picker;
    }
    static String buildPhase(String message){
        if(message.startsWith("Linking") || message.startsWith("Saving prepared") || message.startsWith("Installing disc") || message.startsWith("Game ready"))
            return "4/4 \u00b7 Finish install";
        if(message.startsWith("Compiling") || message.startsWith("Preparing shared") || message.startsWith("Reusing compiled") || message.startsWith("Retrying interrupted") || message.startsWith("Resuming saved"))
            return "3/4 \u00b7 Compile";
        if(message.startsWith("Finding") || message.startsWith("Identifying") || message.startsWith("Analyzing") || message.startsWith("Translating") ||
                message.startsWith("Writing translated") || message.startsWith("Applying game fixes"))
            return "2/4 \u00b7 Translate";
        return "1/4 \u00b7 Prepare";
    }
    void showBuildProgress(String message,long started){
        int visibility=started>0?VISIBLE:GONE;
        if(progress.getVisibility()!=visibility){
            progress.setVisibility(visibility);progressDetail.setVisibility(visibility);stages.setVisibility(visibility);
        }
        if(started==0)return;
        stages.font(faces.stencil);stages.stage(BuildStages.stageOf(buildPhase(message)));
        java.util.regex.Matcher counts=COUNTS.matcher(message);
        String amount="";
        if(counts.find() && Long.parseLong(counts.group(2))>0){
            long completed=Long.parseLong(counts.group(1)),total=Long.parseLong(counts.group(2));
            int value=(int)Math.min(1000,completed*1000/total);
            progress.setIndeterminate(false);progress.setProgress(value);
            amount=value/10+"% of this stage  \u00b7  ";
        }else progress.setIndeterminate(true);
        long seconds=Math.max(0,(android.os.SystemClock.elapsedRealtime()-started)/1000);
        String detail=buildPhase(message)+"  \u00b7  "+amount+String.format(java.util.Locale.ROOT,"%d:%02d elapsed",seconds/60,seconds%60);
        if(!detail.contentEquals(progressDetail.getText()))progressDetail.setText(detail);
        progress.setContentDescription(message);
    }

    void showState(String message,boolean ready,boolean busy,boolean active,boolean canPlay,
                   boolean customSave,boolean hasLower,boolean needsBuild,boolean startupFailed,boolean repair){
        if(!message.contentEquals(state.getText()))state.setText(message);
        play.setVisibility((ready&&!repair)||active||startupFailed?VISIBLE:GONE);play.setEnabled(active||((ready||startupFailed)&&!busy&&canPlay));
        String action=startupFailed?"Startup details":active?"Resume bout":needsBuild?"Prepare game":"Enter the ring";if(!action.contentEquals(play.getText()))play.setText(action);
        disc.setVisibility(active||!ready||repair?VISIBLE:GONE);disc.setEnabled(active||!busy);
        String pick=active?"End session":repair?"Repair disc installation":"Choose original Xbox ISO, ZIP or 7z";
        if(!pick.contentEquals(disc.getText()))disc.setText(pick);
        if(active!=disc.isSelected()){
            disc.setSelected(active);disc.setBackground(new RippleDrawable(ColorStateList.valueOf(0x44f8d58a),
                    states(active?TILE:RED,active?0xff59452f:0xffb25039),null));
        }
        saveFolder.setEnabled(!busy&&!active);updates.setEnabled(!busy&&!active);save.setEnabled(!busy&&!active);exportSave.setEnabled(!busy&&!active);defaultSave.setVisibility(customSave?VISIBLE:GONE);
        defaultSave.setEnabled(!busy&&!active);aspect.setEnabled(!active);quality.setEnabled(!active);
        pacing.setEnabled(!active);vsync.setEnabled(!active);
        sessionActive=active;renderer.setEnabled(!active);updateGraphics();
        card.setVisibility(hasLower?GONE:VISIBLE);
    }
    DiscFonts fonts(){return faces;}
    void openAdvancedGraphics(){
        if(graphicsDetails.getVisibility()!=VISIBLE)advanced.performClick();
    }
    Bitmap cornerArt(){return cornerArt;}
    Bitmap[] trainingArt(){return trainingArt;}

    private Bitmap art(File game,String name){
        try{
            return Bitmap.createBitmap(DiscPoster.read(game,name),DiscPoster.WIDTH,DiscPoster.HEIGHT,Bitmap.Config.ARGB_8888);
        }catch(IOException ignored){return null;}
    }
    private void applyFonts(View v){
        if(v instanceof TextView)((TextView)v).setTypeface(v==play||v==heading||v==disc||v instanceof RadioButton||"section".equals(v.getTag())?faces.stencil:faces.body);
        if(v instanceof ViewGroup){
            ViewGroup g=(ViewGroup)v;
            for(int i=0;i<g.getChildCount();i++)applyFonts(g.getChildAt(i));
        }
    }
    void loadDiscPoster(File game){
        if(requestedArt)return;
        requestedArt=true;
        DiscFonts current=faces;
        new Thread(()->{
            DiscFonts fonts=current==DiscFonts.FALLBACK?DiscFonts.load(game):current;
            post(()->{
                faces=fonts;applyFonts(panel);
                for(Spinner picker:new Spinner[]{renderer,resolution,aspect,rumble,displayRoute})
                    ((ArrayAdapter<?>)picker.getAdapter()).notifyDataSetChanged();
            });
            Bitmap title=art(game,"xldstr.bin"),photo=art(game,"xldout.bin"),portrait=art(game,"xpreload.bin");
            Bitmap[] photos=null;
            try{
                int[][] pixels=TraxArt.training(game);photos=new Bitmap[pixels.length];
                for(int i=0;i<photos.length;i++)photos[i]=Bitmap.createBitmap(pixels[i],TraxArt.CAMP_WIDTH[i],TraxArt.CAMP_WIDTH[i],Bitmap.Config.ARGB_8888);
            }catch(IOException ignored){}
            final Bitmap[] camp=photos;
            CareerArt career=new CareerArt(game);
            Bitmap[] music=null;
            try{
                int[][] pixels=TraxArt.load(game);music=new Bitmap[pixels.length];
                for(int i=0;i<music.length;i++)music[i]=Bitmap.createBitmap(pixels[i],TraxArt.WIDTH[i],64,Bitmap.Config.ARGB_8888);
            }catch(IOException ignored){}
            final Bitmap[] sprites=music;
            Bitmap[] mainSprites=null;
            try{
                int[][] pixels=TraxArt.mainMenu(game,DiscFonts.ogxStencil(game));
                mainSprites=new Bitmap[pixels.length];
                for(int i=0;i<pixels.length;i++)mainSprites[i]=Bitmap.createBitmap(pixels[i],
                        TraxArt.MENU_WIDTH[i],TraxArt.MENU_HEIGHT[i],Bitmap.Config.ARGB_8888);
            }catch(IOException ignored){}
            final Bitmap[] importedMenu=mainSprites;
            Bitmap[] bills=RootIllustration.load(game,camp,importedMenu);
            post(()->{
                mainMenuArt=importedMenu;rootArt=bills;
                careerArt=career;trainingArt=camp;traxArt=sprites;cornerArt=photo;poster.setArt(title);setupArt.portrait=portrait;setupArt.invalidateSelf();
            });
        },"fight-artwork").start();
    }

    /** Keep the walkout portrait fixed behind the scrollable settings. */
    private static final class SetupBackdrop extends Drawable {
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
        private final Rect crop=new Rect(56,0,584,354);
        private final RectF position=new RectF();
        private LinearGradient shade,fade;
        private Bitmap portrait;
        @Override protected void onBoundsChange(Rect b){
            if(b.isEmpty())return;
            float w=b.width(),h=b.height(),size=Math.min(w*.88f,h*.93f);
            position.set(b.right-size,b.top-size*.19f,b.right,b.top+size*(354f/528f-.19f));
            shade=new LinearGradient(b.left,0,b.right,0,new int[]{0xff172023,0xdb172023,0x64172023,0x6c111a1d},
                    new float[]{0,.38f,.76f,1},Shader.TileMode.CLAMP);
            fade=new LinearGradient(0,position.top+position.height()*.45f,0,position.bottom,
                    0x00172023,0xff172023,Shader.TileMode.CLAMP);
        }
        @Override public void draw(Canvas c){
            Rect b=getBounds();if(b.isEmpty())return;
            paint.setShader(null);paint.setColor(0xff172023);c.drawRect(b,paint);
            if(portrait!=null){
                paint.setColor(-1);c.drawBitmap(portrait,crop,position,paint);
                paint.setShader(shade);c.drawRect(b,paint);
                paint.setShader(fade);c.drawRect(position,paint);paint.setShader(null);
            }else{
                // Three ropes and a corner post also distinguish an unimported disc.
                float w=b.width(),h=b.height();paint.setColor(0xff273337);
                paint.setStrokeWidth(Math.max(2,w*.005f));
                for(int i=0;i<3;i++)c.drawLine(b.left,b.top+h*(.18f+i*.12f),b.right,b.top+h*(.045f+i*.115f),paint);
                c.drawRect(b.right-w*.09f,b.top,b.right-w*.074f,b.top+h*.46f,paint);
            }
        }
        @Override public void setAlpha(int alpha){}
        @Override public void setColorFilter(ColorFilter filter){}
        @Override public int getOpacity(){return PixelFormat.OPAQUE;}
    }

    private final class Poster extends View {
        final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
        final Matrix transform=new Matrix(),drift=new Matrix();
        static final long LIVE_MS=20000;
        long shownAt,lastFrame,heldAt=-1;
        Bitmap artwork;
        LinearGradient topFade,bottomFade;
        float artTop,artBottom;
        Poster(Context c){super(c);setContentDescription("Fight Night Round 3");}
        void setArt(Bitmap image){
            if(image!=null)artwork=Bitmap.createBitmap(image,0,0,image.getWidth(),416);
            positionArt(getWidth(),getHeight());invalidate();
        }
        @Override protected void onSizeChanged(int w,int h,int oldWidth,int oldHeight){positionArt(w,h);}
        private void positionArt(int w,int h){
            if(w<=0 || h<=0 || artwork==null)return;
            float scale=Math.min(Math.max(w/640f,h/416f),Math.min(w/520f,h/285f));
            artTop=h*.49f-160*scale;artBottom=artTop+artwork.getHeight()*scale;
            transform.setScale(scale,scale);transform.postTranslate((w-640*scale)*.5f,artTop);
            float fade=Math.min(dp(60),(artBottom-artTop)*.18f);
            topFade=new LinearGradient(0,artTop,0,artTop+fade,0xff211912,0x00211912,Shader.TileMode.CLAMP);
            bottomFade=new LinearGradient(0,artBottom-fade,0,artBottom,0x00211912,0xff211912,Shader.TileMode.CLAMP);
        }
        @Override protected void onDraw(Canvas c){
            int w=getWidth(),h=getHeight();
            c.drawColor(0xff211912);
            if(artwork!=null && topFade!=null){
                paint.setColor(-1);paint.setShader(null);
                long t=android.os.SystemClock.uptimeMillis();
                // Plays for LIVE_MS each time Fight Setup comes back on screen, then holds, so the window can go idle
                // (Thor sweeps read the screen through uiautomator, which waits for idle).
                if(t-lastFrame>1000)shownAt=t;
                lastFrame=t;
                boolean moving=SheetMotion.on() && isShown() && t-shownAt<LIVE_MS;
                if(!moving && heldAt<0)heldAt=t;
                if(moving)heldAt=-1;
                else t=heldAt;
                if(SheetMotion.on()){
                    // A slow push-in and drift over 22 s, centred on the title (frozen where it was once held).
                    float phase=(float)(.5-.5*Math.cos(2*Math.PI*(t%22000)/22000.0));
                    drift.set(transform);
                    drift.postScale(1f+.045f*phase,1f+.045f*phase,w*.5f,(artTop+artBottom)*.5f);
                    drift.postTranslate(-w*.012f*phase,0);
                    c.drawBitmap(artwork,drift,paint);
                }else c.drawBitmap(artwork,transform,paint);
                // Every 7 s a band of light crosses the title art.
                float sweep=moving?(t%7000)/1300f:2f;
                if(sweep<1f){
                    float band=w*.22f,x=-band+sweep*(w+band*2);
                    paint.setShader(new LinearGradient(x-band,artTop,x+band,artBottom,
                            new int[]{0x00fff3d6,0x38fff3d6,0x00fff3d6},null,Shader.TileMode.CLAMP));
                    c.drawRect(0,Math.max(0,artTop),w,Math.min(h,artBottom),paint);paint.setShader(null);
                }
                if(moving)postInvalidateOnAnimation();
                if(artTop>0){paint.setShader(topFade);c.drawRect(0,artTop,w,Math.min(h,artTop+dp(60)),paint);}
                if(artBottom<h){paint.setShader(bottomFade);c.drawRect(0,Math.max(0,artBottom-dp(60)),w,artBottom,paint);}
                paint.setShader(null);
            }else{
                paint.setShader(new LinearGradient(0,0,w,h,0xff693323,0xff191513,Shader.TileMode.CLAMP));
                paint.setColor(-1);c.drawRect(0,0,w,h,paint);paint.setShader(null);
                paint.setTypeface(faces.stencil);paint.setColor(PAPER);paint.setTextAlign(Paint.Align.CENTER);
                paint.setTextSize(Math.min(w*.19f,h*.28f));float textWidth=paint.measureText("FIGHT NIGHT");
                if(textWidth>w*.88f)paint.setTextSize(paint.getTextSize()*w*.88f/textWidth);
                c.drawText("FIGHT NIGHT",w*.5f,h*.51f,paint);
                paint.setTextSize(Math.min(w*.09f,h*.12f));paint.setColor(GOLD);c.drawText("ROUND 3",w*.5f,h*.68f,paint);
            }
            paint.setColor(LINE);
            if(landscape)c.drawRect(w-dp(1),0,w,h,paint);else c.drawRect(0,h-dp(1),w,h,paint);
        }
    }
}
