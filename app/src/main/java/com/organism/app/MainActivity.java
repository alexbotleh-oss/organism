package com.organism.app;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.Color;
import android.net.Uri;
import android.util.Base64;
import android.view.*;
import android.widget.*;
import android.database.sqlite.*;
import android.database.Cursor;
import java.io.*;
import java.math.BigInteger;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.util.*;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.json.*;

public class MainActivity extends Activity {
    static final String AUTH="https://auth.openai.com/api/accounts/authorize";
    static final String TOKEN="https://auth.openai.com/api/accounts/oauth/token";
    static final String JWKS="https://auth.openai.com/.well-known/jwks.json";
    static final String API="https://api.openai.com/v1";
    static final String RESOURCE=API;
    static final String HOST_KEY="host_id";
    static final String CLIENT_KEY="client_id";
    static final String EMAIL_KEY="email";
    static final int PORT=1455;

    Db db; LinearLayout root, content; TextView status, chat; EditText input;
    final Handler main=new Handler(Looper.getMainLooper());
    volatile boolean busy=false; ServerSocket callbackSocket; String pendingState,pendingNonce,pendingVerifier,pendingRedirect;
    String accessToken="",refreshToken="",idToken=""; long expiresAt=0;
    String model="";

    @Override public void onCreate(Bundle b){
        super.onCreate(b); db=new Db(this); loadCreds(); build(); showHome();
    }

