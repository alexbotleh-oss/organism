package com.organism.app;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.util.Base64;
import android.util.Log;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.math.BigInteger;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.RSAPublicKeySpec;
import java.util.*;
import java.util.zip.*;
import javax.crypto.*;
import org.json.*;
import androidx.core.content.FileProvider;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

public class MainActivity extends Activity {
    static final String AUTH="https://auth.openai.com/api/accounts/authorize";
    static final String TOKEN="https://auth.openai.com/api/accounts/oauth/token";
    static final String JWKS="https://auth.openai.com/.well-known/jwks.json";
    static final String API="https://api.openai.com/v1";
    static final String RESOURCE=API;
    static final int CALLBACK_PORT_HINT=1455;

    Db db; ImportPipeline importer; ReflexEngine reflex=new ReflexEngine(); ContextEngine contextEngine; ExperienceEngine experienceEngine; long activeSessionId=0;
    LinearLayout root,content; ScrollView contentScroll; TextView status,screenTitle,chatView; EditText chatInput; Spinner modelSpinner; ScrollView chatScroll; HorizontalScrollView navScroll;
    Handler main=new Handler(Looper.getMainLooper()); boolean busy=false; String screen="home";
    ServerSocket callbackSocket; String pendingState,pendingNonce,pendingVerifier,pendingRedirect;
    String accessToken="",refreshToken="",idToken="",model=""; long expiresAt=0;
    ArrayList<String> modelSlugs=new ArrayList<>(),modelNames=new ArrayList<>();

