package com.organism.app;

import android.database.Cursor;
import org.json.*;
import java.util.*;

/**
 * ORGANISM Context Engine v0.3 foundation.
 * Selects context conditionally: state -> task -> memory -> experience -> relations -> provenance.
 * No item is treated as fact solely because it was retrieved.
 */
public final class ContextEngine {
    public static final class Candidate {
        String id,title,content,status,verification;
        double confidence,score;
        Candidate(String i,String t,String c,String s,String v,double cf,double sc){id=i;title=t;content=c;status=s;verification=v;confidence=cf;score=sc;}
    }
    private final Db db;
    public ContextEngine(Db db){this.db=db;}

    public String build(String query){
        String q=norm(query);
        long project=db.project("ORGANISM");
        String state=db.queryOne("SELECT summary FROM project_states WHERE project_id=? AND is_current=1 ORDER BY id DESC LIMIT 1",new String[]{""+project});
        StringBuilder out=new StringBuilder();
        out.append("CONTEXT SNAPSHOT v0.3\n");
        out.append("PROJECT: ORGANISM\n");
        out.append("STATE: ").append(nvl(state,"UNKNOWN")).append("\n");
        out.append("SELECTION POLICY: applicability + confidence + task/state relevance + provenance + relation strength; conflicts reduce priority.\n\n");

        out.append("ACTIVE TASKS:\n");
        Cursor t=db.query("SELECT logical_id,title,status,description,priority FROM tasks WHERE project_id=? AND status IN ('OPEN','IN_PROGRESS','WAITING') ORDER BY priority DESC,updated_at DESC LIMIT 12",new String[]{""+project});
        int taskN=0; while(t.moveToNext()){out.append("- ").append(t.getString(0)).append(" | ").append(t.getString(1)).append(" | ").append(t.getString(2)).append(" | ").append(nvl(t.getString(3),"")).append("\n");taskN++;} t.close();
        if(taskN==0) out.append("- none\n");

        // Preserve immediate conversational continuity. User messages are direct reports;
        // model outputs remain explicitly unverified and are never treated as experience.
        out.append("\nRECENT PROJECT DIALOGUE (chronological; last 12 messages):\n");
        ArrayList<String> recentDialogue=new ArrayList<>();
        Cursor h=db.query("SELECT kind,description,occurred_at FROM events WHERE project_id=? AND kind IN ('USER_MESSAGE','MODEL_OUTPUT','ERROR') ORDER BY id DESC LIMIT 12",new String[]{""+project});
        while(h.moveToNext()){
            String kind=h.getString(0);
            String label="USER_MESSAGE".equals(kind)?"USER_STATED":("MODEL_OUTPUT".equals(kind)?"MODEL_OUTPUT_NOT_VERIFIED":"ERROR_EVENT");
            recentDialogue.add("- "+h.getString(2)+" ["+label+"] "+shorten(nvl(h.getString(1),""),900));
        }
        h.close();
        Collections.reverse(recentDialogue);
        if(recentDialogue.isEmpty())out.append("- none\n");
        else for(String line:recentDialogue)out.append(line).append("\n");

        List<Candidate> memories=memoryCandidates(q,project);
        out.append("\nRANKED MEMORY:\n");
        for(Candidate c:memories) out.append(format(c)).append("\n");

        List<Candidate> exps=experienceCandidates(q,project);
        out.append("\nRANKED EXPERIENCE (CONDITIONAL):\n");
        for(Candidate c:exps) out.append(format(c)).append("\n");

        appendRelations(out,memories);
        appendConflicts(out,memories,exps);
        out.append("\nMISSING / UNKNOWN:\n");
        if(q.isEmpty()) out.append("- query intent is empty\n");
        if(memories.isEmpty() && exps.isEmpty()) out.append("- no sufficiently relevant memory or experience candidate was found\n");
        out.append("\nCONTEXT RULE: experience is guidance, not a prohibition; NOT_VERIFIED/HYPOTHESIS/UNKNOWN remain qualified.\n");
        return out.toString();
    }

