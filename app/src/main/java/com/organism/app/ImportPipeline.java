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
            String name=displayName(uri);String type=ctx.getContentResolver().getType(uri);
            boolean zip=name.toLowerCase(Locale.ROOT).endsWith(".zip")||"application/zip".equalsIgnoreCase(type)
                    ||"application/x-zip-compressed".equalsIgnoreCase(type);
            if(zip){
                File rawZip=saveIncomingZip(uri,name);
                importZip(rawZip,name);
                l.done("Импорт ZIP завершён: "+name+". "+lastZipSummary);
                return;
            }
            byte[] bytes=readBytes(ctx.getContentResolver().openInputStream(uri));
            String lower=name.toLowerCase(Locale.ROOT);String text;
            if(lower.endsWith(".pdf")||"application/pdf".equals(type))text=pdf(bytes);
            else text=decodeText(bytes);
            bytes=null;
            if(isChatGptHtmlExport(name,type,text)){
                importChatHtmlExport(name,uri.toString(),text);
                l.done("ChatGPT HTML обработан: "+name+"; диалоги разобраны на отдельные сообщения.");
                return;
            }
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
        db.event("IMPORT", "Источник принят: "+name,project,0,src,"STATED","NOT_VERIFIED");
        String normalized=text.replace("\r","").trim();
        if(normalized.isEmpty()){db.event("ERROR","Источник пустой: "+name,project,0,src,"UNKNOWN","NOT_VERIFIED");return;}
        String title=title(name,normalized);
        writeRaw(name,normalized);
        // A role-labelled exported transcript must not become one giant NOTE.
        // Preserve the original source, then create individually retrievable message records.
        int importedMessages=ingestRoleTranscript(name,normalized,project,src);
        if(importedMessages==0){
            // Arbitrary text stays a source-backed note; no keyword-based semantic guessing.
            db.memory("NOTE",title,normalized,project,0,src,"STATED","NOT_VERIFIED",0.5);
        }else{
            db.event("CHAT_IMPORTED","Диалоговый текст разобран на сообщения: "+name+"; messages="+importedMessages,project,0,src,"STATED","VERIFIED");
            db.memory("CONTEXT_INDEX",title,"CHAT TRANSCRIPT INDEX v1\nsource_id: "+src+"\nmessage_count: "+importedMessages+"\nraw_source_preserved: true\nsemantic_extraction: pending\n",project,0,src,"STATED","NOT_VERIFIED",0.5);
        }
        appendEvent("IMPORT",name,src);
        writeSnapshot(project);
    }

    private int ingestRoleTranscript(String name,String text,long project,long source)throws Exception{
        String[] lines=text.split("\n",-1);
        int userMarkers=0,assistantMarkers=0;
        for(String line:lines){String role=line.trim().toLowerCase(Locale.ROOT);if("user".equals(role))userMarkers++;else if("chatgpt".equals(role)||"assistant".equals(role))assistantMarkers++;}
        // Conservative detection: require a repeated role-labelled structure, not one incidental word.
        if(userMarkers<3||assistantMarkers<3||Math.min(userMarkers,assistantMarkers)<Math.max(3,Math.max(userMarkers,assistantMarkers)/8))return 0;
        String conversationTitle=title(name,text);
        StringBuilder body=new StringBuilder();String role=null;int imported=0;int startLine=1;
        for(int i=0;i<lines.length;i++){
            String line=lines[i].trim();String lower=line.toLowerCase(Locale.ROOT);
            String nextRole="user".equals(lower)?"USER":("chatgpt".equals(lower)||"assistant".equals(lower))?"ASSISTANT":null;
            if(nextRole!=null){
                if(role!=null&&body.toString().trim().length()>0){
                    String content=body.toString().trim();
                    String msg="[TRANSCRIPT_MESSAGE source_id="+source+" line_start="+startLine+" line_end="+i+"]\nrole: "+role+"\n"+content;
                    db.memory("CHAT_MESSAGE",conversationTitle+" ["+role+" #"+(imported+1)+"]",msg,project,0,source,"STATED","NOT_VERIFIED",0.5);
                    db.event("CHAT_MESSAGE_IMPORTED","source="+name+"; role="+role+"; sequence="+(imported+1)+"; line_start="+startLine+"; line_end="+i,project,0,source,"STATED","NOT_VERIFIED");
                    imported++;
                }
                role=nextRole;body.setLength(0);startLine=i+2;
            }else if(role!=null){if(body.length()>0)body.append("\n");body.append(lines[i]);}
        }
        if(role!=null&&body.toString().trim().length()>0){
            String content=body.toString().trim();
            String msg="[TRANSCRIPT_MESSAGE source_id="+source+" line_start="+startLine+" line_end="+lines.length+"]\nrole: "+role+"\n"+content;
            db.memory("CHAT_MESSAGE",conversationTitle+" ["+role+" #"+(imported+1)+"]",msg,project,0,source,"STATED","NOT_VERIFIED",0.5);
            db.event("CHAT_MESSAGE_IMPORTED","source="+name+"; role="+role+"; sequence="+(imported+1)+"; line_start="+startLine+"; line_end="+lines.length,project,0,source,"STATED","NOT_VERIFIED");
            imported++;
        }
        return imported;
    }

    private volatile String lastZipSummary="";

    private String displayName(Uri uri){
        android.database.Cursor c=null;
        try{c=ctx.getContentResolver().query(uri,new String[]{android.provider.OpenableColumns.DISPLAY_NAME},null,null,null);if(c!=null&&c.moveToFirst()){String n=c.getString(0);if(n!=null&&!n.trim().isEmpty())return n;}}
        catch(Exception ignored){}finally{if(c!=null)c.close();}
        return String.valueOf(uri.getLastPathSegment());
    }

    private String shaFile(File file)throws Exception{
        MessageDigest md=MessageDigest.getInstance("SHA-256");
        try(InputStream in=new BufferedInputStream(new FileInputStream(file))){byte[] b=new byte[64*1024];int n;while((n=in.read(b))!=-1)md.update(b,0,n);}
        StringBuilder out=new StringBuilder();for(byte v:md.digest())out.append(String.format(Locale.US,"%02x",v));return out.toString();
    }

    private File saveIncomingZip(Uri uri,String name)throws Exception{
        File dir=new File(ctx.getFilesDir(),"raw");if(!dir.exists()&&!dir.mkdirs())throw new IOException("Не удалось создать каталог RAW");
        String safe=name.replaceAll("[^A-Za-z0-9А-Яа-я._-]","_");
        File outFile=new File(dir,System.currentTimeMillis()+"_"+safe+".source.zip");
        try(InputStream in=ctx.getContentResolver().openInputStream(uri);FileOutputStream out=new FileOutputStream(outFile)){
            if(in==null)throw new IOException("Не удалось открыть ZIP");byte[] buffer=new byte[64*1024];int read;
            while((read=in.read(buffer))!=-1){out.write(buffer,0,read);}out.getFD().sync();
        }
        return outFile;
    }

    private void importZip(File zipFile,String displayName)throws Exception{
        int files=0,conversations=0,skippedLargeAux=0,messagesBefore=(int)db.count("memory_objects");boolean foundConversationFile=false;
        long project=db.project("ORGANISM");
        long rawSource=db.source("CHAT_EXPORT_RAW",displayName,zipFile.getAbsolutePath(),null,shaFile(zipFile));
        try(ZipInputStream z=new ZipInputStream(new BufferedInputStream(new FileInputStream(zipFile)))){
            ZipEntry entry;
            while((entry=z.getNextEntry())!=null){
                if(entry.isDirectory())continue;
                String path=entry.getName();String lower=path.toLowerCase(Locale.ROOT);
                if(lower.endsWith("conversations.json")||lower.equals("chat.json")||lower.endsWith("/chat.json")){
                    foundConversationFile=true;files++;
                    conversations+=parseChatJsonStream(path,z,displayName,rawSource);
                    z.closeEntry();
                }else if(lower.endsWith(".txt")||lower.endsWith(".md")||lower.endsWith(".html")){
                    // Auxiliary files are optional. If one is large, drain and skip it rather than aborting
                    // the entire import (official exports can contain large shared-conversation HTML files).
                    byte[] b=readZipEntryBounded(z,8*1024*1024);
                    if(b==null){skippedLargeAux++;files++;z.closeEntry();continue;}
                    String text=decodeText(b);
                    if(text.length()>0)ingest("CHAT_EXPORT",path,zipFile.getAbsolutePath()+"!/"+path,text);
                    files++;z.closeEntry();
                }else{z.closeEntry();}
            }
        }
        if(!foundConversationFile)throw new IOException("В ZIP не найден conversations.json. Выбери исходный ZIP «Экспорт данных ChatGPT», а не отдельный HTML/другой архив.");
        int messagesAfter=(int)db.count("memory_objects");
        db.event("IMPORT","ChatGPT ZIP завершён: "+displayName+"; files="+files+"; conversations="+conversations+"; memory_delta="+(messagesAfter-messagesBefore),project,0,rawSource,"STATED","VERIFIED");
        appendEvent("CHAT_ZIP_IMPORTED",displayName,rawSource);writeSnapshot(project);
        lastZipSummary="Найдено бесед: "+conversations+"; обработано файлов: "+files+(skippedLargeAux>0?"; пропущено крупных вспомогательных файлов: "+skippedLargeAux:"")+". Исходный ZIP сохранён в RAW.";
    }

    private int parseChatJsonStream(String path,InputStream stream,String archiveName,long rawSourceId)throws Exception{
        final long project=db.project("ORGANISM");
        BufferedReader reader=new BufferedReader(new InputStreamReader(stream,StandardCharsets.UTF_8),64*1024);
        boolean inString=false,escaped=false,started=false,closed=false;int objectDepth=0,imported=0;StringBuilder object=new StringBuilder();int ch;
        while((ch=reader.read())!=-1){char c=(char)ch;
            if(!started){if(c=='['){started=true;}continue;}
            if(closed)break;
            if(inString){if(objectDepth>0)object.append(c);if(escaped)escaped=false;else if(c=='\\')escaped=true;else if(c=='"')inString=false;continue;}
            if(c=='"'){inString=true;if(objectDepth>0)object.append(c);continue;}
            if(c=='{' ){if(objectDepth==0)object.setLength(0);objectDepth++;object.append(c);continue;}
            if(objectDepth>0){object.append(c);if(c=='}'){objectDepth--;if(objectDepth==0){
                JSONObject conversation=new JSONObject(object.toString());
                String title=conversation.optString("title","ChatGPT conversation");
                String conversationId=conversation.optString("conversation_id",conversation.optString("id",""));
                long source=db.source("CHAT_EXPORT_CONVERSATION",title,archiveName+"!/"+path,null,sha(conversation.toString()),rawSourceId,conversationId);
                parseChatConversation(path,conversation,source,project);imported++;
            }}continue;}
            if(c==']'){closed=true;break;}
        }
        if(!started)throw new IOException("В файле "+path+" не найден JSON-массив бесед");
        if(!closed||objectDepth!=0)throw new IOException("Файл "+path+" обрезан: импорт остановлен на повреждённом JSON");
        return imported;
    }

    /**
     * Reads an optional ZIP entry up to maxBytes. Returns null for an oversized entry,
     * but always drains it to its end so ZipInputStream can safely continue with later entries.
     * The required conversations.json path is parsed by the separate streaming parser.
     */
    private byte[] readZipEntryBounded(InputStream in,int maxBytes)throws Exception{
        ByteArrayOutputStream out=new ByteArrayOutputStream(Math.min(maxBytes,64*1024));
        byte[] buffer=new byte[8192];int n;boolean oversized=false;
        while((n=in.read(buffer))!=-1){
            if(!oversized){
                if(out.size()+n>maxBytes){oversized=true;out.reset();}
                else out.write(buffer,0,n);
            }
        }
        return oversized?null:out.toByteArray();
    }

    private interface JsonObjectHandler { void handle(String jsonObject) throws Exception; }

    private void parseChatJson(String name,String json,long rawSourceId)throws Exception{
        parseChatJsonRange(name,json,0,json.length(),rawSourceId);
    }

    private void parseChatJsonRange(String name,String json,int start,int end,long rawSourceId)throws Exception{
        final long project=db.project("ORGANISM");
        forEachTopLevelJsonObject(json,start,end, objectText ->
                parseChatConversation(name,new JSONObject(objectText),rawSourceId,project));
    }

    // Stream one conversation object at a time. Parsing the whole 35+ MB export as a JSONArray
    // creates a second huge object graph and can exhaust Android's heap.
    private void forEachTopLevelJsonObject(String json,int startArray,int endExclusive,JsonObjectHandler handler)throws Exception{
        if(startArray<0||startArray>=endExclusive||json.charAt(startArray)!='[')
            throw new JSONException("В ChatGPT JSON не найден массив диалогов");
        boolean inString=false,escaped=false;
        int arrayDepth=0,objectDepth=0,objectStart=-1;
        boolean closed=false;
        for(int i=startArray;i<endExclusive;i++){
            char c=json.charAt(i);
            if(inString){
                if(escaped)escaped=false;
                else if(c=='\\')escaped=true;
                else if(c=='"')inString=false;
                continue;
            }
            if(c=='"'){inString=true;continue;}
            if(c=='['){arrayDepth++;continue;}
            if(c==']'){
                arrayDepth--;
                if(arrayDepth==0){closed=true;break;}
                continue;
            }
            if(c=='{'&&(objectDepth>0||arrayDepth==1)){
                if(objectDepth==0)objectStart=i;
                objectDepth++;
            }else if(c=='}'&&objectDepth>0){
                objectDepth--;
                if(objectDepth==0&&objectStart>=0){
                    handler.handle(json.substring(objectStart,i+1));
                    objectStart=-1;
                }
            }
        }
        if(!closed||objectDepth!=0)throw new JSONException("ChatGPT JSON обрезан или имеет неверную структуру");
    }

    private boolean isChatGptHtmlExport(String name,String type,String text){
        if(text==null)return false;
        String lower=name.toLowerCase(Locale.ROOT);
        return (lower.endsWith(".html")||"text/html".equalsIgnoreCase(type))
                && text.contains("ChatGPT Data Export")
                && (text.contains("var jsonData")||text.contains("let jsonData")||text.contains("const jsonData"));
    }

    private void importChatHtmlExport(String name,String path,String html)throws Exception{
        int[] jsonRange=findChatGptJsonDataRange(html);
        if(jsonRange==null)throw new IOException("В HTML не найден массив jsonData из экспорта ChatGPT");
        String checksum=sha(html);
        long rawSource=db.source("CHAT_EXPORT_RAW",name,path,null,checksum);
        writeRaw(name,html);
        parseChatJsonRange(name,html,jsonRange[0],jsonRange[1],rawSource);
        long project=db.project("ORGANISM");
        db.event("CHAT_HTML_EXPORT_IMPORTED","HTML-экспорт ChatGPT разобран потоково: "+name,project,0,rawSource,"STATED","VERIFIED");
        appendEvent("CHAT_HTML_EXPORT_IMPORTED",name,rawSource);
        writeSnapshot(project);
    }

    private int[] findChatGptJsonDataRange(String html)throws IOException{
        String[] markers={"var jsonData","let jsonData","const jsonData"};
        int marker=-1;
        for(String m:markers){marker=html.indexOf(m);if(marker>=0)break;}
        if(marker<0)return null;
        int equals=html.indexOf('=',marker);
        if(equals<0)return null;
        int start=equals+1;
        while(start<html.length()&&Character.isWhitespace(html.charAt(start)))start++;
        if(start>=html.length()||html.charAt(start)!='[')return null;
        boolean inString=false,escaped=false;int depth=0;
        for(int i=start;i<html.length();i++){
            char c=html.charAt(i);
            if(inString){
                if(escaped)escaped=false;
                else if(c=='\\')escaped=true;
                else if(c=='"')inString=false;
                continue;
            }
            if(c=='"'){inString=true;continue;}
            if(c=='[')depth++;
            else if(c==']'&&--depth==0)return new int[]{start,i+1};
        }
        throw new IOException("Массив jsonData в HTML-экспорте обрезан");
    }

    private void parseChatConversation(String name,JSONObject conversation,long rawSourceId,long project)throws Exception{
        String title=conversation.optString("title","ChatGPT conversation");
        String raw=conversation.toString();
        String conversationId=conversation.optString("conversation_id",conversation.optString("id",""));
        long source=db.source("CHAT_EXPORT_CONVERSATION",title,name,null,sha(raw),rawSourceId,conversationId);
        JSONObject mapping=conversation.optJSONObject("mapping");
        if(mapping==null||mapping.length()==0){
            db.event("CHAT_IMPORT_INCOMPLETE","Чат без mapping: "+title,project,0,source,"STATED","NOT_VERIFIED");
            return;
        }
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
                +"conversation_id: "+(conversationId.isEmpty()?"UNKNOWN":conversationId)+"\n"
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
            String body=chatMessageText(message.optJSONObject("content"));
            String nodeText="[CHAT_MESSAGE conversation_id="+conversationId+" source_id="+source+
                    " node_id="+nodeId+" parent="+parent+" children="+childIds+
                    " current="+nodeId.equals(currentNode)+"]\n"
                    +"message_id: "+messageId+" | role: "+role
                    +(authorName.isEmpty()?"":" | author: "+authorName)
                    +" | time: "+timestamp
                    +(channel.isEmpty()?"":" | channel: "+channel)+"\n"
                    +role+": "+body;
            db.memory("CHAT_MESSAGE",title+" [conversation "+(conversationId.isEmpty()?"UNKNOWN":conversationId)+" node "+nodeId+"]",nodeText,
                    project,0,source,"STATED","NOT_VERIFIED",0.5);
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
            db.memory("CONTEXT_INDEX",title,header+"message_count: "+messageCount+"\n",
                    project,0,source,"STATED","NOT_VERIFIED",0.5);
        }else{
            db.event("CHAT_IMPORT_INCOMPLETE","В чате не найдено ни одного узла: "+title,
                    project,0,source,"STATED","NOT_VERIFIED");
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
