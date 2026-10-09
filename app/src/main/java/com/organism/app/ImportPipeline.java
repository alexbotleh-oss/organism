package com.organism.app;

import android.content.*;
import android.net.Uri;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.charset.Charset;
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
            else text=decodeText(bytes);
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
        // Do not apply recipe-keyword heuristics to arbitrary imports or chat history.
        // Semantic extraction belongs to the provenance-aware M1 pipeline and must cite source spans.
        writeRaw(name,normalized);
        appendEvent("IMPORT",name,src);
        writeSnapshot(project);
    }

    private void importZip(String name,byte[] bytes)throws Exception{
        ZipInputStream z=new ZipInputStream(new ByteArrayInputStream(bytes));ZipEntry e;int n=0;long project=db.project("ORGANISM");
        while((e=z.getNextEntry())!=null){if(e.isDirectory())continue;String p=e.getName().toLowerCase(Locale.ROOT);if(!(p.endsWith(".json")||p.endsWith(".txt")||p.endsWith(".md")||p.endsWith(".html")))continue;byte[] b=readZipEntry(z);String text=decodeText(b);
            if(p.endsWith("conversations.json")||p.endsWith("chat.json")){long rawSource=db.source("CHAT_EXPORT_RAW",e.getName(),e.getName(),text,sha(text));parseChatJson(e.getName(),text,rawSource);}else if(text.length()>0)ingest("CHAT_EXPORT",e.getName(),e.getName(),text);n++;}
        z.close();db.event("IMPORT","ChatGPT ZIP завершён, файлов: "+n,project,0,0,"STATED","VERIFIED");writeSnapshot(project);
    }

    private void parseChatJson(String name,String json,long rawSourceId)throws Exception{
        JSONArray conversations=new JSONArray(json);
        long project=db.project("ORGANISM");
        for(int i=0;i<conversations.length();i++){
            JSONObject conversation=conversations.getJSONObject(i);
            String title=conversation.optString("title","ChatGPT conversation");
            String raw=conversation.toString();
            String conversationId=conversation.optString("conversation_id","");
            long source=db.source("CHAT_EXPORT_CONVERSATION",title,name,null,sha(raw),rawSourceId,conversationId);
            JSONObject mapping=conversation.optJSONObject("mapping");
            if(mapping==null||mapping.length()==0){
                db.event("CHAT_IMPORT_INCOMPLETE","Чат без mapping: "+title,project,0,source,"STATED","NOT_VERIFIED");
                continue;
            }

            // Build an explicit node index first. JSON object key iteration order is not chronology.
            LinkedHashMap<String,JSONObject> nodes=new LinkedHashMap<>();
            Iterator<String> keys=mapping.keys();
            while(keys.hasNext()){
                String nodeId=keys.next();
                JSONObject node=mapping.optJSONObject(nodeId);
                if(node!=null)nodes.put(nodeId,node);
            }

            ArrayList<String> ordered=orderChatNodes(nodes);
            String currentNode=conversation.optString("current_node","");
            String header="CHAT EXPORT INDEX v1\n"
                    +"conversation_id: "+conversation.optString("conversation_id","UNKNOWN")+"\n"
                    +"title: "+title+"\n"
                    +"current_node: "+(currentNode.isEmpty()?"UNKNOWN":currentNode)+"\n"
                    +"node_count: "+nodes.size()+"\n"
                    +"ordering: parent/children traversal; disconnected nodes sorted by message time and node id\n"
                    +"provenance_source_id: "+source+"\n"
                    +"message_nodes: stored as individually searchable memory objects\n";

            int messageCount=0;
            for(String nodeId:ordered){
                JSONObject node=nodes.get(nodeId);
                if(node==null)continue;
                String parent=jsonScalar(node.opt("parent"));
                JSONArray children=node.optJSONArray("children");
                String childIds=children==null?"[]":children.toString();
                JSONObject message=node.optJSONObject("message");
                if(message==null){
                    db.event("CHAT_NODE_IMPORTED","conversation="+title+"; node="+nodeId+
                            "; parent="+parent+"; children="+childIds+"; message=none",
                            project,0,source,"STATED","NOT_VERIFIED");
                    continue;
                }

                JSONObject author=message.optJSONObject("author");
                String role=author==null?"unknown":author.optString("role","unknown");
                String authorName=author==null?"":author.optString("name","");
                String timestamp=jsonScalar(message.opt("create_time"));
                String messageId=message.optString("id",nodeId);
                String channel=message.optString("channel","");
                JSONObject content=message.optJSONObject("content");
                String body=chatMessageText(content);

                String nodeText="[NODE id="+nodeId+" parent="+parent+" children="+childIds+
                        " current="+nodeId.equals(currentNode)+"]\\n"
                        +"message_id: "+messageId+" | role: "+role
                        +(authorName.isEmpty()?"":" | author: "+authorName)
                        +" | time: "+timestamp
                        +(channel.isEmpty()?"":" | channel: "+channel)+"\\n"
                        +role+": "+body;

                // One searchable memory object per message keeps later turns retrievable
                // instead of truncating an entire long conversation to its first characters.
                db.memory("CHAT_MESSAGE",title+" [node "+nodeId+"]",nodeText,
                        project,0,source,"STATED","NOT_VERIFIED",0.5);
                // Store structural provenance separately from semantic interpretation.
                db.event("CHAT_MESSAGE_IMPORTED","conversation="+title+"; node="+nodeId+
                        "; message="+messageId+"; parent="+parent+"; children="+childIds+
                        "; role="+role+"; time="+timestamp+"; channel="+channel,
                        project,0,source,"STATED","NOT_VERIFIED");
                messageCount++;
            }

            if(nodes.size()>0){
                db.event("CHAT_IMPORTED","Чат импортирован: "+title+
                        "; nodes="+nodes.size()+"; messages="+messageCount,
                        project,0,source,"STATED","VERIFIED");
                db.memory("CONTEXT_INDEX",title,header+"message_count: "+messageCount+"\\n",
                        project,0,source,"STATED","NOT_VERIFIED",0.5);
            }else{
                db.event("CHAT_IMPORT_INCOMPLETE","В чате не найдено ни одного узла: "+title,
                        project,0,source,"STATED","NOT_VERIFIED");
            }
        }
    }

    private ArrayList<String> orderChatNodes(LinkedHashMap<String,JSONObject> nodes){
        ArrayList<String> ordered=new ArrayList<>();
        HashSet<String> visited=new HashSet<>();
        ArrayList<String> roots=new ArrayList<>();
        for(Map.Entry<String,JSONObject> entry:nodes.entrySet()){
            String parent=jsonScalar(entry.getValue().opt("parent"));
            if(parent.isEmpty()||!nodes.containsKey(parent))roots.add(entry.getKey());
        }
        Comparator<String> byTimeThenId=(a,b)->{
            double ta=chatNodeTime(nodes.get(a)),tb=chatNodeTime(nodes.get(b));
            int time=Double.compare(ta,tb);
            return time!=0?time:a.compareTo(b);
        };
        Collections.sort(roots,byTimeThenId);
        for(String root:roots)appendChatSubtree(root,nodes,visited,ordered);

        // Include orphaned/cyclic nodes deterministically rather than silently dropping them.
        ArrayList<String> leftovers=new ArrayList<>();
        for(String id:nodes.keySet())if(!visited.contains(id))leftovers.add(id);
        Collections.sort(leftovers,byTimeThenId);
        for(String id:leftovers)appendChatSubtree(id,nodes,visited,ordered);
        return ordered;
    }

    private void appendChatSubtree(String first,LinkedHashMap<String,JSONObject> nodes,
                                   Set<String> visited,List<String> ordered){
        ArrayDeque<String> stack=new ArrayDeque<>();
        stack.push(first);
        while(!stack.isEmpty()){
            String id=stack.pop();
            if(!visited.add(id))continue;
            ordered.add(id);
            JSONObject node=nodes.get(id);
            JSONArray children=node==null?null:node.optJSONArray("children");
            if(children==null)continue;
            // Reverse-push so traversal respects the source's declared child order.
            for(int i=children.length()-1;i>=0;i--){
                String child=children.optString(i,"");
                if(!child.isEmpty()&&nodes.containsKey(child)&&!visited.contains(child))stack.push(child);
            }
        }
    }

    private double chatNodeTime(JSONObject node){
        if(node==null)return Double.MAX_VALUE;
        JSONObject message=node.optJSONObject("message");
        if(message==null)return Double.MAX_VALUE;
        Object time=message.opt("create_time");
        if(time instanceof Number)return ((Number)time).doubleValue();
        try{return Double.parseDouble(String.valueOf(time));}
        catch(Exception ignored){return Double.MAX_VALUE;}
    }

    private String jsonScalar(Object value){
        if(value==null||value==JSONObject.NULL)return "";
        return value instanceof String?(String)value:String.valueOf(value);
    }

    private String chatMessageText(JSONObject content){
        if(content==null)return "[NO CONTENT OBJECT]";
        JSONArray parts=content.optJSONArray("parts");
        if(parts==null)return content.toString();
        StringBuilder body=new StringBuilder();
        for(int i=0;i<parts.length();i++){
            if(i>0)body.append("\n");
            Object part=parts.opt(i);
            if(part instanceof String)body.append((String)part);
            else if(part==null||part==JSONObject.NULL)body.append("[NULL PART]");
            else body.append(String.valueOf(part));
        }
        return body.toString();
    }

    private byte[] readZipEntry(ZipInputStream z)throws Exception{ByteArrayOutputStream o=new ByteArrayOutputStream();byte[] b=new byte[8192];int n;while((n=z.read(b))>0)o.write(b,0,n);return o.toByteArray();}
    private String pdf(byte[] bytes)throws Exception{
        PDDocument d=PDDocument.load(new ByteArrayInputStream(bytes));
        try{
            String text=new PDFTextStripper().getText(d);
            return repairMojibake(text);
        }finally{d.close();}
    }
    private String decodeText(byte[] bytes){
        if(bytes.length>=3&&(bytes[0]&255)==0xEF&&(bytes[1]&255)==0xBB&&(bytes[2]&255)==0xBF)return new String(bytes,3,bytes.length-3,StandardCharsets.UTF_8);
        if(bytes.length>=2&&(bytes[0]&255)==0xFF&&(bytes[1]&255)==0xFE)return new String(bytes,2,bytes.length-2,StandardCharsets.UTF_16LE);
        if(bytes.length>=2&&(bytes[0]&255)==0xFE&&(bytes[1]&255)==0xFF)return new String(bytes,2,bytes.length-2,StandardCharsets.UTF_16BE);
        String utf8=new String(bytes,StandardCharsets.UTF_8);
        if(!looksBroken(utf8))return utf8;
        try{
            String cp1251=new String(bytes,Charset.forName("windows-1251"));
            if(!looksBroken(cp1251))return cp1251;
        }catch(Exception ignored){}
        return utf8;
    }
    private String repairMojibake(String text){
        if(text==null||!looksBroken(text))return text;
        try{
            String fixed=new String(text.getBytes(StandardCharsets.ISO_8859_1),StandardCharsets.UTF_8);
            if(scoreReadable(fixed)>scoreReadable(text))return fixed;
        }catch(Exception ignored){}
        return text;
    }
    private boolean looksBroken(String s){
        if(s==null||s.isEmpty())return false;
        int bad=0;
        for(int i=0;i<s.length();i++){
            char c=s.charAt(i);
            if(c=='\uFFFD'||c=='Ã'||c=='Â'||c=='Ð'||c=='Ñ'||c=='Р'||c=='С')bad++;
        }
        return bad>=2 && bad*10>=s.length();
    }
    private int scoreReadable(String s){
        int score=0;
        for(int i=0;i<s.length();i++){
            char c=s.charAt(i);
            if(c=='\uFFFD'||c=='Ã'||c=='Â'||c=='Ð'||c=='Ñ')score-=3;
            if(c>='А'&&c<='я')score+=2;
            if(Character.isLetterOrDigit(c)||Character.isWhitespace(c))score++;
        }
        return score;
    }
    private byte[] readBytes(InputStream in)throws Exception{if(in==null)throw new IOException("Не удалось открыть источник");try{ByteArrayOutputStream o=new ByteArrayOutputStream();byte[] b=new byte[8192];int n;while((n=in.read(b))>0)o.write(b,0,n);return o.toByteArray();}finally{if(!(in instanceof ZipInputStream))try{in.close();}catch(Exception ignored){}}}
    private String sha(String s)throws Exception{byte[] b=MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));StringBuilder x=new StringBuilder();for(byte v:b)x.append(String.format(Locale.US,"%02x",v));return x.toString();}
    private String title(String name,String text){String first=text.split("\n")[0].trim();return first.length()>80?first.substring(0,80):first.isEmpty()?name:first;}
    private void writeRaw(String name,String text)throws Exception{File dir=new File(ctx.getFilesDir(),"raw");dir.mkdirs();String safe=name.replaceAll("[^A-Za-z0-9А-Яа-я._-]","_");FileOutputStream o=new FileOutputStream(new File(dir,System.currentTimeMillis()+"_"+safe+".raw.txt"));o.write(text.getBytes(StandardCharsets.UTF_8));o.close();}
    private void appendEvent(String kind,String name,long src)throws Exception{File f=new File(ctx.getFilesDir(),"events.jsonl");FileOutputStream o=new FileOutputStream(f,true);String line="{\"event_id\":\""+db.id("EVT")+"\",\"timestamp\":\""+db.now()+"\",\"kind\":\""+kind+"\",\"source_id\":"+src+",\"description\":\""+name.replace("\"","'")+"\"}\n";o.write(line.getBytes(StandardCharsets.UTF_8));o.close();}
    private void writeSnapshot(long project)throws Exception{File f=new File(ctx.getFilesDir(),"PROJECT_MEMORY.md");String state=db.queryOne("SELECT summary FROM project_states WHERE is_current=1 ORDER BY id DESC LIMIT 1",null);String text="# ORGANISM PROJECT MEMORY\n\n- project: ORGANISM\n- current_state: "+state+"\n- active_task: see tasks table\n- status: ACTIVE\n- last_update: "+db.now()+"\n- memory_objects: "+db.count("memory_objects")+"\n- experiences: "+db.count("experiences")+"\n- sources: "+db.count("sources")+"\n";new FileOutputStream(f).write(text.getBytes(StandardCharsets.UTF_8));}
}