    void build(){
        root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(Color.rgb(245,247,251));
        LinearLayout bar=new LinearLayout(this); bar.setPadding(28,28,28,18); bar.setGravity(Gravity.CENTER_VERTICAL);
        TextView title=t("ОРГАНИЗМ",24,Color.rgb(16,24,39)); bar.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        status=t("ChatGPT: не подключён",13,Color.DKGRAY); bar.addView(status);
        root.addView(bar);
        content=new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL); content.setPadding(22,8,22,90);
        ScrollView sv=new ScrollView(this); sv.addView(content); root.addView(sv,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout nav=new LinearLayout(this); nav.setPadding(8,8,8,8); nav.setBackgroundColor(Color.WHITE);
        String[] ns={"Главная","Чат","Знания","Настройки"};
        for(String n:ns){ Button x=new Button(this); x.setText(n); nav.addView(x,new LinearLayout.LayoutParams(0,60,1)); if(n.equals("Главная"))x.setOnClickListener(v->showHome()); if(n.equals("Чат"))x.setOnClickListener(v->showChat()); if(n.equals("Знания"))x.setOnClickListener(v->showKnowledge()); if(n.equals("Настройки"))x.setOnClickListener(v->showSettings()); }
        root.addView(nav); setContentView(root); updateStatus();
    }
    TextView t(String s,int size,int color){ TextView v=new TextView(this); v.setText(s); v.setTextSize(size); v.setTextColor(color); v.setPadding(4,4,4,4); return v; }
    Button btn(String s){ Button b=new Button(this); b.setText(s); return b; }
    void clear(){content.removeAllViews();}
    void card(String text){ TextView v=t(text,16,Color.rgb(35,43,56)); v.setBackgroundColor(Color.WHITE); v.setPadding(22,22,22,22); LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,0,0,16);content.addView(v,p); }
    void showHome(){clear(); card("Цикл Организма\n\nЯ → Организм → GPT → Организм → Я\n\nВопрос пользователя проходит через локальную память и контекст Организма. Ответ GPT возвращается обратно и записывается как новый объект диалога и кандидат опыта."); Button c=btn(hasCreds()?"ChatGPT подключён":"Continue with ChatGPT"); c.setOnClickListener(v->hasCreds()?showChat():signIn()); content.addView(c); Button ch=btn("Открыть чат");ch.setOnClickListener(v->showChat());content.addView(ch);}
    void showSettings(){clear(); card("Подключение ChatGPT\n\nOAuth Sign in with ChatGPT. API key не используется."); Button b=btn(hasCreds()?"Переподключить ChatGPT":"Continue with ChatGPT");b.setOnClickListener(v->signIn());content.addView(b); Button out=btn("Выйти из ChatGPT");out.setOnClickListener(v->{clearCreds();updateStatus();showSettings();});content.addView(out); card("Память: "+db.count("messages")+" сообщений, "+db.count("memories")+" объектов памяти, "+db.count("experiences")+" кандидатов опыта.");}
    void showKnowledge(){clear(); card("База знаний\n\nСообщения: "+db.count("messages")+"\nПамять: "+db.count("memories")+"\nОпыт: "+db.count("experiences")); TextView list=t(db.recentExperiences(),15,Color.DKGRAY);content.addView(list);}
    void showChat(){clear(); chat=t(db.chatText(),15,Color.rgb(30,36,48)); chat.setBackgroundColor(Color.WHITE); content.addView(chat,new LinearLayout.LayoutParams(-1,0,1)); input=new EditText(this);input.setHint("Напишите запрос…");input.setMinLines(2);content.addView(input); Button send=btn("Отправить через ORGANISM → GPT");send.setOnClickListener(v->send());content.addView(send); Button ctx=btn("Показать контекст");ctx.setOnClickListener(v->{String q=input.getText().toString();new AlertDialog.Builder(this).setTitle("Context Engine").setMessage(db.context(q)).setPositiveButton("Закрыть",null).show();});content.addView(ctx);}
    void updateStatus(){status.setText(hasCreds()?"ChatGPT: подключён":"ChatGPT: не подключён");}
    boolean hasCreds(){return !accessToken.isEmpty()||!refreshToken.isEmpty();}

    void signIn(){
        if(busy)return; busy=true; new Thread(()->{
            try{
                int port=PORT; pendingRedirect="http://127.0.0.1:"+port+"/auth/callback";
                callbackSocket=new ServerSocket(port,1,InetAddress.getByName("127.0.0.1"));
                pendingState=random(32); pendingNonce=random(32); pendingVerifier=random(48);
                String challenge=b64(MessageDigest.getInstance("SHA-256").digest(pendingVerifier.getBytes(StandardCharsets.UTF_8)));
                String client=getPrefs().getString(CLIENT_KEY,"dynamic_agent_client");
                String host=getPrefs().getString(HOST_KEY,"");
                if(host.isEmpty()){host="urn:uuid:"+UUID.randomUUID();getPrefs().edit().putString(HOST_KEY,host).apply();}
                Uri.Builder u=Uri.parse(AUTH).buildUpon();
                u.appendQueryParameter("client_id",client).appendQueryParameter("redirect_uri",pendingRedirect)
                 .appendQueryParameter("response_type","code").appendQueryParameter("scope","openid profile email offline_access resource.invoke chatgpt.tokens.use.direct")
                 .appendQueryParameter("resource",RESOURCE).appendQueryParameter("state",pendingState).appendQueryParameter("nonce",pendingNonce)
                 .appendQueryParameter("code_challenge_method","S256").appendQueryParameter("code_challenge",challenge);
                if(client.equals("dynamic_agent_client")){u.appendQueryParameter("agent_name_hint","ОРГАНИЗМ");u.appendQueryParameter("ext_agent_host_id",host);}
                else {String oldId=getPrefs().getString("id_token_hint","");String em=getPrefs().getString(EMAIL_KEY,"");if(!oldId.isEmpty())u.appendQueryParameter("id_token_hint",oldId);if(!em.isEmpty())u.appendQueryParameter("login_hint",em);}
                Intent i=new Intent(Intent.ACTION_VIEW,Uri.parse(u.toString()));startActivity(i);
                Socket s=callbackSocket.accept(); BufferedReader r=new BufferedReader(new InputStreamReader(s.getInputStream(),StandardCharsets.UTF_8));String line=r.readLine();String path=line.split(" ")[1];
                String body="<html><body><h3>ОРГАНИЗМ</h3>Можно вернуться в приложение.</body></html>";OutputStream os=s.getOutputStream();os.write(("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: "+body.getBytes(StandardCharsets.UTF_8).length+"\r\nConnection: close\r\n\r\n"+body).getBytes(StandardCharsets.UTF_8));os.flush();s.close();callbackSocket.close();
                Uri cb=Uri.parse("http://127.0.0.1"+path);String state=cb.getQueryParameter("state");String err=cb.getQueryParameter("error");String code=cb.getQueryParameter("code");String issued=cb.getQueryParameter("client_id");
                if(!pendingState.equals(state))throw new Exception("OAuth state не совпал");
                if(err!=null)throw new Exception("Авторизация отменена: "+err);
                if(code==null)throw new Exception("OAuth code отсутствует");
                if(client.equals("dynamic_agent_client")){if(issued==null||issued.isEmpty())throw new Exception("OpenAI не вернул client_id");client=issued;getPrefs().edit().putString(CLIENT_KEY,client).apply();}
                JSONObject tok=postForm(TOKEN,new String[][]{{"grant_type","authorization_code"},{"client_id",client},{"code",code},{"code_verifier",pendingVerifier},{"redirect_uri",pendingRedirect},{"resource",RESOURCE}});
                String id=tok.optString("id_token",""); if(id.isEmpty())throw new Exception("ID token отсутствует");
                verifyIdToken(id,client,pendingNonce);
                String scopes=tok.optString("scope","");if(!scopes.contains("chatgpt.tokens.use.direct"))throw new Exception("Разрешение ChatGPT plan usage не выдано");
                accessToken=tok.getString("access_token");refreshToken=tok.optString("refresh_token","");expiresAt=System.currentTimeMillis()/1000+tok.optLong("expires_in",3600);idToken=id;
                JSONObject payload=jwtPart(id,1);getPrefs().edit().putString("id_token_hint",id).putString(EMAIL_KEY,payload.optString("email","")).apply();saveCreds();
                main.post(()->{busy=false;updateStatus();toast("ChatGPT подключён. Можно тестировать текущий чат.");showChat();});
            }catch(Exception e){busy=false;main.post(()->toast("Не удалось подключить ChatGPT: "+e.getMessage()));}
        }).start();
    }

    void send(){
        if(busy||input==null)return;String q=input.getText().toString().trim();if(q.isEmpty())return;if(!hasCreds()){toast("Сначала подключите ChatGPT");return;}
        busy=true;input.setText("");db.addMessage("user",q);chat.setText(db.chatText()+"\n\nОрганизм → GPT: …");new Thread(()->{
            try{refreshIfNeeded();String context=db.context(q);String answer=infer(q,context);db.addMessage("assistant",answer);db.addMemory(q,answer);db.addExperience(q,answer);main.post(()->{busy=false;chat.setText(db.chatText());updateStatus();});}
            catch(Exception e){db.addMessage("system","Ошибка: "+e.getMessage());main.post(()->{busy=false;chat.setText(db.chatText());toast("Ошибка: "+e.getMessage());});}
        }).start();
    }

    String infer(String q,String context)throws Exception{
        if(model.isEmpty())model=chooseModel();String inputText="Ты работаешь через ORGANISM. Используй контекст и опыт только применимо и условно. Не выдавай кандидатов опыта за подтверждённые факты.\n\nКОНТЕКСТ ORGANISM:\n"+context+"\n\nТЕКУЩИЙ ЗАПРОС:\n"+q;
        JSONObject body=new JSONObject();body.put("model",model);body.put("input",inputText);body.put("store",false);body.put("stream",true);
        HttpURLConnection c=(HttpURLConnection)new URL(API+"/responses").openConnection();c.setRequestMethod("POST");c.setDoOutput(true);c.setRequestProperty("Authorization","Bearer "+accessToken);c.setRequestProperty("Content-Type","application/json");c.getOutputStream().write(body.toString().getBytes(StandardCharsets.UTF_8));
        int code=c.getResponseCode();InputStream in=code>=400?c.getErrorStream():c.getInputStream();BufferedReader r=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8));String line;StringBuilder out=new StringBuilder();boolean completed=false;
        while((line=r.readLine())!=null){if(!line.startsWith("data:"))continue;String d=line.substring(5).trim();if(d.equals("[DONE]"))break;try{JSONObject e=new JSONObject(d);String type=e.optString("type");if(type.equals("response.output_text.delta"))out.append(e.optString("delta"));else if(type.equals("response.completed"))completed=true;else if(type.equals("response.failed"))throw new Exception(e.optJSONObject("response")!=null?e.optJSONObject("response").optString("error","response.failed"):"response.failed");}catch(JSONException ignore){}}
        if(code>=400)throw new Exception(readAll(in));if(!completed&&out.length()==0)throw new Exception("Поток ответа завершился без текста");return out.toString().trim();
    }
    String chooseModel()throws Exception{
        HttpURLConnection c=(HttpURLConnection)new URL(API+"/models").openConnection();c.setRequestProperty("Authorization","Bearer "+accessToken);if(c.getResponseCode()>=400)throw new Exception("Не удалось получить список моделей: "+c.getResponseCode());JSONObject j=new JSONObject(readAll(c.getInputStream()));JSONArray a=j.optJSONArray("data");if(a!=null)for(int i=0;i<a.length();i++){JSONObject m=a.getJSONObject(i);if(m.optBoolean("visibility",false))return m.optString("id");}if(a!=null&&a.length()>0)return a.getJSONObject(0).optString("id");throw new Exception("Список моделей пуст");}

    void refreshIfNeeded()throws Exception{
        if(accessToken.isEmpty()||System.currentTimeMillis()/1000+90<expiresAt)return;if(refreshToken.isEmpty())throw new Exception("Нужна повторная авторизация");
        String client=getPrefs().getString(CLIENT_KEY,"");JSONObject t=postForm(TOKEN,new String[][]{{"grant_type","refresh_token"},{"client_id",client},{"refresh_token",refreshToken},{"resource",RESOURCE}});
        accessToken=t.getString("access_token");refreshToken=t.optString("refresh_token",refreshToken);expiresAt=System.currentTimeMillis()/1000+t.optLong("expires_in",3600);saveCreds();
    }

    void verifyIdToken(String jwt,String client,String nonce)throws Exception{
        JSONObject h=jwtPart(jwt,0),p=jwtPart(jwt,1);if(!"RS256".equals(h.optString("alg")))throw new Exception("Неподдерживаемая подпись");if(!"https://auth.openai.com".equals(p.optString("iss")))throw new Exception("Неверный issuer");if(p.optLong("exp",0)<System.currentTimeMillis()/1000)throw new Exception("ID token истёк");String aud=p.optString("aud");boolean audOk=client.equals(aud);JSONArray aa=p.optJSONArray("aud");if(aa!=null)for(int i=0;i<aa.length();i++)if(client.equals(aa.optString(i)))audOk=true;if(!audOk)throw new Exception("Неверная audience");if(!nonce.equals(p.optString("nonce")))throw new Exception("Неверный nonce");
        JSONObject jwks=new JSONObject(readUrl(JWKS));JSONArray keys=jwks.getJSONArray("keys");String kid=h.optString("kid");for(int i=0;i<keys.length();i++){JSONObject k=keys.getJSONObject(i);if(kid.equals(k.optString("kid"))){BigInteger n=new BigInteger(1,Base64.decode(k.getString("n"),Base64.URL_SAFE|Base64.NO_WRAP|Base64.NO_PADDING));BigInteger e=new BigInteger(1,Base64.decode(k.getString("e"),Base64.URL_SAFE|Base64.NO_WRAP|Base64.NO_PADDING));PublicKey pk=KeyFactory.getInstance("RSA").generatePublic(new RSAPublicKeySpec(n,e));String[] parts=jwt.split("\\.");Signature s=Signature.getInstance("SHA256withRSA");s.initVerify(pk);s.update((parts[0]+"."+parts[1]).getBytes(StandardCharsets.UTF_8));if(!s.verify(Base64.decode(parts[2],Base64.URL_SAFE|Base64.NO_WRAP|Base64.NO_PADDING)))throw new Exception("Неверная подпись ID token");return;}}throw new Exception("Ключ подписи не найден");
    }
    JSONObject postForm(String url,String[][] fields)throws Exception{HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();c.setRequestMethod("POST");c.setDoOutput(true);c.setRequestProperty("Content-Type","application/x-www-form-urlencoded");StringBuilder b=new StringBuilder();for(String[] f:fields){if(b.length()>0)b.append('&');b.append(URLEncoder.encode(f[0],"UTF-8")).append('=').append(URLEncoder.encode(f[1],"UTF-8"));}c.getOutputStream().write(b.toString().getBytes(StandardCharsets.UTF_8));int sc=c.getResponseCode();String txt=readAll(sc>=400?c.getErrorStream():c.getInputStream());if(sc>=400)throw new Exception("OAuth HTTP "+sc+": "+txt);return new JSONObject(txt);}
    String readUrl(String u)throws Exception{HttpURLConnection c=(HttpURLConnection)new URL(u).openConnection();c.setConnectTimeout(15000);c.setReadTimeout(15000);return readAll(c.getInputStream());}
    String readAll(InputStream in)throws Exception{if(in==null)return "";BufferedReader r=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8));StringBuilder b=new StringBuilder();String s;while((s=r.readLine())!=null)b.append(s).append('\n');return b.toString();}
    JSONObject jwtPart(String jwt,int part)throws Exception{return new JSONObject(new String(Base64.decode(jwt.split("\\.")[part],Base64.URL_SAFE|Base64.NO_WRAP|Base64.NO_PADDING),StandardCharsets.UTF_8));}
    String random(int n){byte[] b=new byte[n];new SecureRandom().nextBytes(b);return b64(b);}
    String b64(byte[] b){return Base64.encodeToString(b,Base64.URL_SAFE|Base64.NO_WRAP|Base64.NO_PADDING);}
    SharedPreferences getPrefs(){return getSharedPreferences("organism",MODE_PRIVATE);}
    void saveCreds(){getPrefs().edit().putString("access",accessToken).putString("refresh",refreshToken).putLong("expires",expiresAt).apply();}
    void loadCreds(){accessToken=getPrefs().getString("access","");refreshToken=getPrefs().getString("refresh","");expiresAt=getPrefs().getLong("expires",0);idToken=getPrefs().getString("id_token_hint","");}
    void clearCreds(){accessToken="";refreshToken="";idToken="";expiresAt=0;getPrefs().edit().remove("access").remove("refresh").remove("expires").remove("id_token_hint").remove(EMAIL_KEY).remove(CLIENT_KEY).apply();}
    void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}

    class Db extends SQLiteOpenHelper{
        Db(Context c){super(c,"organism.db",null,1);}
        public void onCreate(SQLiteDatabase d){d.execSQL("CREATE TABLE messages(id INTEGER PRIMARY KEY AUTOINCREMENT, role TEXT, text TEXT, at INTEGER)");d.execSQL("CREATE TABLE memories(id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT, content TEXT, at INTEGER)");d.execSQL("CREATE TABLE experiences(id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT, happened TEXT, tried TEXT, worked TEXT, confidence REAL, at INTEGER)");}
        public void onUpgrade(SQLiteDatabase d,int o,int n){}
        void addMessage(String role,String text){ContentValues v=new ContentValues();v.put("role",role);v.put("text",text);v.put("at",System.currentTimeMillis());getWritableDatabase().insert("messages",null,v);}
        void addMemory(String q,String a){ContentValues v=new ContentValues();v.put("title","Диалог: "+q.substring(0,Math.min(80,q.length())));v.put("content","Вопрос:\n"+q+"\n\nОтвет GPT:\n"+a);v.put("at",System.currentTimeMillis());getWritableDatabase().insert("memories",null,v);}
        void addExperience(String q,String a){ContentValues v=new ContentValues();v.put("title","Кандидат опыта: "+q.substring(0,Math.min(80,q.length())));v.put("happened","В текущем разговоре GPT ответил через ORGANISM.");v.put("tried",q);v.put("worked",a.substring(0,Math.min(1200,a.length())));v.put("confidence",0.5);v.put("at",System.currentTimeMillis());getWritableDatabase().insert("experiences",null,v);}
        int count(String t){Cursor c=getReadableDatabase().rawQuery("SELECT COUNT(*) FROM "+t,null);c.moveToFirst();int n=c.getInt(0);c.close();return n;}
        String chatText(){StringBuilder b=new StringBuilder();Cursor c=getReadableDatabase().rawQuery("SELECT role,text FROM messages ORDER BY id ASC LIMIT 80",null);while(c.moveToNext())b.append(c.getString(0).equals("user")?"\nВы: ":"\nGPT: ").append(c.getString(1)).append("\n");c.close();return b.length()==0?"Начните разговор.":b.toString();}
        String context(String q){StringBuilder b=new StringBuilder();b.append("Проект: ORGANISM\n");b.append("Последние сообщения:\n");Cursor c=getReadableDatabase().rawQuery("SELECT role,text FROM messages ORDER BY id DESC LIMIT 8",null);while(c.moveToNext())b.append(c.getString(0)).append(": ").append(c.getString(1)).append("\n");c.close();b.append("\nПамять:\n");Cursor m=getReadableDatabase().rawQuery("SELECT content FROM memories ORDER BY id DESC LIMIT 6",null);while(m.moveToNext())b.append(m.getString(0)).append("\n---\n");m.close();b.append("\nОпыт (кандидат, confidence 0.5):\n");Cursor e=getReadableDatabase().rawQuery("SELECT happened,tried,worked FROM experiences ORDER BY id DESC LIMIT 5",null);while(e.moveToNext())b.append(e.getString(0)).append(" | ").append(e.getString(1)).append(" | ").append(e.getString(2)).append("\n");e.close();return b.toString();}
        String recentExperiences(){StringBuilder b=new StringBuilder();Cursor c=getReadableDatabase().rawQuery("SELECT title,happened,confidence FROM experiences ORDER BY id DESC LIMIT 30",null);while(c.moveToNext())b.append("\n• ").append(c.getString(0)).append("\n").append(c.getString(1)).append("\nconfidence: ").append(c.getDouble(2)).append("\n");c.close();return b.length()==0?"Пока нет кандидатов опыта.":b.toString();}
    }
}