    private List<Candidate> memoryCandidates(String q,long project){
        ArrayList<Candidate> a=new ArrayList<>();
        Cursor c=db.query("SELECT m.logical_id,m.title,m.content,m.claim_status,m.verification_status,m.confidence,m.priority,m.project_id,m.task_id,m.source_id,\n"+
                "CASE WHEN m.project_id=? THEN 1 ELSE 0 END project_match,\n"+
                "CASE WHEN m.memory_status='ACTIVE' THEN 1 ELSE 0 END active\n"+
                "FROM memory_objects m WHERE m.memory_status='ACTIVE' AND m.availability_level!='DELETED' AND (m.project_id=? OR m.project_id IS NULL) ORDER BY m.priority DESC,m.updated_at DESC LIMIT 160",new String[]{""+project});
        while(c.moveToNext()){
            String text=(nvl(c.getString(1),"")+" "+nvl(c.getString(2),"")).toLowerCase(Locale.ROOT);
            double lexical=overlap(q,text);
            double score=.38*lexical+.20*c.getDouble(5)+.12*Math.min(1,c.getInt(6)/10.0)+.18*c.getInt(10)+.12*c.getInt(11);
            if(c.getString(4)!=null && ("NOT_VERIFIED".equals(c.getString(4))||"UNKNOWN".equals(c.getString(4))||"HYPOTHESIS".equals(c.getString(3)))) score*=.75;
            if(score>=.18)a.add(new Candidate(c.getString(0),c.getString(1),c.getString(2),c.getString(3),c.getString(4),c.getDouble(5),score));
        } c.close();
        addFtsCandidates(a,q,project);
        sort(a); return trimUnique(a,14);
    }

    private void addFtsCandidates(List<Candidate> a,String q,long project){
        if(q.isEmpty())return;
        Cursor c=null;
        try{
            String[] terms=q.split("[^\\p{L}\\p{N}]+");
            StringBuilder sql=new StringBuilder("SELECT m.logical_id,m.title,m.content,m.claim_status,m.verification_status,m.confidence,m.project_id FROM memory_search s JOIN memory_objects m ON m.id=s.memory_id WHERE m.memory_status='ACTIVE' AND m.availability_level!='DELETED' AND (m.project_id=? OR m.project_id IS NULL) AND (");
            ArrayList<String> args=new ArrayList<>();
            args.add(""+project);
            int added=0;
            for(String term:terms){
                if(term.length()<2)continue;
                if(added++>0)sql.append(" OR ");
                sql.append("(s.title LIKE ? OR s.content LIKE ?)");
                args.add("%"+term+"%");
                args.add("%"+term+"%");
            }
            if(added==0)return;
            sql.append(") ORDER BY m.priority DESC,m.updated_at DESC LIMIT 40");
            c=db.query(sql.toString(),args.toArray(new String[0]));
            while(c.moveToNext()){
                String text=(nvl(c.getString(1),"")+" "+nvl(c.getString(2),"")).toLowerCase(Locale.ROOT);
                double lexical=overlap(q,text);
                double projectMatch=c.isNull(6)?0.35:1.0;
                double score=.48*lexical+.16*c.getDouble(5)+.12*projectMatch;
                String claim=c.getString(3),verification=c.getString(4);
                if("NOT_VERIFIED".equals(verification)||"UNKNOWN".equals(verification)||"HYPOTHESIS".equals(claim))score*=.75;
                if(score>=.18)a.add(new Candidate(c.getString(0),c.getString(1),c.getString(2),claim,verification,c.getDouble(5),score));
            }
        }catch(Exception ignored){
            // FTS is an optional ranking aid; primary memory selection remains available.
        }finally{
            if(c!=null)c.close();
        }
    }

    private List<Candidate> experienceCandidates(String q,long project){
        ArrayList<Candidate> a=new ArrayList<>();
        Cursor c=db.query("SELECT logical_id,what_happened,what_was_tried,what_worked,what_failed,confidence,experience_type,applicability_json,project_id,task_id FROM experiences WHERE (project_id=? OR project_id IS NULL) AND experience_type IN ('POSITIVE','NEGATIVE') ORDER BY updated_at DESC LIMIT 120",new String[]{""+project});
        while(c.moveToNext()){
            String blob=nvl(c.getString(1),"")+" "+nvl(c.getString(2),"")+" "+nvl(c.getString(3),"")+" "+nvl(c.getString(4),"");
            double lexical=overlap(q,blob.toLowerCase(Locale.ROOT));
            double applicability=applicabilityScore(q,c.getString(7));
            double projectMatch=c.getLong(8)==project?1:.35;
            double confidence=c.getDouble(5);
            double typeBoost="POSITIVE".equals(c.getString(6))||"NEGATIVE".equals(c.getString(6))?.08:0;
            double score=.30*lexical+.32*applicability+.18*confidence+.12*projectMatch+typeBoost;
            if(score>=.15)a.add(new Candidate(c.getString(0),c.getString(6),blob,c.getString(6),"EXPERIENCE",confidence,score));
        } c.close();
        sort(a); return trimUnique(a,8);
    }

