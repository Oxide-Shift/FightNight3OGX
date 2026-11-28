package org.oxideshift.corner;

import android.content.SharedPreferences;
import java.util.*;

/** Alpha 84: Vulkan 1.1/1.2 drivers use the compatibility path; only drivers below 1.1 start on Software. */
public final class VulkanSupportSmoke {
    static void check(boolean ok,String what){if(!ok)throw new AssertionError(what);}
    static final int V10=(1<<22), V11=(1<<22)|(1<<12), V12=(1<<22)|(2<<12), V13=VulkanSupport.FULL, V14=(1<<22)|(4<<12)|3;

    public static void main(String[] args){
        check(V11==4198400,"crash reports' vulkan_api_version for 1.1");
        check(VulkanSupport.tooOld(V10) && !VulkanSupport.tooOld(V11) && !VulkanSupport.tooOld(V13),"only below 1.1 is too old");
        check(!VulkanSupport.tooOld(0) && !VulkanSupport.compatibility(0),"no report is left to the renderer's probe");
        check(VulkanSupport.compatibility(V11) && VulkanSupport.compatibility(V12),"1.1 and 1.2 use the compatibility path");
        check(!VulkanSupport.compatibility(V13) && !VulkanSupport.compatibility(V14) && !VulkanSupport.compatibility(V10),"1.3+ and 1.0 do not");
        check(VulkanSupport.label(V11).equals("1.1") && VulkanSupport.label(V14).equals("1.4"),"labels");
        check(VulkanSupport.notice(V10,true).contains("Vulkan 1.0") && VulkanSupport.notice(V10,true).contains("Software"),"1.0 notice");
        check(VulkanSupport.notice(V11,false).contains("compatibility path") && VulkanSupport.notice(V11,true)==null,"1.1 notice only on Vulkan");
        check(VulkanSupport.notice(V13,false)==null,"nothing to say on 1.3");

        Prefs old=new Prefs();
        check(VulkanSupport.chooseDefault(old,V10) && old.getInt("renderer",0)==VulkanSupport.RENDERER_SOFTWARE,"1.0 switches to Software");
        old.edit().putInt("renderer",0).apply();          // the player picks Vulkan again on purpose
        check(!VulkanSupport.chooseDefault(old,V10) && old.getInt("renderer",9)==0,"runs only once");

        Prefs leftOnSoftware=new Prefs();leftOnSoftware.edit().putInt("renderer",1).apply();
        check(VulkanSupport.chooseDefault(leftOnSoftware,V11) && leftOnSoftware.getInt("renderer",9)==VulkanSupport.RENDERER_VULKAN,
                "a 1.1 phone left on Software gets one try of the compatibility path");
        leftOnSoftware.edit().putInt("renderer",1).apply();   // it failed and the launcher switched back
        check(!VulkanSupport.chooseDefault(leftOnSoftware,V11) && leftOnSoftware.getInt("renderer",9)==1,"never offered twice");

        Prefs shared=new Prefs();shared.edit().putInt("renderer",3).apply();
        check(!VulkanSupport.chooseDefault(shared,V11) && shared.getInt("renderer",0)==3,"other Vulkan modes kept on 1.1");
        Prefs modern=new Prefs();modern.edit().putInt("renderer",1).apply();
        check(!VulkanSupport.chooseDefault(modern,V13) && modern.getInt("renderer",0)==1,"1.3 keeps the player's Software choice");
        Prefs unknown=new Prefs();
        check(!VulkanSupport.chooseDefault(unknown,0) && !unknown.contains("renderer"),"unreported keeps default");
        System.out.println("VulkanSupportSmoke passed");
    }

    static final class Prefs implements SharedPreferences {
        final Map<String,Object> values=new HashMap<>();
        public Map<String,?> getAll(){return values;}
        public String getString(String k,String d){Object v=values.get(k);return v==null?d:(String)v;}
        public Set<String> getStringSet(String k,Set<String> d){return d;}
        public int getInt(String k,int d){Object v=values.get(k);return v==null?d:(Integer)v;}
        public long getLong(String k,long d){Object v=values.get(k);return v==null?d:(Long)v;}
        public float getFloat(String k,float d){Object v=values.get(k);return v==null?d:(Float)v;}
        public boolean getBoolean(String k,boolean d){Object v=values.get(k);return v==null?d:(Boolean)v;}
        public boolean contains(String k){return values.containsKey(k);}
        public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l){}
        public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l){}
        public Editor edit(){
            Map<String,Object> pending=new HashMap<>();
            return new Editor(){
                public Editor putString(String k,String v){pending.put(k,v);return this;}
                public Editor putStringSet(String k,Set<String> v){pending.put(k,v);return this;}
                public Editor putInt(String k,int v){pending.put(k,v);return this;}
                public Editor putLong(String k,long v){pending.put(k,v);return this;}
                public Editor putFloat(String k,float v){pending.put(k,v);return this;}
                public Editor putBoolean(String k,boolean v){pending.put(k,v);return this;}
                public Editor remove(String k){pending.put(k,null);return this;}
                public Editor clear(){values.clear();return this;}
                public boolean commit(){apply();return true;}
                public void apply(){for(Map.Entry<String,Object> e:pending.entrySet())
                    if(e.getValue()==null)values.remove(e.getKey());else values.put(e.getKey(),e.getValue());}
            };
        }
    }
}
