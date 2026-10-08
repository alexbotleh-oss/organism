#!/usr/bin/env python3
from __future__ import annotations
import base64, hashlib, json, os, secrets, threading, time, urllib.parse, urllib.request, uuid
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
import jwt
from jwt import PyJWKClient

HOST="127.0.0.1"; PORT=1455
ROOT=Path(__file__).resolve().parent; WEB=ROOT/"web"
CONFIG_DIR=Path.home()/".config"/"organism"
CRED_FILE=CONFIG_DIR/"chatgpt_credentials.json"; HOST_ID_FILE=CONFIG_DIR/"host_id"
AUTHORIZE_URL="https://auth.openai.com/api/accounts/authorize"
TOKEN_URL="https://auth.openai.com/api/accounts/oauth/token"
RESOURCE="https://api.openai.com/v1"
SCOPE="openid profile email offline_access resource.invoke chatgpt.tokens.use.direct"
ISSUER="https://auth.openai.com"
JWKS_URL="https://auth.openai.com/.well-known/jwks.json"
JWKS=PyJWKClient(JWKS_URL)
pending={}; lock=threading.Lock()

def b64url(b): return base64.urlsafe_b64encode(b).rstrip(b"=").decode()
def get_host_id():
    CONFIG_DIR.mkdir(parents=True,exist_ok=True)
    if HOST_ID_FILE.exists(): return HOST_ID_FILE.read_text(encoding="utf-8").strip()
    value="urn:uuid:"+str(uuid.uuid4()); HOST_ID_FILE.write_text(value,encoding="utf-8")
    try: os.chmod(HOST_ID_FILE,0o600)
    except OSError: pass
    return value
def load_creds():
    if not CRED_FILE.exists(): return None
    try: return json.loads(CRED_FILE.read_text(encoding="utf-8"))
    except Exception: return None
def save_creds(data):
    CONFIG_DIR.mkdir(parents=True,exist_ok=True); tmp=CRED_FILE.with_suffix(".tmp")
    tmp.write_text(json.dumps(data,ensure_ascii=False,indent=2),encoding="utf-8")
    try: os.chmod(tmp,0o600)
    except OSError: pass
    tmp.replace(CRED_FILE)
    try: os.chmod(CRED_FILE,0o600)
    except OSError: pass
def token_request(form):
    req=urllib.request.Request(TOKEN_URL,data=urllib.parse.urlencode(form).encode(),
        headers={"Content-Type":"application/x-www-form-urlencoded"},method="POST")
    with urllib.request.urlopen(req,timeout=30) as r: return json.loads(r.read().decode())
def refresh_if_needed(creds):
    if float(creds.get("expires_at",0))>time.time()+180: return creds
    if not creds.get("refresh_token"): raise RuntimeError("Сессия ChatGPT истекла. Подключите ChatGPT заново.")
    data=token_request({"grant_type":"refresh_token","client_id":creds["client_id"],
                         "refresh_token":creds["refresh_token"],"resource":RESOURCE})
    if not data.get("access_token"): raise RuntimeError("OpenAI не вернул новый access token.")
    creds.update({"access_token":data["access_token"],"refresh_token":data.get("refresh_token",creds["refresh_token"]),
                  "token_type":data.get("token_type","Bearer"),"expires_in":data.get("expires_in",3600),
                  "expires_at":time.time()+int(data.get("expires_in",3600)),"scope":data.get("scope",creds.get("scope",""))})
    save_creds(creds); return creds
def start_auth():
    verifier=b64url(secrets.token_bytes(32)); challenge=b64url(hashlib.sha256(verifier.encode()).digest())
    state=b64url(secrets.token_bytes(24)); nonce=b64url(secrets.token_bytes(24))
    redirect=f"http://{HOST}:{PORT}/auth/callback"; old=load_creds()
    params={"client_id":old["client_id"] if old else "dynamic_agent_client","response_type":"code",
            "redirect_uri":redirect,"scope":SCOPE,"resource":RESOURCE,"state":state,"nonce":nonce,
            "code_challenge_method":"S256","code_challenge":challenge,"ext_agent_host_id":get_host_id()}
    if not old: params["agent_name_hint"]="ОРГАНИЗМ"
    else:
        if old.get("id_token"): params["id_token_hint"]=old["id_token"]
        if old.get("email"): params["login_hint"]=old["email"]
    with lock: pending[state]={"verifier":verifier,"nonce":nonce,"redirect":redirect}
    return AUTHORIZE_URL+"?"+urllib.parse.urlencode(params)
def validate_id_token(id_token,client_id,nonce):
    if not id_token: raise RuntimeError("OpenAI не вернул ID token.")
    key=JWKS.get_signing_key_from_jwt(id_token).key
    claims=jwt.decode(id_token,key,algorithms=["RS256","RS384","RS512","ES256","ES384","ES512","EdDSA"],issuer=ISSUER,audience=client_id,leeway=5,options={"require":["sub","exp","iat"]})
    if claims.get("nonce")!=nonce: raise RuntimeError("ID token nonce не совпадает с OAuth-сеансом.")
    if not claims.get("sub"): raise RuntimeError("ID token не содержит subject.")
    return claims

