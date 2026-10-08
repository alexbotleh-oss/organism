package com.organism.app;

import org.json.*;
import java.util.*;

/** Experience lifecycle: candidate -> conditional applicability -> reinforcement/refutation. */
public final class ExperienceEngine {
    private final Db db;
    public ExperienceEngine(Db db){this.db=db;}

    public long recordInteraction(String query,String answer,long projectId){
        JSONObject app=new JSONObject();
        try{
            app.put("project","ORGANISM");
            app.put("task_type",classify(query));
            app.put("keywords",query==null?"":query);
            app.put("conditions","candidate_from_current_interaction");
            app.put("exclude","not_verified");
        }catch(Exception ignored){}
        return db.experience(
            "Запрос обработан через ORGANISM",
            query,
            answer,
            "",
            projectId,
            .50,
            "NEUTRAL",
            app.toString()
        );
    }

    public void reinforce(long experienceId,boolean confirmed,String reason){
        db.reinforceExperience(experienceId,confirmed,reason);
    }

    private String classify(String q){
        String s=q==null?"":q.toLowerCase(Locale.ROOT);
        if(s.contains("исправ")||s.contains("ошиб")||s.contains("слом"))return "debugging";
        if(s.contains("сделай")||s.contains("разработ")||s.contains("добав"))return "implementation";
        if(s.contains("проверь")||s.contains("сравн")||s.contains("оцени"))return "verification";
        if(s.contains("как")||s.contains("почему"))return "analysis";
        return "general";
    }
}