    @Override public void onCreate(Bundle b){super.onCreate(b);WindowCompat.setDecorFitsSystemWindows(getWindow(),false);getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);try{db=new Db(this);activeSessionId=ensureActiveSession();importer=new ImportPipeline(this,db);contextEngine=new ContextEngine(db);experienceEngine=new ExperienceEngine(db);loadCreds();buildShell();showHome();}catch(Throwable t){showStartupError(t);}}
    void showStartupError(Throwable t){Log.e("ORGANISM","Startup failure",t);TextView v=new TextView(this);v.setText("ОРГАНИЗМ не смог запуститься.\n\nОшибка: "+t.getClass().getName()+"\n"+String.valueOf(t.getMessage())+"\n\nЗакройте приложение и сообщите этот текст разработчику.");v.setTextSize(16);v.setTextColor(Color.rgb(30,36,48));v.setPadding(32,48,32,48);v.setTextIsSelectable(true);ViewCompat.setOnApplyWindowInsetsListener(v,(view,insets)->{androidx.core.graphics.Insets bars=insets.getInsets(WindowInsetsCompat.Type.systemBars());view.setPadding(32,bars.top+32,32,bars.bottom+32);return insets;});setContentView(v);ViewCompat.requestApplyInsets(v);}
    int dp(float value){return (int)(value*getResources().getDisplayMetrics().density+0.5f);}
    GradientDrawable rounded(int color,float radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    TextView tv(String s,int z,int c){TextView v=new TextView(this);v.setText(s);v.setTextSize(z);v.setTextColor(c);v.setPadding(dp(10),dp(8),dp(10),dp(8));return v;}
    Button bt(String s){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextColor(Color.rgb(31,61,112));b.setTextSize(14);b.setPadding(dp(12),dp(8),dp(12),dp(8));b.setMinHeight(dp(46));b.setBackground(rounded(Color.rgb(231,238,251),15));if(android.os.Build.VERSION.SDK_INT>=21)b.setStateListAnimator(null);return b;}
    void buildShell(){
        root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(Color.rgb(242,245,250));
        ViewCompat.setOnApplyWindowInsetsListener(root,(v,insets)->{androidx.core.graphics.Insets bars=insets.getInsets(WindowInsetsCompat.Type.systemBars());androidx.core.graphics.Insets ime=insets.getInsets(WindowInsetsCompat.Type.ime());boolean keyboard=insets.isVisible(WindowInsetsCompat.Type.ime());v.setPadding(0,bars.top,0,keyboard?ime.bottom:bars.bottom);if(navScroll!=null)navScroll.setVisibility(keyboard?View.GONE:View.VISIBLE);return insets;});
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);top.setPadding(dp(16),dp(10),dp(12),dp(8));top.setBackgroundColor(Color.rgb(255,255,255));
        LinearLayout brand=new LinearLayout(this);brand.setOrientation(LinearLayout.VERTICAL);brand.setGravity(Gravity.CENTER_VERTICAL);
        screenTitle=tv("ОРГАНИЗМ",21,Color.rgb(16,24,39));screenTitle.setTypeface(null,android.graphics.Typeface.BOLD);screenTitle.setPadding(0,0,0,0);
        TextView subtitle=tv("ПАМЯТЬ · ОПЫТ · НЕПРЕРЫВНОСТЬ",9,Color.rgb(91,108,133));subtitle.setPadding(0,dp(2),0,0);
        brand.addView(screenTitle);brand.addView(subtitle);top.addView(brand,new LinearLayout.LayoutParams(0,-2,1));
        status=tv(hasCreds()?"● ChatGPT подключён":"● локальный режим",11,hasCreds()?Color.rgb(25,130,90):Color.rgb(91,108,133));status.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(status);
        Button quick=bt("•••");quick.setContentDescription("Дополнительные разделы");quick.setOnClickListener(v->showQuickMenu());top.addView(quick,new LinearLayout.LayoutParams(dp(44),dp(44)));
        root.addView(top);
        View separator=new View(this);separator.setBackgroundColor(Color.rgb(225,231,240));root.addView(separator,new LinearLayout.LayoutParams(-1,dp(1)));
        content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);content.setPadding(dp(16),dp(12),dp(16),dp(20));
        contentScroll=new ScrollView(this);contentScroll.setFillViewport(true);contentScroll.setClipToPadding(false);contentScroll.addView(content);root.addView(contentScroll,new LinearLayout.LayoutParams(-1,0,1));
        navScroll=new HorizontalScrollView(this);navScroll.setHorizontalScrollBarEnabled(false);navScroll.setBackgroundColor(Color.WHITE);
        LinearLayout nav=new LinearLayout(this);nav.setGravity(Gravity.CENTER_VERTICAL);nav.setPadding(dp(5),dp(5),dp(5),dp(5));
        String[][] items={{"⌂  Главная","home"},{"◉  Чат","chat"},{"✳  Память","memory"},{"⇧  Импорт","import"},{"•••  Ещё","more"}};
        for(String[] it:items){Button b=bt(it[0]);b.setTextSize(11);b.setMinHeight(dp(48));b.setPadding(dp(2),dp(4),dp(2),dp(4));b.setBackground(rounded(Color.rgb(255,255,255),12));b.setTextColor(Color.rgb(48,65,91));b.setOnClickListener(v->navigate(it[1]));nav.addView(b,new LinearLayout.LayoutParams(0,dp(50),1));}
        navScroll.addView(nav,new HorizontalScrollView.LayoutParams(-1,-2));root.addView(navScroll);setContentView(root);
    }
    void navigate(String s){if("home".equals(s))showHome();else if("chat".equals(s))showChat();else if("memory".equals(s))showMemory();else if("import".equals(s))showImport();else if("database".equals(s))showDatabase();else if("more".equals(s))showQuickMenu();else showSettings();}
    void clear(String title){if("chat".equals(screen))deactivateChatLayout();screen=title;content.removeAllViews();screenTitle.setText(title);status.setText(hasCreds()?"ChatGPT: подключён":"ChatGPT: не подключён");}
    void card(String title,String body){LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(16),dp(15),dp(16),dp(15));box.setBackground(rounded(Color.WHITE,20));box.setElevation(dp(1));TextView h=tv(title,17,Color.rgb(20,32,52));h.setTypeface(null,android.graphics.Typeface.BOLD);h.setPadding(0,0,0,dp(5));box.addView(h);TextView t=tv(body,14,Color.rgb(69,81,101));t.setLineSpacing(dp(3),1.0f);t.setPadding(0,0,0,0);box.addView(t);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,0,0,dp(14));content.addView(box,p);}
    void activateChatLayout(){if(content.getParent()==contentScroll){contentScroll.removeView(content);root.removeView(contentScroll);root.addView(content,2,new LinearLayout.LayoutParams(-1,0,1));}content.setPadding(dp(12),dp(6),dp(12),dp(8));}
    void deactivateChatLayout(){if(content.getParent()==root){root.removeView(content);content.setPadding(dp(16),dp(12),dp(16),dp(20));contentScroll.addView(content);root.addView(contentScroll,2,new LinearLayout.LayoutParams(-1,0,1));}}
    void showHome(){clear("Главная");card("Цикл Организма","Я → ORGANISM → GPT → ORGANISM → Я\n\nОрганизм хранит RAW, источники, события, память, связи, опыт и состояние отдельно от модели. Перед каждым запросом Context Engine собирает релевантный контекст.");card("Состояние","Проект: ORGANISM\nПамять: "+db.count("memory_objects")+"\nИсточники: "+db.count("sources")+"\nСобытия: "+db.count("events")+"\nОпыт: "+db.count("experiences")+"\nЗадачи: "+db.count("tasks"));Button c=bt(hasCreds()?"Продолжить с ChatGPT":"Подключить ChatGPT");c.setOnClickListener(v->{if(hasCreds())showChat();else signIn();});content.addView(c);Button imp=bt("Добавить источник");imp.setOnClickListener(v->showImport());content.addView(imp);Button quickSettings=bt("Настройки и экспорт базы");quickSettings.setOnClickListener(v->showSettings());content.addView(quickSettings);}
    void showQuickMenu(){
        String[] labels={"Подключить / переподключить ChatGPT","Новый диалог","Копировать текущий диалог","Старая общая история","Context Snapshot","Память","Задачи","Источники","Опыт","Проверка импорта","Импорт","База данных","Настройки","Экспорт полного архива"};
        Runnable[] actions={()->signIn(),()->startNewChat(),()->copyText("История чата",db.chat(activeSessionId)),()->showLegacyChat(),()->showContextDialog(),()->showMemory(),()->showTasks(),()->showSources(),()->showExperience(),()->showImportAudit(),()->showImport(),()->showDatabase(),()->showSettings(),()->backup()};
        new AlertDialog.Builder(this).setTitle("ORGANISM · Дополнительные функции").setItems(labels,(dialog,which)->actions[which].run()).show();
    }
    long ensureActiveSession(){String key=getPrefs().getString("active_session_key","");if(key.isEmpty()){key="SES-"+UUID.randomUUID().toString();getPrefs().edit().putString("active_session_key",key).apply();}return db.ensureSession(key,db.project("ORGANISM"));}
    void startNewChat(){if(busy){toast("Дождитесь завершения текущего запроса.");return;}String key="SES-"+UUID.randomUUID().toString();getPrefs().edit().putString("active_session_key",key).apply();activeSessionId=ensureActiveSession();showChat();}
    void showLegacyChat(){TextView t=tv(db.legacyChat(),14,Color.rgb(30,36,48));t.setTextIsSelectable(true);ScrollView s=new ScrollView(this);s.addView(t);new AlertDialog.Builder(this).setTitle("Старая общая история").setView(s).setPositiveButton("Закрыть",null).show();}
    void showChat(){
        clear("Чат");
        activateChatLayout();
        // Chat is a full-screen workspace: the history takes all free height,
        // the composer stays at the bottom, and secondary actions live in the ••• menu.
        LinearLayout modelRow=new LinearLayout(this);
        modelRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView modelLabel=tv("Модель",14,Color.rgb(69,81,101));
        modelRow.addView(modelLabel,new LinearLayout.LayoutParams(dp(72),-2));
        modelSpinner=new Spinner(this);
        modelRow.addView(modelSpinner,new LinearLayout.LayoutParams(0,-2,1));
        content.addView(modelRow,new LinearLayout.LayoutParams(-1,-2));

        chatScroll=new ScrollView(this);
        chatScroll.setFillViewport(true);
        chatScroll.setClipToPadding(false);
        chatView=tv(db.chat(activeSessionId),14,Color.rgb(30,36,48));
        chatView.setTextIsSelectable(true);
        chatView.setPadding(dp(14),dp(12),dp(14),dp(12));
        chatView.setBackground(rounded(Color.WHITE,18));
        chatScroll.addView(chatView,new ScrollView.LayoutParams(-1,-2));
        LinearLayout.LayoutParams historyParams=new LinearLayout.LayoutParams(-1,0,1f);
        historyParams.setMargins(0,dp(6),0,dp(8));
        content.addView(chatScroll,historyParams);

        LinearLayout composer=new LinearLayout(this);
        composer.setOrientation(LinearLayout.VERTICAL);
        composer.setBackground(rounded(Color.WHITE,20));
        composer.setPadding(dp(10),dp(5),dp(10),dp(8));
        chatInput=new EditText(this);
        chatInput.setHint("Напишите запрос…");
        chatInput.setMinLines(1);
        chatInput.setMaxLines(5);
        chatInput.setGravity(Gravity.TOP|Gravity.START);
        chatInput.setTextSize(16);
        chatInput.setBackgroundColor(Color.TRANSPARENT);
        composer.addView(chatInput,new LinearLayout.LayoutParams(-1,-2));
        Button send=bt("Отправить через ORGANISM → GPT");
        send.setOnClickListener(v->send());
        composer.addView(send,new LinearLayout.LayoutParams(-1,-2));
        content.addView(composer,new LinearLayout.LayoutParams(-1,-2));
        if(hasCreds())loadModels();
        else{
            TextView local=tv("ORGANISM работает локально. Подключите ChatGPT через меню •••, чтобы отправлять запросы модели.",13,Color.rgb(91,108,133));
            content.addView(local,new LinearLayout.LayoutParams(-1,-2));
        }
    }
    void showContextDialog(){
        String snapshot=contextEngine.build(chatInput==null?"":chatInput.getText().toString(),activeSessionId);
        TextView t=tv(snapshot,14,Color.rgb(30,36,48));
        t.setTextIsSelectable(true);
        t.setPadding(18,18,18,18);
        ScrollView sc=new ScrollView(this);sc.addView(t);
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.addView(sc,new LinearLayout.LayoutParams(-1,0,1f));
        Button copy=bt("Копировать контекст");copy.setOnClickListener(v->copyText("Context Snapshot",snapshot));box.addView(copy);
        AlertDialog d=new AlertDialog.Builder(this).setTitle("Context Engine").setView(box).setPositiveButton("Закрыть",null).create();
        d.show();
    }
    void copyText(String label,String text){
        ClipboardManager cm=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText(label,text==null?"":text));
        toast("Скопировано: "+label);
    }
    void showMemory(){clear("Память");card("Memory Objects","Все знания, решения, заметки, контексты и извлечённые данные хранятся с logical ID, provenance, claim/verification status и confidence.");TextView list=tv(db.recent("memory_objects"),13,Color.DKGRAY);content.addView(list);}
    void showDatabase(){clear("База");String body="Projects: "+db.count("projects")+"\nStates: "+db.count("project_states")+"\nTasks: "+db.count("tasks")+"\nEvents: "+db.count("events")+"\nActions: "+db.count("actions")+"\nResults: "+db.count("results")+"\nVerifications: "+db.count("verifications")+"\nMemory: "+db.count("memory_objects")+"\nRelations: "+db.count("relations")+"\nExperiences: "+db.count("experiences")+"\nRejected paths: "+db.count("rejected_paths")+"\nLoss coverage: "+db.count("loss_coverage");card("Структура хранилища",body);Button mem=bt("Показать память");mem.setOnClickListener(v->showMemory());content.addView(mem);Button exp=bt("Показать опыт");exp.setOnClickListener(v->showExperience());content.addView(exp);Button src=bt("Показать источники");src.setOnClickListener(v->showSources());content.addView(src);Button tasks=bt("Показать задачи");tasks.setOnClickListener(v->showTasks());content.addView(tasks);}
    void showExperience(){clear("Опыт");card("Experience","Опыт не является догмой. Он хранит what happened / tried / worked / failed, confidence и applicability.");content.addView(tv(db.recent("experiences"),13,Color.DKGRAY));}
    void showSources(){clear("Источники");card("Provenance","RAW сохраняется отдельно. Каждый импорт получает source record и checksum.");content.addView(tv(db.recent("sources"),13,Color.DKGRAY));}
    void showTasks(){clear("Задачи");card("Задачи","DONE не устанавливается автоматически как подтверждённое завершение.");content.addView(tv(db.recent("tasks"),13,Color.DKGRAY));}
    void showImport(){clear("Импорт данных");card("История чатов ChatGPT","Выберите ZIP, скачанный через экспорт данных ChatGPT. Импорт выполняется потоком, чтобы большой архив не загружал целиком оперативную память телефона. Исходный ZIP сохраняется в RAW.");Button file=bt("Выбрать ZIP с историей ChatGPT");file.setOnClickListener(v->{Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("application/zip");i.addCategory(Intent.CATEGORY_OPENABLE);i.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"application/zip","application/x-zip-compressed","application/octet-stream"});startActivityForResult(i,700);});content.addView(file,new LinearLayout.LayoutParams(-1,-2));Button other=bt("Импортировать TXT / MD / PDF");other.setOnClickListener(v->{Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("*/*");i.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,701);});content.addView(other,new LinearLayout.LayoutParams(-1,-2));Button audit=bt("Проверить результат импорта");audit.setOnClickListener(v->showImportAudit());content.addView(audit,new LinearLayout.LayoutParams(-1,-2));Button url=bt("Импортировать по ссылке");url.setOnClickListener(v->urlDialog());content.addView(url,new LinearLayout.LayoutParams(-1,-2));Button paste=bt("Вставить текст");paste.setOnClickListener(v->pasteDialog());content.addView(paste,new LinearLayout.LayoutParams(-1,-2));Button info=bt("Как работает импорт");info.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("Безопасный импорт").setMessage("1. Исходный файл сохраняется отдельно.\n2. ZIP читается потоком, без загрузки всего архива в память.\n3. Беседы разбираются по одной.\n4. Сообщения сохраняются с источником и структурными связями.\n5. Результат можно проверить в аудите импорта.").setPositiveButton("Понятно",null).show());content.addView(info,new LinearLayout.LayoutParams(-1,-2));}
    void showImportAudit(){
        clear("Проверка импорта");
        card("Контроль импорта чатов","Здесь показываются реальные записи SQLite, а не только сообщение об успешном импорте. Сравните количество узлов и сообщений с исходным экспортом.");
        android.database.Cursor raw=db.query("SELECT COUNT(*),COALESCE(SUM(LENGTH(raw_text)),0) FROM sources WHERE source_type='CHAT_EXPORT_RAW'",null);
        long rawCount=0,rawChars=0;if(raw.moveToFirst()){rawCount=raw.getLong(0);rawChars=raw.getLong(1);}raw.close();
        android.database.Cursor conv=db.query("SELECT COUNT(*) FROM sources WHERE source_type='CHAT_EXPORT_CONVERSATION'",null);
        long conversations=0;if(conv.moveToFirst())conversations=conv.getLong(0);conv.close();
        android.database.Cursor messages=db.query("SELECT COUNT(*) FROM memory_objects WHERE kind='CHAT_MESSAGE'",null);
        long messageObjects=0;if(messages.moveToFirst())messageObjects=messages.getLong(0);messages.close();
        android.database.Cursor events=db.query("SELECT COUNT(*) FROM events WHERE kind='CHAT_MESSAGE_IMPORTED'",null);
        long importedEvents=0;if(events.moveToFirst())importedEvents=events.getLong(0);events.close();
        card("Сводка хранилища","RAW-файлов ChatGPT: "+rawCount+"\nОбъектов бесед: "+conversations+"\nСохранённых сообщений: "+messageObjects+"\nСобытий импорта сообщений: "+importedEvents+"\nСимволов в RAW: "+rawChars+"\n\nВажно: повторный импорт пока может создавать дубликаты. Эти числа — количество записей в базе, а не число уникальных сообщений.");
        android.database.Cursor rawFiles=db.query("SELECT id,source_name,checksum,LENGTH(raw_text) FROM sources WHERE source_type='CHAT_EXPORT_RAW' ORDER BY id DESC LIMIT 20",null);
        while(rawFiles.moveToNext()){long rawId=rawFiles.getLong(0);String rawName=rawFiles.getString(1);String rawHash=rawFiles.getString(2);long rawLength=rawFiles.getLong(3);Button rawButton=bt("Посмотреть RAW: "+rawName);rawButton.setOnClickListener(v->showRawChatExport(rawId,rawName));content.addView(rawButton);content.addView(tv("SHA-256: "+rawHash+" | символов: "+rawLength,11,Color.DKGRAY));}
        rawFiles.close();
        android.database.Cursor c=db.query("SELECT s.id,s.source_name,s.external_id,s.checksum,(SELECT COUNT(*) FROM events e WHERE e.source_id=s.id AND e.kind='CHAT_MESSAGE_IMPORTED'),(SELECT COUNT(*) FROM memory_objects m WHERE m.source_id=s.id AND m.kind='CHAT_MESSAGE'),(SELECT description FROM events e WHERE e.source_id=s.id AND e.kind='CHAT_IMPORTED' ORDER BY e.id DESC LIMIT 1) FROM sources s WHERE s.source_type='CHAT_EXPORT_CONVERSATION' ORDER BY s.id DESC LIMIT 100",null);
        if(!c.moveToFirst()){c.close();card("Беседы","Импортированные беседы не найдены. Выберите ChatGPT ZIP на экране «Импорт».");Button back=bt("← Назад к импорту");back.setOnClickListener(v->showImport());content.addView(back);return;}
        do{
            long id=c.getLong(0);String title=c.getString(1);String conversationId=c.getString(2);String checksum=c.getString(3);long eventCount=c.getLong(4);long memoryCount=c.getLong(5);String summary=c.getString(6);
            String label=(title==null?"Без названия":title)+"\nconversation_id: "+(conversationId==null||conversationId.isEmpty()?"не указан":conversationId)+"\nСобытий: "+eventCount+" | объектов сообщений: "+memoryCount+"\n"+(summary==null?"Нет итоговой записи CHAT_IMPORTED":summary)+"\nSHA-256 беседы: "+(checksum==null?"нет":checksum);
            Button b=bt("Открыть: "+(title==null?"Беседа":title)+" ("+memoryCount+" сообщений)");
            b.setOnClickListener(v->showImportedConversation(id,title==null?"Беседа":title));
            content.addView(b);TextView details=tv(label,12,Color.DKGRAY);details.setTextIsSelectable(true);content.addView(details);
        }while(c.moveToNext());
        c.close();
        Button sources=bt("Открыть список источников");sources.setOnClickListener(v->showSources());content.addView(sources);
    }
    void showRawChatExport(long rawSourceId,String name){
        clear("RAW-экспорт");
        Button back=bt("← К проверке импорта");back.setOnClickListener(v->showImportAudit());content.addView(back);
        android.database.Cursor c=db.query("SELECT checksum,LENGTH(raw_text),raw_text FROM sources WHERE id=? AND source_type='CHAT_EXPORT_RAW'",new String[]{""+rawSourceId});
        if(!c.moveToFirst()){c.close();card("RAW не найден","Исходный JSON не найден в таблице sources.");return;}
        String hash=c.getString(0);long length=c.getLong(1);String raw=c.getString(2);c.close();
        card(name,"Исходный RAW из SQLite. Размер: "+length+" символов. SHA-256: "+hash+"\nПоказаны начало и конец, чтобы не перегружать экран; полный текст остаётся сохранённым в базе.");
        String preview=raw==null?"[raw_text отсутствует]":raw.substring(0,Math.min(5000,raw.length()));
        TextView first=tv("НАЧАЛО RAW\n"+preview,12,Color.rgb(35,43,58));first.setTextIsSelectable(true);first.setPadding(12,12,12,12);first.setBackgroundColor(Color.WHITE);content.addView(first);
        if(raw!=null&&raw.length()>5000){String tail=raw.substring(Math.max(0,raw.length()-3000));TextView last=tv("КОНЕЦ RAW\n"+tail,12,Color.rgb(35,43,58));last.setTextIsSelectable(true);last.setPadding(12,12,12,12);last.setBackgroundColor(Color.WHITE);content.addView(last);}
    }
    void showImportedConversation(long sourceId,String title){showImportedConversation(sourceId,title,0);}
    void showImportedConversation(long sourceId,String title,int page){
        clear("Проверка беседы");
        Button back=bt("← К списку импортов");back.setOnClickListener(v->showImportAudit());content.addView(back);
        long total=Long.parseLong(db.queryOne("SELECT COUNT(*) FROM memory_objects WHERE kind='CHAT_MESSAGE' AND source_id=?",new String[]{""+sourceId}));
        card(title,"Показана страница "+(page+1)+" из "+Math.max(1,(int)Math.ceil(total/100.0))+". Всего сообщений в этой беседе: "+total+". Фрагменты прочитаны из SQLite; статусы не означают, что содержание независимо проверено.");
        android.database.Cursor c=db.query("SELECT title,content,claim_status,verification_status,source_id FROM memory_objects WHERE kind='CHAT_MESSAGE' AND source_id=? ORDER BY id ASC LIMIT 100 OFFSET "+(page*100),new String[]{""+sourceId});
        int count=0;
        while(c.moveToNext()){
            count++;String head=c.getString(0);String body=c.getString(1);
            TextView item=tv("\n"+head+"\n"+body+"\nСтатус: "+c.getString(2)+" / "+c.getString(3)+" | source_id="+c.getString(4),13,Color.rgb(35,43,58));
            item.setTextIsSelectable(true);item.setPadding(12,12,12,12);item.setBackgroundColor(Color.WHITE);content.addView(item);
        }
        c.close();
        if(count==0)card("Нет сообщений","Для этой беседы не найдены объекты CHAT_MESSAGE. Это признак неполного импорта или несоответствия source_id.");
        else card("Проверка содержимого","Сверьте сообщения этой страницы с исходным экспортом. Начало, середина и конец проверяются переходом между страницами.");
        if(page>0){Button prev=bt("← Предыдущие 100 сообщений");prev.setOnClickListener(v->showImportedConversation(sourceId,title,page-1));content.addView(prev);}
        if((page+1)*100<total){Button next=bt("Следующие 100 сообщений →");next.setOnClickListener(v->showImportedConversation(sourceId,title,page+1));content.addView(next);}
    }
    void urlDialog(){EditText e=new EditText(this);e.setHint("https://…");new AlertDialog.Builder(this).setTitle("Импорт URL").setView(e).setNegativeButton("Отмена",null).setPositiveButton("Импортировать",(d,w)->{String u=e.getText().toString().trim();if(!u.isEmpty())importer.importUrl(u,new ImportPipeline.Listener(){public void done(String m){main.post(()->{toast(m);showDatabase();});}public void fail(String m){main.post(()->toast("Ошибка: "+m));}});}).show();}
    void pasteDialog(){EditText e=new EditText(this);e.setMinLines(10);e.setGravity(Gravity.TOP);new AlertDialog.Builder(this).setTitle("Вставить текст").setView(e).setNegativeButton("Отмена",null).setPositiveButton("Сохранить",(d,w)->{importer.importText("Вставленный текст",e.getText().toString(),new ImportPipeline.Listener(){public void done(String m){main.post(()->{toast(m);showDatabase();});}public void fail(String m){main.post(()->toast("Ошибка: "+m));}});}).show();}
    @Override protected void onActivityResult(int req,int res,Intent data){super.onActivityResult(req,res,data);if((req==700||req==701)&&res==RESULT_OK&&data!=null&&data.getData()!=null){final Uri uri=data.getData();try{getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Exception ignored){}cardImportProgress();importer.importUri(uri,new ImportPipeline.Listener(){public void done(String m){main.post(()->{toast(m);showImportAudit();});}public void fail(String m){main.post(()->new AlertDialog.Builder(MainActivity.this).setTitle("Импорт не завершён").setMessage(m+"\n\nИсходный файл не удалён. Можно повторить импорт или прислать этот текст ошибки.").setPositiveButton("Понятно",null).show());}});}} 
    void cardImportProgress(){clear("Импорт выполняется");card("Обрабатываем архив","Не закрывайте приложение. Большие истории могут обрабатываться несколько минут. Исходный файл не изменяется.");}
    void showSettings(){clear("Настройки");card("ChatGPT","Sign in with ChatGPT. API key не нужен. Доступ к чатам ChatGPT не предоставляется: ORGANISM ведёт собственную историю и базу.");Button c=bt(hasCreds()?"Переподключить":"Подключить ChatGPT");c.setOnClickListener(v->signIn());content.addView(c);Button out=bt("Выйти из ChatGPT");out.setOnClickListener(v->{clearCreds();showSettings();});content.addView(out);Button archive=bt("Экспорт полного архива (чаты + память + файлы)");archive.setOnClickListener(v->backupArchive());content.addView(archive);Button backup=bt("Экспорт только базы SQLite");backup.setOnClickListener(v->backup());content.addView(backup);card("Переносимость","Полный архив содержит SQLite и внутренние файлы RAW/событий. Экспорт уже реализуется как единый пакет; восстановление архива на другом устройстве будет отдельным проверяемым шагом.");card("Защита","Удаление памяти проходит через рефлекс защиты; RAW и события не заменяются кратким резюме. Удаление критической памяти автоматически не каскадирует связи.");}
    void backup(){if(busy){toast("Сначала дождитесь завершения текущего запроса.");return;}try{File f=new File(getExternalFilesDir(null),"organism-backup.db");copyDb(f);Intent i=new Intent(Intent.ACTION_SEND);i.setType("application/octet-stream");i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);i.putExtra(Intent.EXTRA_STREAM,FileProvider.getUriForFile(this,getPackageName()+".files",f));startActivity(Intent.createChooser(i,"Передать резервную копию"));}catch(Exception e){toast("Резервная копия не создана: "+e.getMessage());}}
    void backupArchive(){if(busy){toast("Сначала дождитесь завершения текущего запроса.");return;}try{File base=new File(getExternalFilesDir(null),"organism-full-archive");if(!base.exists()&&!base.mkdirs())throw new IOException("Не удалось создать каталог архива");File database=new File(base,"organism.db");copyDb(database);File zip=new File(getExternalFilesDir(null),"organism-full-archive.zip");try(ZipOutputStream out=new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(zip)))){putZipFile(out,database,"database/organism.db");File privateFiles=getFilesDir();zipDirectory(out,privateFiles,"app-files/");String manifest="ORGANISM PORTABLE ARCHIVE\nformat_version=1\ncreated_at="+db.now()+"\ndatabase=database/organism.db\napp_private_files=app-files/\ncontents=SQLite database (events, sources, memory, experiences, relations, imported chat messages), plus app-private RAW files, events.jsonl and PROJECT_MEMORY.md when present.\nNOTE=This archive is a data export. Safe restore/import on another device must validate schema and integrity before replacing any existing data.\n";out.putNextEntry(new ZipEntry("ARCHIVE_MANIFEST.txt"));out.write(manifest.getBytes(StandardCharsets.UTF_8));out.closeEntry();}android.database.sqlite.SQLiteDatabase check=android.database.sqlite.SQLiteDatabase.openDatabase(database.getAbsolutePath(),null,android.database.sqlite.SQLiteDatabase.OPEN_READONLY);try{android.database.Cursor c=check.rawQuery("PRAGMA integrity_check",null);try{if(!c.moveToFirst()||!"ok".equalsIgnoreCase(c.getString(0)))throw new IOException("Проверка SQLite не пройдена");}finally{c.close();}}finally{check.close();}Intent i=new Intent(Intent.ACTION_SEND);i.setType("application/zip");i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);i.putExtra(Intent.EXTRA_STREAM,FileProvider.getUriForFile(this,getPackageName()+".files",zip));startActivity(Intent.createChooser(i,"Передать полный архив ORGANISM"));}catch(Exception e){toast("Полный архив не создан: "+e.getMessage());}}
    void putZipFile(ZipOutputStream out,File file,String name)throws Exception{out.putNextEntry(new ZipEntry(name));try(InputStream in=new BufferedInputStream(new FileInputStream(file))){byte[] b=new byte[8192];int n;while((n=in.read(b))>0)out.write(b,0,n);}out.closeEntry();}
    void zipDirectory(ZipOutputStream out,File dir,String prefix)throws Exception{File[] children=dir.listFiles();if(children==null)return;Arrays.sort(children,Comparator.comparing(File::getName));for(File child:children){if(child.isDirectory()){zipDirectory(out,child,prefix+child.getName()+"/");}else{String name=prefix+child.getName();if(name.equals("app-files/organism.db")||name.endsWith(".tmp"))continue;putZipFile(out,child,name);}}}
    // Model output is not experience by itself; reusable experience requires an observed result and a separate check.
    void send(){if(busy){toast("Предыдущий запрос ещё выполняется. Дождитесь ответа.");return;}if(chatInput==null)return;String q=chatInput.getText().toString().trim();if(q.isEmpty())return;if(!hasCreds()){toast("Сначала подключите ChatGPT");return;}busy=true;chatInput.setText("");Map<String,Object> a=new HashMap<>();a.put("claim_status","STATED");a.put("qualified",true);Map<String,Object> c=new HashMap<>();c.put("confidence",1.0);for(ReflexEngine.Result r:reflex.check(c,a))if(r.block){busy=false;toast(r.message);return;}long p=db.project("ORGANISM");String turnId=UUID.randomUUID().toString();db.event("USER_MESSAGE",q,p,0,0,"USER_STATED","NOT_VERIFIED",turnId,activeSessionId);chatView.setText(db.chat(activeSessionId)+"\n\nORGANISM → модель: …");new Thread(()->{try{refreshIfNeeded();String context=contextEngine.build(q,activeSessionId);String answer=infer(q,context);long src=db.source("MODEL_RESPONSE","OpenAI / "+model,null,answer,null,0,turnId);db.event("MODEL_OUTPUT",answer,p,0,src,"MODEL_OUTPUT","NOT_VERIFIED",turnId,activeSessionId);try{db.memory("MODEL_OUTPUT","Ответ на: "+shorten(q,80),answer,p,0,src,"MODEL_OUTPUT","NOT_VERIFIED",0.5);JSONObject experienceMeta=new JSONObject();experienceMeta.put("status","CANDIDATE_UNVERIFIED");experienceMeta.put("turn_id",turnId);experienceMeta.put("source_id",src);experienceMeta.put("model",model);experienceMeta.put("rule","Model output alone is not evidence of success; verify an observable outcome before reuse.");db.experience("Диалоговый ход: "+shorten(q,240),"Модель "+model+" сформировала ответ","", "",p,0.2,"CANDIDATE",experienceMeta.toString());db.event("EXPERIENCE_CANDIDATE_CREATED","turn_id="+turnId+"; status=CANDIDATE_UNVERIFIED; requires outcome verification",p,0,src,"MODEL_OUTPUT","NOT_VERIFIED",turnId);}catch(Exception persistenceWarning){Log.e("ORGANISM","Model answer saved, but optional memory/experience indexing failed for turn "+turnId,persistenceWarning);try{db.event("POST_RESPONSE_PERSISTENCE_WARNING","turn_id="+turnId+"; "+String.valueOf(persistenceWarning.getMessage()),p,0,src,"UNKNOWN","NOT_VERIFIED",turnId,activeSessionId);}catch(Exception logFailure){Log.e("ORGANISM","Could not persist post-response warning",logFailure);}}main.post(()->{busy=false;chatView.setText(db.chat(activeSessionId));if(chatScroll!=null)chatScroll.post(()->chatScroll.fullScroll(View.FOCUS_DOWN));});}catch(Exception e){db.event("ERROR",e.getMessage()==null?e.toString():e.getMessage(),p,0,0,"UNKNOWN","NOT_VERIFIED",turnId,activeSessionId);main.post(()->{busy=false;chatView.setText(db.chat(activeSessionId));toast("Ошибка: "+e.getMessage());});}}).start();}
    String shorten(String s,int n){return s.length()<=n?s:s.substring(0,n);}
    String infer(String q,String context)throws Exception{if(model.isEmpty())model=chooseModel();JSONObject body=new JSONObject();body.put("model",model);body.put("instructions","Ты работаешь через ORGANISM. Используй переданный контекст условно и применимо. Не выдавай HYPOTHESIS, UNKNOWN, MISSING_DATA или непроверенный опыт за подтверждённые факты.");body.put("input",new JSONArray().put(new JSONObject().put("role","user").put("content","CONTEXT SNAPSHOT:\n"+context+"\n\nUSER REQUEST:\n"+q)));body.put("store",false);body.put("stream",true);HttpURLConnection c=(HttpURLConnection)new URL(API+"/responses").openConnection();c.setRequestMethod("POST");c.setDoOutput(true);c.setConnectTimeout(20000);c.setReadTimeout(120000);c.setRequestProperty("Authorization","Bearer "+accessToken);c.setRequestProperty("Content-Type","application/json");c.getOutputStream().write(body.toString().getBytes(StandardCharsets.UTF_8));int code=c.getResponseCode();if(code>=400)throw new Exception(readAll(c.getErrorStream()));BufferedReader r=new BufferedReader(new InputStreamReader(c.getInputStream(),StandardCharsets.UTF_8));String line;StringBuilder out=new StringBuilder();boolean completed=false;while((line=r.readLine())!=null){if(!line.startsWith("data:"))continue;String d=line.substring(5).trim();if("[DONE]".equals(d))continue;try{JSONObject e=new JSONObject(d);String type=e.optString("type");if("response.output_text.delta".equals(type))out.append(e.optString("delta"));else if("response.completed".equals(type))completed=true;else if("response.failed".equals(type)){JSONObject rr=e.optJSONObject("response");throw new Exception(rr==null?"response.failed":rr.optString("error","response.failed"));}}catch(JSONException ignored){}}if(!completed)throw new Exception("Поток не завершился response.completed");return out.toString().trim();}
    String chooseModel()throws Exception{listModels();if(model.isEmpty())throw new Exception("Нет доступной модели");return model;}
    void loadModels(){new Thread(()->{try{listModels();main.post(this::fillModels);}catch(Exception e){main.post(()->toast("Модели: "+e.getMessage()));}}).start();}
    void listModels()throws Exception{HttpURLConnection c=(HttpURLConnection)new URL(API+"/models").openConnection();c.setConnectTimeout(15000);c.setReadTimeout(20000);c.setRequestProperty("Authorization","Bearer "+accessToken);int code=c.getResponseCode();if(code>=400)throw new Exception("GET /models HTTP "+code+": "+readAll(c.getErrorStream()));JSONObject j=new JSONObject(readAll(c.getInputStream()));JSONArray a=j.optJSONArray("models");if(a==null)a=j.optJSONArray("data");modelSlugs.clear();modelNames.clear();if(a!=null)for(int i=0;i<a.length();i++){JSONObject m=a.getJSONObject(i);String slug=m.optString("slug",m.optString("id",""));String vis=m.optString("visibility","");if(!slug.isEmpty()&&(vis.isEmpty()||"list".equals(vis))){modelSlugs.add(slug);modelNames.add(m.optString("display_name",slug));}}if(modelSlugs.isEmpty())throw new Exception("OpenAI вернул пустой каталог моделей");String saved=getPrefs().getString("model","");int ix=modelSlugs.indexOf(saved);if(ix<0)ix=0;model=modelSlugs.get(ix);getPrefs().edit().putString("model",model).apply();}
    void fillModels(){if(modelSpinner==null)return;ArrayAdapter<String>a=new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,modelNames);a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);modelSpinner.setAdapter(a);int ix=modelSlugs.indexOf(model);if(ix>=0)modelSpinner.setSelection(ix);modelSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){public void onItemSelected(android.widget.AdapterView<?>p,View v,int pos,long id){if(pos>=0&&pos<modelSlugs.size()){model=modelSlugs.get(pos);getPrefs().edit().putString("model",model).apply();}}public void onNothingSelected(android.widget.AdapterView<?>p){}});}
    void refreshIfNeeded()throws Exception{if(accessToken.isEmpty()||System.currentTimeMillis()/1000+90<expiresAt)return;if(refreshToken.isEmpty())throw new Exception("Нужна повторная авторизация");String client=getPrefs().getString("client_id","");JSONObject t=postForm(TOKEN,new String[][]{{"grant_type","refresh_token"},{"client_id",client},{"refresh_token",refreshToken},{"resource",RESOURCE}});accessToken=t.getString("access_token");refreshToken=t.optString("refresh_token",refreshToken);expiresAt=System.currentTimeMillis()/1000+t.optLong("expires_in",3600);saveCreds();}
    void signIn(){if(busy)return;busy=true;new Thread(()->{try{if(callbackSocket!=null){try{callbackSocket.close();}catch(Exception ignored){}callbackSocket=null;}ServerSocket localSocket=new ServerSocket();
                localSocket.setReuseAddress(true);
                try{
                    localSocket.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.1"),0));
                }catch(Exception bindAny){
                    localSocket.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.1"),CALLBACK_PORT_HINT));
                }
                int callbackPort=localSocket.getLocalPort();
                pendingRedirect="http://127.0.0.1:"+callbackPort+"/auth/callback";
                callbackSocket=localSocket;
                main.post(()->toast("Ожидание возврата OpenAI…"));pendingState=random(32);pendingNonce=random(32);pendingVerifier=random(48);String challenge=b64(MessageDigest.getInstance("SHA-256").digest(pendingVerifier.getBytes(StandardCharsets.UTF_8)));String client=getPrefs().getString("client_id","dynamic_agent_client");String host=getPrefs().getString("host_id","");if(host.isEmpty()){host="urn:uuid:"+UUID.randomUUID();getPrefs().edit().putString("host_id",host).apply();}Uri.Builder u=Uri.parse(AUTH).buildUpon();u.appendQueryParameter("client_id",client).appendQueryParameter("redirect_uri",pendingRedirect).appendQueryParameter("response_type","code").appendQueryParameter("scope","openid profile email offline_access resource.invoke chatgpt.tokens.use.direct").appendQueryParameter("resource",RESOURCE).appendQueryParameter("state",pendingState).appendQueryParameter("nonce",pendingNonce).appendQueryParameter("code_challenge_method","S256").appendQueryParameter("code_challenge",challenge);if("dynamic_agent_client".equals(client)){u.appendQueryParameter("agent_name_hint","ОРГАНИЗМ");u.appendQueryParameter("ext_agent_host_id",host);}else{String hint=getPrefs().getString("id_token_hint","");String email=getPrefs().getString("email","");if(!hint.isEmpty())u.appendQueryParameter("id_token_hint",hint);if(!email.isEmpty())u.appendQueryParameter("login_hint",email);}startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(u.toString())));localSocket.setSoTimeout(120000);
                Socket s=localSocket.accept();
                BufferedReader r=new BufferedReader(new InputStreamReader(s.getInputStream(),StandardCharsets.UTF_8));
                String req=r.readLine();
                if(req==null||!req.startsWith("GET "))throw new Exception("Loopback callback: неверный HTTP-запрос");
                String[] requestParts=req.split(" ");
                if(requestParts.length<2)throw new Exception("Loopback callback: отсутствует путь");
                String path=requestParts[1];String body="<html><body><h3>ОРГАНИЗМ</h3>Авторизация завершена. Вернитесь в приложение.</body></html>";byte[] bb=body.getBytes(StandardCharsets.UTF_8);OutputStream os=s.getOutputStream();os.write(("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: "+bb.length+"\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.UTF_8));os.write(bb);os.flush();s.close();try{localSocket.close();}catch(Exception ignored){}callbackSocket=null;Uri cb=Uri.parse("http://127.0.0.1"+path);if(!pendingState.equals(cb.getQueryParameter("state")))throw new Exception("OAuth state не совпал");if(cb.getQueryParameter("error")!=null)throw new Exception("Авторизация отменена: "+cb.getQueryParameter("error"));String code=cb.getQueryParameter("code");if(code==null)throw new Exception("OAuth code отсутствует");String issued=cb.getQueryParameter("client_id");if("dynamic_agent_client".equals(client)){if(issued==null||issued.isEmpty())throw new Exception("OpenAI не вернул выданный client_id");client=issued;getPrefs().edit().putString("client_id",client).apply();}JSONObject tok=postForm(TOKEN,new String[][]{{"grant_type","authorization_code"},{"client_id",client},{"code",code},{"code_verifier",pendingVerifier},{"redirect_uri",pendingRedirect},{"resource",RESOURCE}});idToken=tok.optString("id_token","");if(idToken.isEmpty())throw new Exception("ID token отсутствует");verifyIdToken(idToken,client,pendingNonce);if(!tok.optString("scope","").contains("chatgpt.tokens.use.direct"))throw new Exception("Разрешение chatgpt.tokens.use.direct не выдано этому аккаунту");accessToken=tok.getString("access_token");refreshToken=tok.optString("refresh_token","");expiresAt=System.currentTimeMillis()/1000+tok.optLong("expires_in",3600);JSONObject p=jwtPart(idToken,1);getPrefs().edit().putString("id_token_hint",idToken).putString("email",p.optString("email","")).apply();saveCreds();main.post(()->{busy=false;toast("ChatGPT подключён");showChat();});}catch(Exception e){try{if(callbackSocket!=null)callbackSocket.close();}catch(Exception ignored){}callbackSocket=null;busy=false;main.post(()->toast("Подключение: "+(e.getMessage()==null?e.getClass().getSimpleName():e.getMessage())));}}).start();}
    void verifyIdToken(String jwt,String client,String nonce)throws Exception{JSONObject h=jwtPart(jwt,0),p=jwtPart(jwt,1);if(!"RS256".equals(h.optString("alg")))throw new Exception("Неподдерживаемая подпись");if(!"https://auth.openai.com".equals(p.optString("iss")))throw new Exception("Неверный issuer");if(p.optLong("exp",0)<System.currentTimeMillis()/1000)throw new Exception("ID token истёк");boolean aud=client.equals(p.optString("aud"));JSONArray aa=p.optJSONArray("aud");if(aa!=null)for(int i=0;i<aa.length();i++)aud|=client.equals(aa.optString(i));if(!aud)throw new Exception("Неверная audience");if(!nonce.equals(p.optString("nonce")))throw new Exception("Неверный nonce");JSONObject jwks=new JSONObject(readUrl(JWKS));JSONArray keys=jwks.getJSONArray("keys");String kid=h.optString("kid");for(int i=0;i<keys.length();i++){JSONObject k=keys.getJSONObject(i);if(kid.equals(k.optString("kid"))){BigInteger n=new BigInteger(1,Base64.decode(k.getString("n"),Base64.URL_SAFE|Base64.NO_WRAP|Base64.NO_PADDING));BigInteger e=new BigInteger(1,Base64.decode(k.getString("e"),Base64.URL_SAFE|Base64.NO_WRAP|Base64.NO_PADDING));PublicKey pk=KeyFactory.getInstance("RSA").generatePublic(new RSAPublicKeySpec(n,e));String[] parts=jwt.split("\\.");Signature s=Signature.getInstance("SHA256withRSA");s.initVerify(pk);s.update((parts[0]+"."+parts[1]).getBytes(StandardCharsets.UTF_8));if(!s.verify(Base64.decode(parts[2],Base64.URL_SAFE|Base64.NO_WRAP|Base64.NO_PADDING)))throw new Exception("Неверная подпись ID token");return;}}throw new Exception("Ключ подписи не найден");}
    JSONObject postForm(String url,String[][] fields)throws Exception{HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();c.setRequestMethod("POST");c.setDoOutput(true);c.setConnectTimeout(20000);c.setReadTimeout(30000);c.setRequestProperty("Content-Type","application/x-www-form-urlencoded");StringBuilder b=new StringBuilder();for(String[]f:fields){if(b.length()>0)b.append('&');b.append(URLEncoder.encode(f[0],"UTF-8")).append('=').append(URLEncoder.encode(f[1],"UTF-8"));}c.getOutputStream().write(b.toString().getBytes(StandardCharsets.UTF_8));int sc=c.getResponseCode();String x=readAll(sc>=400?c.getErrorStream():c.getInputStream());if(sc>=400)throw new Exception("OAuth HTTP "+sc+": "+x);return new JSONObject(x);}
    String readUrl(String u)throws Exception{HttpURLConnection c=(HttpURLConnection)new URL(u).openConnection();c.setConnectTimeout(15000);c.setReadTimeout(15000);return readAll(c.getInputStream());}
    String readAll(InputStream in)throws Exception{if(in==null)return "";BufferedReader r=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8));StringBuilder b=new StringBuilder();String s;while((s=r.readLine())!=null)b.append(s).append('\n');return b.toString();}
    JSONObject jwtPart(String jwt,int p)throws Exception{return new JSONObject(new String(Base64.decode(jwt.split("\\." )[p],Base64.URL_SAFE|Base64.NO_WRAP|Base64.NO_PADDING),StandardCharsets.UTF_8));}
    String random(int n){byte[]b=new byte[n];new SecureRandom().nextBytes(b);return b64(b);}String b64(byte[]b){return Base64.encodeToString(b,Base64.URL_SAFE|Base64.NO_WRAP|Base64.NO_PADDING);}
    SharedPreferences getPrefs(){return getSharedPreferences("organism",MODE_PRIVATE);}boolean hasCreds(){return !accessToken.isEmpty()||!refreshToken.isEmpty();}
    void saveCreds(){getPrefs().edit().putString("access",accessToken).putString("refresh",refreshToken).putLong("expires",expiresAt).apply();}
    void loadCreds(){accessToken=getPrefs().getString("access","");refreshToken=getPrefs().getString("refresh","");expiresAt=getPrefs().getLong("expires",0);idToken=getPrefs().getString("id_token_hint","");model=getPrefs().getString("model","");}
    void clearCreds(){accessToken="";refreshToken="";idToken="";expiresAt=0;model="";getPrefs().edit().remove("access").remove("refresh").remove("expires").remove("id_token_hint").remove("email").remove("client_id").remove("model").apply();}
    void copyDb(File dest)throws Exception{android.database.sqlite.SQLiteDatabase live=db.getWritableDatabase();android.database.Cursor checkpoint=live.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)",null);int checkpointBusy=0;try{if(checkpoint.moveToFirst())checkpointBusy=checkpoint.getInt(0);}finally{checkpoint.close();}if(checkpointBusy!=0)throw new IOException("База занята: не удалось безопасно завершить WAL checkpoint");File inFile=getDatabasePath("organism.db");try(java.io.FileInputStream in=new java.io.FileInputStream(inFile);java.io.FileOutputStream out=new java.io.FileOutputStream(dest)){byte[] b=new byte[8192];int n;while((n=in.read(b))>0)out.write(b,0,n);out.getFD().sync();}android.database.sqlite.SQLiteDatabase check=android.database.sqlite.SQLiteDatabase.openDatabase(dest.getAbsolutePath(),null,android.database.sqlite.SQLiteDatabase.OPEN_READONLY);try{android.database.Cursor verify=check.rawQuery("PRAGMA integrity_check",null);try{if(!verify.moveToFirst()||!"ok".equalsIgnoreCase(verify.getString(0)))throw new IOException("Проверка целостности резервной копии не пройдена");}finally{verify.close();}}finally{check.close();}}
    void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
    @Override public void onBackPressed(){if(!"home".equals(screen))showHome();else super.onBackPressed();}
}