    private double applicabilityScore(String q,String json){
        if(json==null||json.trim().isEmpty())return .25;
        try{
            JSONObject o=new JSONObject(json);
            double s=.0; int hits=0;
            for(String k:new String[]{"project","domain","task_type","technology","operation","conditions","include","keywords"}){
                String v=o.optString(k,"").toLowerCase(Locale.ROOT);
                if(!v.isEmpty()){hits++; if(overlap(q,v)>0)s+=1;}
            }
            String[] ex=o.optString("exclude","").toLowerCase(Locale.ROOT).split("[,;\\s]+");
            for(String x:ex)if(!x.isEmpty()&&q.contains(x))return .0;
            return hits==0?.25:Math.min(1,s/hits);
        }catch(Exception e){return .2;}
    }

    private void appendRelations(StringBuilder out,List<Candidate> ms){
        if(ms.isEmpty())return;
        out.append("\nDIRECT RELATIONS:\n");
        HashSet<String> ids=new HashSet<>();for(Candidate c:ms)ids.add(c.id);
        int n=0;
        Cursor r=db.query("SELECT a.logical_id,ra.logical_id,ra.relation_type,ra.weight,ra.confidence,b.logical_id FROM relations ra JOIN memory_objects a ON a.id=ra.from_object_id JOIN memory_objects b ON b.id=ra.to_object_id WHERE a.logical_id IN ("+placeholders(ids.size())+") ORDER BY ra.weight DESC LIMIT 16",ids.toArray(new String[0]));
        while(r.moveToNext()){out.append("- ").append(r.getString(0)).append(" --").append(r.getString(2)).append("--> ").append(r.getString(5)).append(" [weight=").append(r.getDouble(3)).append(",conf=").append(r.getDouble(4)).append("]\n");n++;}r.close();
        if(n==0)out.append("- none\n");
    }

    private void appendConflicts(StringBuilder out,List<Candidate> ms,List<Candidate> es){
        out.append("\nCONFLICT / CAUTION:\n");
        int n=0;
        for(Candidate a:ms)for(Candidate b:ms)if(a!=b && a.title!=null && b.title!=null && overlap(norm(a.title),norm(b.title))>.55 && !nvl(a.content,"").equals(nvl(b.content,""))){
            out.append("- competing memory candidates: ").append(a.id).append(" vs ").append(b.id).append("; do not silently merge.\n");n++; if(n>=5)break;
        }
        if(n==0)out.append("- no direct contradiction detected by lightweight check\n");
    }

    private String format(Candidate c){return "- "+c.id+" score="+round(c.score)+" conf="+round(c.confidence)+" ["+nvl(c.status,"")+"/"+nvl(c.verification,"")+"] "+nvl(c.title,"")+": "+shorten(nvl(c.content,""),1400);}

    private static void sort(List<Candidate>a){Collections.sort(a,(x,y)->Double.compare(y.score,x.score));}
    private static List<Candidate> trimUnique(List<Candidate>a,int n){LinkedHashMap<String,Candidate>m=new LinkedHashMap<>();for(Candidate c:a){if(!m.containsKey(c.id)||m.get(c.id).score<c.score)m.put(c.id,c);if(m.size()>=n)break;}return new ArrayList<>(m.values());}
    private static double overlap(String q,String text){if(q.isEmpty()||text.isEmpty())return 0;Set<String>a=tokens(q),b=tokens(text);int hit=0;for(String x:a)if(b.contains(x))hit++;return a.isEmpty()?0:(double)hit/a.size();}
    private static Set<String> tokens(String s){HashSet<String>r=new HashSet<>();for(String x:s.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+")){if(x.length()>=3)r.add(x);}return r;}
    private static String fts(String q){return q.replaceAll("[^\\p{L}\\p{N} ]"," ").trim().replaceAll("\\s+"," OR ");}
    private static String placeholders(int n){StringBuilder b=new StringBuilder();for(int i=0;i<n;i++){if(i>0)b.append(',');b.append('?');}return b.toString();}
    private static String nvl(String s,String d){return s==null?d:s;}
    private static String shorten(String s,int n){return s.length()<=n?s:s.substring(0,n)+"…";}
    private static String round(double x){return String.format(Locale.US,"%.3f",x);}
    private static String norm(String s){return s==null?"":s.toLowerCase(Locale.ROOT).trim();}
}