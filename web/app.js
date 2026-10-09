const KEY="organism_pwa_test_v01";
const state=load();
let currentView="home",busy=false;

function seed(){return{version:1,projects:[{id:"PRJ001",name:"Первый эксперимент",description:"Тестовый проект Организма",status:"ACTIVE"}],projectStates:[{id:"PST001",projectId:"PRJ001",name:"Исследование прототипа",status:"ACTIVE"}],sessions:[],messages:[],events:[],actions:[],results:[],verifications:[],memories:[],experiences:[],applicability:[],relations:[],rejectedPaths:[],sources:[],changes:[],tasks:[],settings:{agent:{type:"mock",baseUrl:"",apiKey:"",model:"",connected:false}}};}
function load(){try{return JSON.parse(localStorage.getItem(KEY))||seed()}catch(e){return seed()}}
function save(){localStorage.setItem(KEY,JSON.stringify(state))}
function uid(p){return p+Date.now().toString(36).toUpperCase()+Math.random().toString(36).slice(2,6).toUpperCase()}
function esc(s){return String(s??"").replace(/[&<>"']/g,function(c){return{"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;","'":"&#39;"}[c]})}
function project(){return state.projects[0]}
function toast(m){var e=document.createElement("div");e.className="notice";e.textContent=m;document.body.appendChild(e);setTimeout(function(){e.remove()},2400)}
function setView(v){currentView=v;render()}
function updateAgentStatus(){var a=state.settings.agent,b=document.getElementById("agentStatus");b.textContent=a.connected?"Агент: "+(a.type==="mock"?"тестовый":a.model||"подключён"):"Агент: не подключён";b.onclick=function(){setView("settings")}}
function contextSnapshot(msg){var words=msg.toLowerCase().split(/\W+/).filter(function(x){return x.length>2});var ex=state.experiences.map(function(e){var t=(e.title+" "+e.whatHappened+" "+e.whatWorked+" "+e.whatFailed+" "+(e.appliesWhen||"")).toLowerCase();var score=words.reduce(function(n,w){return n+(t.indexOf(w)>=0?1:0)},0);return Object.assign({},e,{score:score})}).filter(function(e){return e.score>0||e.projectId===project().id}).sort(function(a,b){return b.score*b.confidence-a.score*a.confidence}).slice(0,6);return{project:project(),state:state.projectStates.find(function(x){return x.projectId===project().id})||null,relevantExperiences:ex,memory:state.memories.slice(-12),pendingTasks:state.tasks.filter(function(t){return t.status!=="DONE"}).slice(0,10),missingInfo:[]}}
function showJSON(o){modal("<h2>Контекст</h2><pre>"+esc(JSON.stringify(o,null,2))+"</pre><button class='btn' id='closeM'>Закрыть</button>","closeM",closeModal)}
function showExperience(id){var e=state.experiences.find(function(x){return x.id===id});if(!e)return;modal("<h2>"+esc(e.title)+"</h2><div class='tag'>"+esc(e.type)+"</div><p>"+esc(e.whatHappened)+"</p><hr><b>Что пробовали</b><p>"+esc(e.whatTried)+"</p><b>Что сработало</b><p class='positive'>"+esc(e.whatWorked||"—")+"</p><b>Что не сработало</b><p class='negative'>"+esc(e.whatFailed||"—")+"</p><b>Применимость</b><p>"+esc(e.appliesWhen)+"</p><b>Не применять</b><p>"+esc(e.doesNotApplyWhen)+"</p><p>confidence: "+e.confidence.toFixed(2)+"</p><button class='btn' id='closeM'>Закрыть</button>","closeM",closeModal)}
function modal(html,id,fn){document.getElementById("modalBody").innerHTML=html;document.getElementById("modal").classList.remove("hidden");setTimeout(function(){var e=document.getElementById(id);if(e)e.onclick=fn},0)}
function closeModal(){document.getElementById("modal").classList.add("hidden")}

function render(){
document.querySelectorAll(".bottom-nav button").forEach(function(b){b.classList.toggle("active",b.dataset.view===currentView)});
var v=document.getElementById("view"),p=project();
if(currentView==="home")v.innerHTML="<h2>Центр Организма</h2><div class='card'><b>"+esc(p.name)+"</b><div class='muted'>"+esc(p.description)+"</div></div><div class='grid'><div class='stat'><b>"+state.memories.length+"</b><small>Память</small></div><div class='stat'><b>"+state.experiences.length+"</b><small>Опыт</small></div><div class='stat'><b>"+state.relations.length+"</b><small>Связи</small></div><div class='stat'><b>"+state.messages.length+"</b><small>Сообщения</small></div></div><div class='card'><h3>Главный цикл</h3><div class='muted'>Импорт → отсев → события → действия → результаты → проверки → опыт → связи → Context Engine → агент → новый опыт.</div><hr><button class='btn primary' data-action='import'>Загрузить TXT истории</button> <button class='btn' data-view2='chat'>Открыть чат</button></div><div class='card'><h3>Потоки</h3><span class='tag'>CHAT</span><span class='tag'>IMPORT</span><span class='tag'>MEMORY</span><span class='tag'>CONTEXT</span><span class='tag'>AGENT</span></div>";
else if(currentView==="projects")v.innerHTML="<h2>Проекты</h2><button class='btn primary' data-action='newProject'>+ Новый проект</button><div class='list'>"+state.projects.map(function(x){return"<div class='item'><b>"+esc(x.id)+" · "+esc(x.name)+"</b><span class='tag'>"+esc(x.status)+"</span><div class='muted'>"+esc(x.description)+"</div></div>"}).join("")+"</div>";
else if(currentView==="knowledge")v.innerHTML="<h2>База знаний</h2><div class='grid'><div class='stat'><b>"+state.memories.length+"</b><small>Объекты памяти</small></div><div class='stat'><b>"+state.experiences.filter(function(x){return x.type==="POSITIVE"}).length+"</b><small>Положительный опыт</small></div><div class='stat'><b>"+state.experiences.filter(function(x){return x.type==="NEGATIVE"}).length+"</b><small>Отрицательный опыт</small></div><div class='stat'><b>"+state.rejectedPaths.length+"</b><small>Отклонённые пути</small></div></div><div class='card'><h3>Адресный индекс</h3><div class='muted'>MEM / EXP / REL / EVT / ACT / RSL / VRF / SRC / TSK</div></div><div class='card'><h3>Опыт</h3><div class='list'>"+(state.experiences.slice().reverse().map(function(e){return"<div class='item'><b>"+esc(e.title)+"</b> <span class='tag'>"+esc(e.type)+"</span><div class='muted'>"+esc(e.whatHappened)+"</div><small>confidence: "+e.confidence.toFixed(2)+" · "+esc(e.id)+"</small><br><button class='btn ghost' data-exp='"+esc(e.id)+"'>Подробнее</button></div>"}).join("")||"<div class='muted'>Пока нет опыта.</div>")+"</div></div>";
else if(currentView==="chat")v.innerHTML="<h2>Чат с агентом</h2><div class='notice'>Перед запросом: проект → состояние → память → релевантный опыт → незавершённые элементы.</div><div class='chat'>"+(state.messages.map(function(m){return"<div class='bubble "+(m.role==="user"?"user":"agent")+"'><small>"+(m.role==="user"?"Вы":"Агент")+"</small><div>"+esc(m.text).replace(/\n/g,"<br>")+"</div></div>"}).join("")||"<div class='muted'>Начните разговор.</div>")+"</div><div class='composer'><textarea id='chatInput' placeholder='Напишите запрос…'></textarea><div class='row'><button class='btn primary' data-action='send'>Отправить агенту</button><button class='btn' data-action='showContext'>Показать контекст</button></div></div>";
else v.innerHTML="<h2>Подключение ChatGPT</h2><div class='card'><h3>Цикл Организма</h3><div class='notice'>Я → Организм → GPT → Организм → Я. API key вводить не нужно.</div><button class='btn primary' data-action='connectChatGPT'>Подключить ChatGPT</button> <button class='btn' data-action='checkChatGPT'>Проверить</button></div><div class='card'><h3>История и опыт</h3><div class='muted'>Каждый вопрос и каждый ответ GPT проходят через Организм, сохраняются в текущей истории и становятся кандидатами для нового опыта и знаний.</div></div><div class='card'><h3>Импорт старых данных</h3><button class='btn primary' data-action='import'>Выбрать TXT</button> <button class='btn' data-action='export'>Экспорт JSON</button></div>"
bind();updateAgentStatus();
}

async function bridgeStatus(){for(var base of ["","http://127.0.0.1:1455"]){try{var r=await fetch(base+"/api/status",{cache:"no-store"});if(r.ok)return await r.json()}catch(e){}}return null}
async function corePost(path,payload){
 var r=await fetch(path,{method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify(payload)});
 var j=await r.json();if(!r.ok||!j.ok)throw new Error(j.error||("HTTP "+r.status));return j;
}
async function persistCoreText(name,text,sourceKind,speaker,origin){
 return corePost("/api/core/import-text",{source_name:name,text:text,source_kind:sourceKind,speaker:speaker,origin:origin,completeness:"unknown"});
}
async function buildCoreContext(msg){
 try{
  var r=await corePost("/api/core/handoff",{project_id:project().id,task:msg,environment:{platform:"browser-pwa"},model_name:state.settings.agent.model||"ChatGPT bridge",model_version:"unknown"});
  return {context:{coreHandoff:r.result.payload,project:project(),coreVersion:"0.4"},applicationId:r.result.application_id,coreAvailable:true};
 }catch(e){
  return {context:{project:project(),task:msg,coreUnavailable:true,safetyNote:"CORE Handoff unavailable. Do not treat local candidate memories as verified evidence.",verifiedClaims:[],validatedExperiences:[]},applicationId:null,coreAvailable:false};
 }
}
async function callAgent(message,ctx){
 var history=state.messages.slice(0,-1);
 var r=await fetch("/api/chat",{method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify({message:message,context:ctx,history:history})});
 var j=await r.json();if(!r.ok||!j.ok)throw new Error(j.error||("HTTP "+r.status));return j.text;
}
async function sendChat(){
 if(busy)return;var input=document.getElementById("chatInput"),msg=input.value.trim();if(!msg)return;
 busy=true;state.messages.push({id:uid("MSG"),role:"user",text:msg,at:Date.now()});save();render();
 try{
  var rawUserStored=false;
  try{await persistCoreText("live-chat-user-"+Date.now()+".txt",msg,"live_chat_message","user","external");rawUserStored=true}catch(rawError){console.warn("CORE RAW user write failed:",rawError.message)}
  var prepared=await buildCoreContext(msg),ctx=prepared.context;
  var reply=await callAgent(msg,ctx);
  try{await persistCoreText("live-chat-assistant-"+Date.now()+".txt",reply,"live_chat_message","assistant","organism_generated")}catch(rawError){console.warn("CORE RAW assistant write failed:",rawError.message)}
  state.messages.push({id:uid("MSG"),role:"agent",text:reply,at:Date.now(),context:{applicationId:prepared.applicationId,coreAvailable:prepared.coreAvailable}});
  var ev={id:uid("EVT"),projectId:project().id,description:"Ответ модели получен; проверка результата не выполнена: "+msg,status:"RECORDED"};
  state.events.push(ev);
  var ac={id:uid("ACT"),eventId:ev.id,description:msg,status:"PROPOSED_NOT_EXECUTED"};
  state.actions.push(ac);
  var rs={id:uid("RSL"),actionId:ac.id,resultType:"MODEL_RESPONSE_NOT_VERIFIED",status:"PENDING_CHECK",description:reply,applicationId:prepared.applicationId};
  state.results.push(rs);
  state.memories.push({id:uid("MEM"),title:"Диалог (не проверенное знание): "+msg.slice(0,80),content:"Вопрос:\n"+msg+"\n\nОтвет модели:\n"+reply.slice(0,3000),status:"RAW_DIALOGUE_NOT_KNOWLEDGE",createdAt:Date.now(),dimensions:{project:project().id,type:"dialogue",verification:"unverified"}});
  state.relations.push({id:uid("REL"),fromId:state.messages[state.messages.length-2].id,toId:state.messages[state.messages.length-1].id,type:"DIALOGUE_TURN",dimension:"provenance"});
  save();input.value="";toast(rawUserStored?"Ответ сохранён как диалог; проверкой или опытом не считается.":"Ответ получен; не удалось сохранить вопрос в CORE RAW.");
 }catch(e){state.messages.push({id:uid("MSG"),role:"agent",text:"Ошибка подключения: "+e.message,at:Date.now()});save()}
 finally{busy=false;render()}
}
function createExperienceFromImport(text,filename){
 var source={id:uid("SRC"),type:"TXT",title:filename,content:text,createdAt:Date.now()};state.sources.push(source);
 var lines=text.split(/\r?\n/).map(function(x){return x.trim()}).filter(Boolean),chunks=[],i,count=0;
 for(i=0;i<lines.length;i+=8)chunks.push(lines.slice(i,i+8).join("\n"));
 chunks.slice(0,40).forEach(function(chunk){
  var low=chunk.toLowerCase(),marker=/ошиб|слом|потер|не сработ|провал|не удалось|пропал|регресс|успеш|сработал|исправ|готов|запуст|получил|подтверд|работает|решил/.test(low);
  if(!marker)return;
  var id=uid("EXP");count++;
  state.experiences.push({id:id,projectId:project().id,title:"Фрагмент-кандидат из "+filename+" #"+count,
   whatHappened:chunk.slice(0,700),whatTried:"",whatWorked:"",whatFailed:"",
   understandingBefore:"",understandingAfter:"Не проверено; извлечён только текстовый фрагмент.",
   confidence:0,type:"CANDIDATE",appliesWhen:"Требуется семантический разбор и проверка",
   doesNotApplyWhen:"Не использовать как подтверждённый опыт",sourceId:source.id});
  state.relations.push({id:uid("REL"),fromId:id,toId:source.id,type:"DERIVED_FROM",dimension:"provenance"});
  state.memories.push({id:uid("MEM"),title:"Кандидат извлечения: "+filename+" #"+count,content:chunk.slice(0,900),
   status:"CANDIDATE",sourceId:source.id,dimensions:{project:project().id,type:"extraction_candidate",time:Date.now(),confidence:0}});
 });
 save();return count;
}
function bind(){
document.querySelectorAll("[data-view]").forEach(function(b){b.onclick=function(){setView(b.dataset.view)}});
document.querySelectorAll("[data-view2]").forEach(function(b){b.onclick=function(){setView(b.dataset.view2)}});
document.querySelectorAll("[data-action]").forEach(function(b){b.onclick=function(){var a=actions[b.dataset.action];if(a)a()}});
document.querySelectorAll("[data-exp]").forEach(function(b){b.onclick=function(){showExperience(b.dataset.exp)}});
}
var actions={
import:function(){document.getElementById("fileInput").click()},
send:sendChat,
showContext:function(){showJSON(contextSnapshot(document.getElementById("chatInput")?document.getElementById("chatInput").value:"текущий проект"))},
newProject:function(){modal("<h2>Новый проект</h2><input id='npName' placeholder='Название'><br><br><input id='npDesc' placeholder='Описание'><br><br><button class='btn primary' id='saveProject'>Создать</button>","saveProject",function(){var p={id:uid("PRJ"),name:document.getElementById("npName").value||"Новый проект",description:document.getElementById("npDesc").value||"",status:"ACTIVE"};state.projects.push(p);state.projectStates.push({id:uid("PST"),projectId:p.id,name:"Начальное состояние",status:"ACTIVE"});save();closeModal();render()})},
connectChatGPT:function(){window.location.href="http://127.0.0.1:1455/auth/start"},
checkChatGPT:async function(){var s=await bridgeStatus();if(!s){toast("Локальный мост не найден. Запустите organism_server.py.");return}state.settings.agent.connected=!!s.connected;save();render();toast(s.connected?"ChatGPT подключён.":"ChatGPT не подключён.")},
export:function(){var blob=new Blob([JSON.stringify(state,null,2)],{type:"application/json"}),a=document.createElement("a");a.href=URL.createObjectURL(blob);a.download="organism_pwa_test_backup.json";a.click();URL.revokeObjectURL(a.href)},
reset:function(){if(confirm("Удалить локальную тестовую базу?")){localStorage.removeItem(KEY);location.reload()}}
};
document.getElementById("fileInput").addEventListener("change",async function(e){
 var f=e.target.files[0];if(!f)return;var text=await f.text(),n=createExperienceFromImport(text,f.name),rawSaved=false;
 try{await persistCoreText(f.name,text,"chat_export","unknown","external");rawSaved=true}catch(err){console.warn("CORE RAW import failed:",err.message)}
 toast(rawSaved?"RAW сохранён в CORE; фрагментов-кандидатов: "+n+" (это ещё не опыт).":"Локальный импорт сохранён; CORE RAW недоступен. Кандидатов: "+n);
 setView("knowledge");e.target.value="";
});
document.getElementById("modal").addEventListener("click",function(e){if(e.target.id==="modal")closeModal()});
render();
