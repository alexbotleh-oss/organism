package com.organism.app;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.*;

public class ProviderManager {
    public static class Provider {
        public final String id,name,url;
        Provider(String i,String n,String u){id=i;name=n;url=u;}
    }
    private final SharedPreferences p;
    private final List<Provider> providers=Arrays.asList(
        new Provider("chatgpt","ChatGPT","https://chatgpt.com/"),
        new Provider("qwen","Qwen","https://chat.qwen.ai/"),
        new Provider("deepseek","DeepSeek","https://chat.deepseek.com/"),
        new Provider("alice","Алиса","https://alice.yandex.ru/"),
        new Provider("gemini","Gemini","https://gemini.google.com/"),
        new Provider("claude","Claude","https://claude.ai/")
    );
    public ProviderManager(Context c){p=c.getSharedPreferences("organism_ai",Context.MODE_PRIVATE);}
    public List<Provider> all(){return providers;}
    public boolean isConnected(String id){
        if("chatgpt".equals(id))return false;
        return p.getBoolean("opened_"+id,false);
    }
    public void markOpened(String id){p.edit().putBoolean("opened_"+id,true).apply();}
}