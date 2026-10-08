package com.organism.app;

import android.content.*;
import android.net.Uri;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.*;
import org.json.*;
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.text.PDFTextStripper;

public class ImportPipeline {
    public interface Listener { void done(String message); void fail(String message); }
    private final Context ctx; private final Db db;
    public ImportPipeline(Context c,Db d){ctx=c;db=d;PDFBoxResourceLoader.init(c.getApplicationContext());}

    public void importUri(Uri uri,Listener l){
        new Thread(()->{try{
            String name=String.valueOf(uri.getLastPathSegment());String type=ctx.getContentResolver().getType(uri);
            byte[] bytes=readBytes(ctx.getContentResolver().openInputStream(uri));
            if(name.toLowerCase(Locale.ROOT).endsWith(".zip")||"application/zip".equals(type)){importZip(name,bytes);l.done("ChatGPT ZIP обработан: "+name);return;}
            String lower=name.toLowerCase(Locale.ROOT);String text;
            if(lower.endsWith(".pdf")||"application/pdf".equals(type))text=pdf(bytes);
            else text=new String(bytes,StandardCharsets.UTF_8);
            ingest("FILE",name,uri.toString(),text);l.done("Источник импортирован: "+name);}
        catch(Exception e){l.fail(e.getMessage()==null?e.toString():e.getMessage());}}).start();
    }

    public void importUrl(String url,Listener l){
        new Thread(()->{try{
            HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();c.setConnectTimeout(20000);c.setReadTimeout(30000);c.setRequestProperty("User-Agent","ORGANISM/0.2");int code=c.getResponseCode();if(code>=400)throw new IOException("HTTP "+code);
            String text=new String(readBytes(c.getInputStream()),StandardCharsets.UTF_8);ingest("URL",url,url,text);l.done("URL импортирован: "+url);
        }catch(Exception e){l.fail(e.getMessage()==null?e.toString():e.getMessage());}}).start();
    }

    public void importText(String name,String text,Listener l){
        new Thread(()->{try{ingest("USER_INPUT",name,"",text);l.done("Текст добавлен в базу.");}catch(Exception e){l.fail(e.toString());}}).start();
    }

    private void ingest(String sourceType,String name,String path,String text)throws Exception{
        if(text==null)text="";String checksum=sha(text);long src=db.source(sourceType,name,path,text,checksum);long project=db.project("ORGANISM");
        db.event("IMPORT", "Источник принят: "+name,project,0,src,"STATED","VERIFIED");
        String normalized=text.replace("\r","").trim();
        if(normalized.isEmpty()){db.event("ERROR","Источник пустой: "+name,project,0,src,"STATED","VERIFIED");return;}
        String title=title(name,normalized);
        long mem=db.memory("NOTE",title,normalized,project,0,src,"STATED","NOT_VERIFIED",0.5);
        extractStructure(normalized,project,src,mem,title);
        writeRaw(name,normalized);
        appendEvent("IMPORT",name,src);
        writeSnapshot(project);
    }

    private void extractStructure(String text,long project,long src,long mem,String title){
        String[] lines=text.split("\n");StringBuilder ingredients=new StringBuilder(),steps=new StringBuilder(),tips=new StringBuilder(),bju=new StringBuilder();
        boolean ing=false,step=false,tip=false;
        for(String raw:lines){
            String x=raw.trim();if(x.isEmpty())continue;String lo=x.toLowerCase(Locale.ROOT);
            if(lo.matches(".*(ингредиент|ingredients|состав).*")){ing=true;step=false;tip=false;continue;}
            if(lo.matches(".*(приготов|порядок|инструкц|шаг|steps|directions).*")){step=true;ing=false;tip=false;continue;}
            if(lo.matches(".*(совет|tips|примечан|подсказ).*")){tip=true;ing=false;step=false;continue;}
            if(lo.matches(".*(бжу|кбжу|белк|жир|углевод|калори|kcal|protein|fat|carb).*"))bju.append(x).append("\n");
            else if(ing)ingredients.append(x).append("\n");else if(step)steps.append(x).append("\n");else if(tip)tips.append(x).append("\n");
        }
        if(ingredients.length()>0){long m=db.memory("FACT","Ингредиенты: "+title,ingredients.toString(),project,0,src,"STATED","NOT_VERIFIED",0.5);db.relation(mem,m,"DERIVED_FROM",src);tag(m,"domain","ingredients",src);}
        if(steps.length()>0){long m=db.memory("FACT","Приготовление: "+title,steps.toString(),project,0,src,"STATED","NOT_VERIFIED",0.5);db.relation(mem,m,"DERIVED_FROM",src);tag(m,"domain","preparation",src);}
        if(tips.length()>0){long m=db.memory("NOTE","Советы: "+title,tips.toString(),project,0,src,"STATED","NOT_VERIFIED",0.5);db.relation(mem,m,"DERIVED_FROM",src);tag(m,"domain","tips",src);}
        if(bju.length()>0){long m=db.memory("FACT","БЖУ/КБЖУ: "+title,bju.toString(),project,0,src,"STATED","NOT_VERIFIED",0.5);db.relation(mem,m,"DERIVED_FROM",src);tag(m,"domain","nutrition",src);}
    }

    private void tag(long mem,String dim,String value,long src){android.content.ContentValues v=new android.content.ContentValues();v.put("memory_id",mem);v.put("dimension",dim);v.put("value",value);v.put("normalized_value",value.toLowerCase(Locale.ROOT));v.put("confidence",0.5);v.put("source_id",src);v.put("created_at",db.now());db.getWritableDatabase().insert("memory_tags",null,v);}