def exchange(code,state,issued_client_id):
    with lock: item=pending.pop(state,None)
    if not item: raise RuntimeError("OAuth state недействителен или уже использован.")
    data=token_request({"grant_type":"authorization_code","client_id":issued_client_id,"code":code,
                         "code_verifier":item["verifier"],"redirect_uri":item["redirect"],"resource":RESOURCE})
    scopes=set((data.get("scope") or "").split())
    if "chatgpt.tokens.use.direct" not in scopes:
        raise RuntimeError("Вы вошли, но не разрешили использование ChatGPT plan.")
    if not data.get("access_token"): raise RuntimeError("OpenAI не вернул access token.")
    claims=validate_id_token(data.get("id_token"),issued_client_id,item["nonce"])
    creds={"client_id":issued_client_id,"subject":claims["sub"],"issuer":ISSUER,"email":claims.get("email"),"name":claims.get("name"),"access_token":data["access_token"],"refresh_token":data.get("refresh_token"),
           "id_token":data.get("id_token"),"token_type":data.get("token_type","Bearer"),"scope":data.get("scope",""),
           "expires_in":data.get("expires_in",3600),"expires_at":time.time()+int(data.get("expires_in",3600)),
           "saved_at":time.time()}
    save_creds(creds); return creds
def responses_text(message,context,history):
    creds=refresh_if_needed(load_creds() or {})
    system=("Ты — GPT внутри Организма. Организм является прослойкой памяти и контекста. "
            "Используй контекст как рабочую память, не выдумывай отсутствующие факты. "
            "Учитывай текущую историю разговора. После ответа Организм сохранит сообщение и ответ "
            "для анализа опыта и знаний.\n\nКОНТЕКСТ ОРГАНИЗМА:\n"+json.dumps(context,ensure_ascii=False))
    inp=[{"role":"system","content":system}]
    for m in history[-20:]: inp.append({"role":m["role"],"content":m["text"]})
    inp.append({"role":"user","content":message})
    data={"model":context.get("model") or "gpt-6.1-sol","input":inp,"store":False,"stream":True}
    req=urllib.request.Request(RESOURCE+"/responses",data=json.dumps(data).encode(),
        headers={"Authorization":"Bearer "+creds["access_token"],"Content-Type":"application/json"},method="POST")
    chunks=[]; completed=False
    with urllib.request.urlopen(req,timeout=180) as r:
        for raw in r:
            line=raw.decode(errors="replace").strip()
            if not line.startswith("data:"): continue
            val=line[5:].strip()
            if not val or val=="[DONE]": continue
            try: event=json.loads(val)
            except json.JSONDecodeError: continue
            typ=event.get("type","")
            if typ=="response.output_text.delta": chunks.append(event.get("delta",""))
            elif typ=="response.completed": completed=True
            elif typ=="response.failed":
                err=event.get("response",{}).get("error") or {}
                raise RuntimeError(err.get("message") or err.get("code") or "Responses API: response.failed")
    if not completed: raise RuntimeError("Поток GPT завершился без response.completed.")
    return "".join(chunks)
def esc(s): return str(s).replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace('"',"&quot;").replace("'","&#39;")

class Handler(BaseHTTPRequestHandler):
    def log_message(self,fmt,*args): print("[ORGANISM]",fmt%args)
    def send_json(self,obj,code=200):
        data=json.dumps(obj,ensure_ascii=False).encode(); self.send_response(code)
        self.send_header("Content-Type","application/json; charset=utf-8"); self.send_header("Cache-Control","no-store")
        self.end_headers(); self.wfile.write(data)
    def do_GET(self):
        parsed=urllib.parse.urlparse(self.path)
        if parsed.path=="/auth/start":
            self.send_response(302); self.send_header("Location",start_auth()); self.end_headers(); return
        if parsed.path=="/auth/callback":
            q=urllib.parse.parse_qs(parsed.query)
            try:
                if q.get("error"): raise RuntimeError(q["error"][0]+": "+q.get("error_description",[""])[0])
                state=q.get("state",[""])[0]; code=q.get("code",[""])[0]; cid=q.get("client_id",[""])[0]
                if not code or not state or not cid: raise RuntimeError("OAuth callback не содержит code/state/client_id.")
                exchange(code,state,cid); msg="ОРГАНИЗМ подключён к ChatGPT plan. Вернитесь в приложение."
            except Exception as e: msg="Ошибка подключения: "+str(e)
            data=f'<!doctype html><meta charset="utf-8"><title>ОРГАНИЗМ</title><h2>ОРГАНИЗМ</h2><p>{esc(msg)}</p>'.encode()
            self.send_response(200); self.send_header("Content-Type","text/html; charset=utf-8"); self.end_headers(); self.wfile.write(data); return
        if parsed.path=="/api/status":
            c=load_creds(); self.send_json({"connected":bool(c and c.get("access_token")),"email":(c or {}).get("email","")}); return
        if parsed.path=="/": self.path="/index.html"
        path=(WEB/urllib.parse.unquote(self.path.lstrip("/"))).resolve()
        if WEB not in path.parents or not path.is_file(): self.send_error(404); return
        types={".html":"text/html",".js":"text/javascript",".css":"text/css",".json":"application/json"}
        self.send_response(200); self.send_header("Content-Type",types.get(path.suffix,"application/octet-stream")); self.end_headers(); self.wfile.write(path.read_bytes())
    def do_POST(self):
        if self.path!="/api/chat": self.send_error(404); return
        try:
            n=int(self.headers.get("Content-Length","0")); body=json.loads(self.rfile.read(n).decode())
            if not load_creds(): raise RuntimeError("ChatGPT не подключён. Сначала подключите ChatGPT.")
            reply=responses_text(body.get("message",""),body.get("context",{}),body.get("history",[]))
            self.send_json({"ok":True,"text":reply})
        except Exception as e: self.send_json({"ok":False,"error":str(e)},400)

if __name__=="__main__":
    if not WEB.exists(): raise SystemExit("Не найдена папка web/")
    print(f"ОРГАНИЗМ: http://{HOST}:{PORT}/")
    ThreadingHTTPServer((HOST,PORT),Handler).serve_forever()
