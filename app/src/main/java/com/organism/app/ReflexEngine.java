package com.organism.app;

import java.util.*;

public final class ReflexEngine {
    public static final class Result { public final boolean passed; public final String message; public final boolean block; Result(boolean p,String m,boolean b){passed=p;message=m;block=b;} }
    public List<Result> check(Map<String,Object> context,Map<String,Object> action){
        ArrayList<Result> out=new ArrayList<>();
        out.add(noUnknownAsFact(action));
        out.add(criticalUncertainty(context,action));
        out.add(protectMemory(action));
        out.add(noUnauthorizedDeletion(context,action));
        return out;
    }
    private Result noUnknownAsFact(Map<String,Object>a){String s=String.valueOf(a.get("claim_status"));if(("UNKNOWN".equals(s)||"MISSING_DATA".equals(s)||"HYPOTHESIS".equals(s))&&!Boolean.TRUE.equals(a.get("qualified")) )return new Result(false,"Неизвестное нельзя выдавать за факт.",true);return new Result(true,"OK",false);}
    private Result criticalUncertainty(Map<String,Object>c,Map<String,Object>a){Object v=c.get("confidence");double x=v instanceof Number?((Number)v).doubleValue():1;return x<.3&&!Boolean.TRUE.equals(a.get("clarification"))?new Result(false,"Критическая неопределённость: требуется уточнение.",true):new Result(true,"OK",false);}
    private Result protectMemory(Map<String,Object>a){return "DELETE".equals(a.get("action_type"))&&Boolean.TRUE.equals(a.get("protected"))?new Result(false,"Защищённая память не может быть удалена.",true):new Result(true,"OK",false);}
    private Result noUnauthorizedDeletion(Map<String,Object>c,Map<String,Object>a){return "DELETE".equals(a.get("action_type"))&&!Boolean.TRUE.equals(c.get("user_confirmed"))?new Result(false,"Удаление требует подтверждения пользователя.",true):new Result(true,"OK",false);}
}