    private void importZip(String name,byte[] bytes)throws Exception{
        ZipInputStream z=new ZipInputStream(new ByteArrayInputStream(bytes));ZipEntry e;int n=0;long project=db.project("ORGANISM");
        while((e=z.getNextEntry())!=null){if(e.isDirectory())continue;String p=e.getName().toLowerCase(Locale.ROOT);if(!(p.endsWith(".json")||p.endsWith(".txt")||p.endsWith(".md")||p.endsWith(".html")))continue;byte[] b=readZipEntry(z);String text=new String(b,StandardCharsets.UTF_8);
            if(p.endsWith("conversations.json")||p.endsWith("chat.json"))parseChatJson(e.getName(),text);else if(text.length()>0)ingest("CHAT_EXPORT",e.getName(),e.getName(),text);n++;}
        z.close();db.event("IMPORT","ChatGPT ZIP завершён, файлов: "+n,project,0,0,"STATED","VERIFIED");writeSnapshot(project);
    }

    private void parseChatJson(String name,String json)throws Exception{
        JSONArray arr=new JSONArray(json);long project=db.project("ORGANISM");
        for(int i=0;i<arr.length();i++){JSONObject c=arr.getJSONObject(i);String title=c.optString("title","ChatGPT conversation");String raw=c.toString();long src=db.source("CHAT_EXPORT",title,name,raw,sha(raw));StringBuilder transcript=new StringBuilder();JSONObject map=c.optJSONObject("mapping");
            if(map!=null){Iterator<String> keys=map.keys();while(keys.hasNext()){JSONObject node=map.optJSONObject(keys.next());if(node==null)continue;JSONObject msg=node.optJSONObject("message");if(msg==null)continue;JSONObject author=msg.optJSONObject("author");String role=author==null?"unknown":author.optString("role","unknown");JSONObject content=msg.optJSONObject("content");if(content==null)continue;JSONArray parts=content.optJSONArray("parts");if(parts!=null)for(int j=0;j<parts.length();j++)if(parts.optString(j,null)!=null)transcript.append(role).append(": ").append(parts.optString(j)).append("\n");}}
            if(transcript.length()>0){db.event("USER_MESSAGE","Импортирован чат: "+title,project,0,src,"STATED","VERIFIED");db.memory("CONTEXT",title,transcript.toString(),project,0,src,"STATED","NOT_VERIFIED",0.5);}
        }
    }

    private byte[] readZipEntry(ZipInputStream z)throws Exception{ByteArrayOutputStream o=new ByteArrayOutputStream();byte[] b=new byte[8192];int n;while((n=z.read(b))>0)o.write(b,0,n);return o.toByteArray();}\n    private String pdf(byte[] bytes)throws Exception{PDDocument d=PDDocument.load(new ByteArrayInputStream(bytes));try{return new PDFTextStripper().getText(d);}finally{d.close();}}
    private byte[] readBytes(InputStream in)throws Exception{if(in==null)throw new IOException("Не удалось открыть источник");try{ByteArrayOutputStream o=new ByteArrayOutputStream();byte[] b=new byte[8192];int n;while((n=in.read(b))>0)o.write(b,0,n);return o.toByteArray();}finally{if(!(in instanceof ZipInputStream))try{in.close();}catch(Exception ignored){}}}
    private String sha(String s)throws Exception{byte[] b=MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));StringBuilder x=new StringBuilder();for(byte v:b)x.append(String.format(Locale.US,"%02x",v));return x.toString();}
    private String title(String name,String text){String first=text.split("\n")[0].trim();return first.length()>80?first.substring(0,80):first.isEmpty()?name:first;}
    private void writeRaw(String name,String text)throws Exception{File dir=new File(ctx.getFilesDir(),"raw");dir.mkdirs();String safe=name.replaceAll("[^A-Za-z0-9А-Яа-я._-]","_");FileOutputStream o=new FileOutputStream(new File(dir,System.currentTimeMillis()+"_"+safe+".raw.txt"));o.write(text.getBytes(StandardCharsets.UTF_8));o.close();}
    private void appendEvent(String kind,String name,long src)throws Exception{File f=new File(ctx.getFilesDir(),"events.jsonl");FileOutputStream o=new FileOutputStream(f,true);String line="{\"event_id\":\""+db.id("EVT")+"\",\"timestamp\":\""+db.now()+"\",\"kind\":\""+kind+"\",\"source_id\":"+src+",\"description\":\""+name.replace("\"","'")+"\"}\n";o.write(line.getBytes(StandardCharsets.UTF_8));o.close();}
    private void writeSnapshot(long project)throws Exception{File f=new File(ctx.getFilesDir(),"PROJECT_MEMORY.md");String state=db.queryOne("SELECT summary FROM project_states WHERE is_current=1 ORDER BY id DESC LIMIT 1",null);String text="# ORGANISM PROJECT MEMORY\n\n- project: ORGANISM\n- current_state: "+state+"\n- active_task: see tasks table\n- status: ACTIVE\n- last_update: "+db.now()+"\n- memory_objects: "+db.count("memory_objects")+"\n- experiences: "+db.count("experiences")+"\n- sources: "+db.count("sources")+"\n";new FileOutputStream(f).write(text.getBytes(StandardCharsets.UTF_8));}
}
