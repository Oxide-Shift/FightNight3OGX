package org.oxideshift.corner;

import java.util.HashMap;

/** Give each menu its own place in the tip rotation, including on a return visit. */
final class MenuTips {
    static final long INTERVAL=11000;
    private final HashMap<String,Integer> last=new HashMap<>();
    private String topic;
    private long next;
    private int index;
    String select(String key,String[] tips,long now){
        if(!key.equals(topic) || now>=next){
            Integer previous=last.get(key);
            index=previous==null?0:(previous+1)%tips.length;
            topic=key;last.put(key,index);next=now+INTERVAL;
        }
        return tips[index];
    }
    boolean due(long now){return topic!=null && now>=next;}
    void leave(){topic=null;}
}
