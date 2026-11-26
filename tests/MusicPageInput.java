package org.oxideshift.corner;

import android.content.Context;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;

/** Run on an Android or LayoutLib UI thread with the project's imported font set. */
public final class MusicPageInput {
    private static void require(boolean condition,String message){
        if(!condition)throw new AssertionError(message);
    }
    private static View row(View view,String label){
        CharSequence text=view.getContentDescription();
        if(text!=null && text.toString().startsWith(label))return view;
        if(view instanceof ViewGroup){
            ViewGroup group=(ViewGroup)view;
            for(int i=0;i<group.getChildCount();i++){
                View match=row(group.getChildAt(i),label);
                if(match!=null)return match;
            }
        }
        return null;
    }
    private static void key(AchievementGallery page,int action,int code){
        page.handleKey(new KeyEvent(action,code),false);
    }
    public static void verify(Context context,DiscFonts fonts){
        boolean[] enabled={false,false};
        int[] opens={0},backs={0};
        AchievementGallery page=new AchievementGallery(context,fonts,new Achievements(k->false,k->{}),1920,
                null,()->backs[0]++,cue->{},new AchievementGallery.MusicSettings(){
            public boolean enabled(int i){return enabled[i];}
            public void toggle(int i){enabled[i]=!enabled[i];}
            public void openTrax(){opens[0]++;}
        });
        int exact=View.MeasureSpec.EXACTLY;
        page.measure(View.MeasureSpec.makeMeasureSpec(1920,exact),View.MeasureSpec.makeMeasureSpec(1080,exact));
        page.layout(0,0,1920,1080);
        View fight=row(page,"On During Fights"),expanded=row(page,"FN3 Expanded"),trax=row(page,"EA Trax");
        require(fight!=null && expanded!=null && trax!=null && opens[0]==0,"Three rows on entry");
        View lower=page.new LowerPage(context);
        require(row(lower,"A, Turn on")!=null,"Lower A describes the selected setting");
        fight.performClick();expanded.performClick();
        require(enabled[0] && enabled[1] && opens[0]==0,"Independent toggles");
        require(row(lower,"A, Turn off")!=null,"The toggle updates the lower action");
        page.selectPlaylist();
        require(row(lower,"A, Open")!=null && row(page,"A, Open")!=null,"Both displays describe the playlist action");
        key(page,KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_RIGHT);
        key(page,KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_BUTTON_A);
        require(opens[0]==0,"Held confirm must stay on parent");
        key(page,KeyEvent.ACTION_UP,KeyEvent.KEYCODE_BUTTON_A);
        require(opens[0]==1,"Release opens once");
        trax.performClick();
        require(opens[0]==2,"Touch opens once");
        page.selectPlaylist();
        key(page,KeyEvent.ACTION_UP,KeyEvent.KEYCODE_BUTTON_B);
        require(backs[0]==0,"Release from the child closed Music");
        key(page,KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_BUTTON_B);
        key(page,KeyEvent.ACTION_UP,KeyEvent.KEYCODE_BUTTON_B);
        require(backs[0]==1 && opens[0]==2,"Second Back leaves Music without opening the child");
    }
}

