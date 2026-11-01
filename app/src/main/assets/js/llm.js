/* ===== Motor local (llama.cpp) =====
   En la APK, window.IgnisLLM descarga, importa y carga modelos .gguf y genera las respuestas del chat.
   En el navegador no existe y todo sigue en modo demostración. Este archivo solo declara funciones:
   se carga antes que app.js y usa sus datos (M, st, BR…) cuando se le llama. */
const LLM=()=>{try{return !!(window.IgnisLLM&&IgnisLLM.available())}catch(e){return false}};
const HF='https://huggingface.co/';
let DL=null;               // descarga en curso {id,p,got,size}
let IMP=null;              // importación en curso {p,got,size}
let LD={busy:0,file:'',want:''}; // modelo en memoria
let GEN=null;              // respuesta en curso
let PEND=null;             // pedido que espera a que el modelo cargue
let FILES=[];              // modelos en el teléfono [{file,size}|{file,part}]
const fsz=b=>b>=1e9?(b/1e9).toFixed(1)+' GB':Math.max(1,Math.round(b/1e6))+' MB';
/* velocidad aproximada en un teléfono de gama media-alta (tokens por segundo) */
const MSX={q06:40,g1:26,l1:26,q17:20,qc15:20,l3:11,qc3:11,g4:8,q4i:9,q8:4};

/* Lee los modelos del teléfono y marca su estado: 0 no está, 1 descargado, 2 elegido */
function llmSync(){if(!LLM())return;
 try{FILES=JSON.parse(IgnisLLM.models()).filter(f=>/^[A-Za-z0-9._-]+$/.test(f.file))}catch(e){FILES=[]}
 const has=m=>FILES.some(f=>f.size&&f.file==m[8]);
 for(const f of FILES)if(f.size&&!M.some(m=>m[8]==f.file))
  M.push(['f:'+f.file,f.file.replace(/\.gguf$/i,'').replace(/[-_]+/g,' ').slice(0,30),'GGUF · '+(f.size/1e9).toFixed(1)+' GB',['Importado'],'cube','#3a2b8a,#7c5cff',0,null,f.file]);
 for(let i=M.length-1;i>=0;i--)if(M[i][0].startsWith('f:')&&!has(M[i]))M.splice(i,1);
 const m={};for(const x of M)m[x[0]]=has(x)?(x[0]==BR.cfg.act?2:1):0;
 if(!Object.values(m).includes(2)){const f=M.find(has);if(f){m[f[0]]=2;BR.cfg.act=f[0]}}
 st.m=m;try{LD.file=IgnisLLM.loaded()}catch(e){}}

const llmActive=()=>M.find(x=>st.m[x[0]]==2);
function msx(m){const r=BR.tps&&BR.tps[m[0]],e=MSX[m[0]];
 return `${r?r+' t/s medidos':e?'≈'+e+' t/s':'— t/s'} · ${cp(m)=='justo'?'2K':'4K'} ctx · ${nec(m)} GB`}

/* Botones de cada modelo */
function mBtn(m){const k=m[0],s=st.m[k];let ic=s?'play':'dl',lb=s?'Usar modelo':'Descargar';
 if(LLM()){if(DL&&DL.id==k){ic='x';lb='Pausar descarga'}else if(s==2&&LD.file==m[8]){ic='check';lb='Modelo en memoria'}}
 const del=LLM()&&s?`<button class="bx" style="border:0;background:none;${st.mdel==k?'color:#ff6a85':''}" aria-label="Borrar del teléfono" onclick="mDel('${k}')">${I(st.mdel==k?'x':'dots',12)}</button>`:I('dots',12);
 return `<button class="bx" aria-label="${lb}" onclick="mA('${k}')">${I(ic,11)}</button>${del}`}
function dlBar(m){if(!LLM())return'';const k=m[0];
 if(DL&&DL.id==k)return DL.check?`<small class="st2">Comprobando el archivo (SHA-256)…</small>`:`<div class="bar" style="margin-top:4px"><i style="width:${DL.p||0}%"></i></div><small class="st2">Descargando ${DL.p||0}% · ${fsz(DL.got||0)}${DL.size>0?' de '+fsz(DL.size):''} · ✕ pausa</small>`;
 const p=FILES.find(f=>f.part&&f.file==m[8]);if(p&&!st.m[k])return `<small class="st2" style="color:#ffb84a">En pausa · ${fsz(p.part)} descargados · toca ↓ para seguir</small>`;
 if(st.m[k]==2)return `<small class="st2" style="color:#3ddc97">${LD.busy&&LD.want==m[8]?'Cargando en memoria…':LD.file==m[8]?'En memoria · listo para el chat':'Se carga al escribir en el chat'}</small>`;
 return''}
/* Importar y espacio usado (pestaña Modelos) */
function llmTools(){
 if(!window.IgnisLLM)return window.IgnisDevice?'':'<p class="hint" style="margin:0 0 8px">Modo demostración: en la app Android los modelos se descargan y responden de verdad.</p>';
 if(!LLM()){let s={};try{s=JSON.parse(IgnisLLM.status())}catch(e){}
  return `<div class="cd" style="padding:8px 9px;margin:0 0 9px;font-size:8.5px;color:#ff6a85"><b style="display:block;font-size:10px">Motor local no disponible</b>${esc(s.err||'')}${s.abi?' · procesador: '+esc(s.abi):''}</div>`}
 let s={};try{s=JSON.parse(IgnisLLM.status())}catch(e){}const used=FILES.reduce((a,f)=>a+(f.size||f.part||0),0);
 return `<div class="cd" style="padding:8px 9px;margin:0 0 9px;display:flex;gap:8px;align-items:center"><div style="flex:1;font-size:8.5px;color:var(--mut)"><span style="color:#3ddc97">● Motor llama.cpp activo${s.v?' · v'+esc(s.v):''}</span><br>${IMP?`Importando ${IMP.p||0}%<div class="bar"><i style="width:${IMP.p||0}%"></i></div>`:`Modelos en el teléfono<b style="display:block;color:var(--tx);font-size:10px">${FILES.filter(f=>f.size).length} · ${fsz(used)}</b>`}</div><button class="btn sm o" onclick="llmImport()" ${IMP?'disabled':''}>${I('dl',11)} Importar .gguf</button></div>`}
function llmImport(){if(IMP)return;IMP={p:0};R('models');IgnisLLM.importModel()}

/* Tocar un modelo: descargar · usar · cargar */
function llmModel(k){const m=M.find(x=>x[0]==k);if(!m)return;const s=st.m[k];
 if(DL&&DL.id==k){IgnisLLM.cancelDownload();return}
 if(!s){
  if(cp(m)=='no'){setMood('error',2600);T('No compatible: necesita ~'+nec(m)+' GB de RAM');return}
  if(DL){T('Espera a que termine la otra descarga');return}
  if(!m[7]){T('Ese modelo ya no está en el teléfono');return}
  const part=(FILES.find(f=>f.part&&f.file==m[8])||{}).part||0,free=IgnisLLM.freeSpace();
  if(free>0&&free+part<sz(m)*1e9+3e8){setMood('error',2600);T('Necesitas ~'+(sz(m)+.3).toFixed(1)+' GB libres');return}
  DL={id:k,p:0,got:part,size:0};setMood('work');hap();T((part?'Continuando ':'Descargando ')+m[1]+'…');
  IgnisLLM.download(k,HF+m[7]+'/resolve/main/'+m[8]+'?download=true',m[8],m[9]||'',m[10]||0);R('models');return}
 if(s==1){for(const x in st.m)if(st.m[x]==2)st.m[x]=1;st.m[k]=2;BR.cfg.act=k;brSave();llmLoad(m);cambioModelo(k);R('models','chat','home');return}
 if(LD.file!=m[8])llmLoad(m);else T('Modelo activo y en memoria')}
function llmLoad(m){if(LD.busy)return;LD.busy=1;LD.want=m[8];setMood('work');IgnisLLM.load(m[8],cp(m)=='justo'?2048:4096);if(cur()=='models')R('models')}
function mDel(k){const m=M.find(x=>x[0]==k);if(!m)return;
 if(st.mdel!=k){st.mdel=k;T('Toca otra vez para borrar '+m[1]+' del teléfono');setTimeout(()=>{if(st.mdel==k){st.mdel=0;R('models')}},3000);R('models');return}
 st.mdel=0;if(LD.file==m[8])LD.file='';IgnisLLM.delete(m[8]);if(BR.cfg.act==k)BR.cfg.act=null;llmSync();brSave();T('Borrado: '+m[1]);R('models','chat','home')}

/* Mensajes del lado nativo */
window.onLlm=o=>{switch(o.st){
 case 'dl':DL=Object.assign(DL||{id:o.id},o);if(cur()=='models')R('models');break;
 case 'dlcheck':DL=Object.assign(DL||{id:o.id},{p:100,check:1});if(cur()=='models')R('models');break;
 case 'dlok':DL=null;llmSync();setMood('done',2400);hap('ok');T('Modelo descargado · toca ▶ para usarlo');R('models','home','chat');break;
 case 'dlerr':DL=null;llmSync();setMood('error',2600);T(o.msg);R('models');break;
 case 'dlcancel':DL=null;llmSync();T('Descarga en pausa');R('models');break;
 case 'imp':IMP=o;if(cur()=='models')R('models');break;
 case 'impok':IMP=null;llmSync();hap('ok');T('Modelo importado · toca ▶ para usarlo');R('models');break;
 case 'imperr':IMP=null;setMood('error',2600);T(o.msg);R('models');break;
 case 'impcancel':IMP=null;R('models');break;
 case 'loading':LD.busy=1;LD.want=o.file;break;
 case 'loaded':LD={busy:0,file:o.file,want:''};setMood('done',1800);T('Modelo listo en '+(o.ms/1000).toFixed(1)+' s');R('models','home');if(PEND){const p=PEND;PEND=null;llmGen(p)}break;
 case 'loaderr':LD={busy:0,file:'',want:''};setMood('error',2600);T(o.msg);if(PEND){PEND=null;genFail(o.msg)}R('models');break;
 case 'tok':if(GEN){GEN.raw+=o.t;genPaint()}break;
 case 'end':genDone(o.text,o.n,o.ms);break;
 case 'generr':genDone(o.text||(GEN&&GEN.raw)||'',0,0,o.msg);break}};

/* ===== Chat con el modelo ===== */
const SYSP='Respondes siempre en español, de forma breve y clara. Cuando te piden un diseño (web, app o interfaz), primero escribe una o dos frases con la idea y después entrega UNA página HTML completa dentro de un bloque ```html ... ```, con todo el CSS en una etiqueta <style>: moderna, cuidada, responsive y pensada primero para móvil, con textos de ejemplo realistas. No uses JavaScript ni librerías externas (solo Google Fonts). Si te piden otra cosa, responde normalmente sin código.';
const scrollChat=()=>{const s=$('#p-chat .scr');if(s)s.scrollTop=1e6};
function llmBot(h){const b=`<div class="bub bb">${h}</div>`;st.msgs.push(b);brSave();$('#ms').insertAdjacentHTML('beforeend',b);scrollChat()}
function llmSend(v){
 if(GEN||PEND){T('Ignis todavía está respondiendo');return}
 const rf=refInfo();recordar('pedido',v+(rf?' · ref: '+rf.n:''));
 const u=`<div class="bub bu">${esc(v)}</div>`;st.msgs.push(u);$('#ms').insertAdjacentHTML('beforeend',u);$('#ci').value='';
 const m=llmActive();
 if(!m){setMood('error',2200);llmBot('Para responderte necesito un modelo en el teléfono. Descarga uno en <b>Modelos</b>: los marcados como <b>Compatible</b> van bien en tu teléfono.<div style="margin-top:6px"><button class="btn sm" onclick="st.mt=2;to(\'models\')">Ver modelos</button></div>');return}
 $('#ms').insertAdjacentHTML('beforeend','<div class="bub bb" id="lv"><span class="dt"></span><span class="dt"></span><span class="dt"></span></div>');scrollChat();
 setMood('think');const req={q:v,m};
 if(LD.file==m[8])llmGen(req);else{PEND=req;$('#lv').insertAdjacentHTML('beforeend',' <small>Cargando '+esc(m[1])+'…</small>');llmLoad(m)}}
function llmGen(r){
 // historial corto: las respuestas se guardan sin el código, así cabe en la memoria del modelo
 const hist=(BR.chat||[]).slice(-4).map(x=>({r:x.r,c:String(x.c).slice(0,1200)}));
 // Qwen3 con razonamiento: se le pide responder directo (la versión Instruct 2507 ya no razona)
 const q=r.q+(/qwen3/i.test(r.m[8])&&!/2507/.test(r.m[8])?' /no_think':'');
 const msgs=[{r:'system',c:contexto()+'\n'+SYSP}].concat(hist,[{r:'user',c:q}]);
 GEN={raw:'',q:r.q,m:r.m};setMood('work');genPaint();
 try{IgnisLLM.generate(JSON.stringify({msgs,max:cp(r.m)=='justo'?1400:3000,temp:.7}))}catch(e){genDone('',0,0,'No se pudo iniciar el modelo')}}
/* texto visible: sin el razonamiento <think> */
function vis(raw){let t=String(raw||'').replace(/<think>[\s\S]*?<\/think>/g,'');const i=t.indexOf('<think>');const th=i>=0;if(th)t=t.slice(0,i);return{t:t.trim(),th}}
/* texto con **negrita**, saltos de línea y bloques de código */
function mdx(t,final){return String(t).split('```').map((p,i)=>{
 if(!(i%2))return esc(p).replace(/\*\*(.+?)\*\*/g,'<b>$1</b>').replace(/\n/g,'<br>');
 const nl=p.indexOf('\n'),lang=(nl>=0?p.slice(0,nl):'').trim()||'código',code=nl>=0?p.slice(nl+1):p,n=code.split('\n').length;
 return final?`<span class="cbk">${I('code',11)} ${esc(lang)} · ${n} líneas</span>`:`<pre class="cbl"><small>${esc(lang)} · ${n} líneas…</small>${esc(code.slice(-700))}</pre>`}).join('')}
function genPaint(){const el=$('#lv');if(!el||!GEN)return;const v=vis(GEN.raw);
 const near=(()=>{const s=$('#p-chat .scr');return !s||s.scrollHeight-s.scrollTop-s.clientHeight<120})();
 el.innerHTML=(v.t?mdx(v.t,false):'<span class="dt"></span><span class="dt"></span><span class="dt"></span>')+(v.th?'<br><small style="color:var(--mut)">Pensando…</small>':'')+'<div style="margin-top:6px"><button class="btn sm o" onclick="IgnisLLM.stop()">Detener</button></div>';
 if(near)scrollChat()}
function genFail(msg){const el=$('#lv');if(el)el.remove();setMood('error',2600);llmBot(`<span style="color:#ff6a85">${esc(msg)}</span>`)}
function extractHtml(t){let m=/```html?\s*\n([\s\S]*?)(```|$)/i.exec(t);if(m&&/<[a-z!]/i.test(m[1]))return m[1].trim();
 m=/```[a-z]*\s*\n([\s\S]*?)(```|$)/i.exec(t);if(m&&/<(!doctype|html|body|section|div)/i.test(m[1]))return m[1].trim();
 const i=t.search(/<!doctype html|<html/i);return i>=0?t.slice(i).trim():''}
const titleOf=h=>{const m=/<title>([^<]{1,60})<\/title>/i.exec(h)||/<h1[^>]*>([^<]{1,60})</i.exec(h);return m?m[1].trim():''};
function genDone(text,n,ms,err){const g=GEN;GEN=null;const el=$('#lv');if(el)el.remove();if(!g)return;
 const v=vis(text||g.raw).t,html=extractHtml(v);
 if(n>4&&ms>0){BR.tps=BR.tps||{};BR.tps[g.m[0]]=+(n/(ms/1000)).toFixed(1)}
 if(html)BR.res={html,n:titleOf(html)||g.q.slice(0,40),q:g.q,m:g.m[1],d:fecha()};
 let h=v?mdx(v,true):'';if(err)h+=(h?'<br>':'')+`<span style="color:#ff6a85">${esc(err)}</span>`;
 if(!h)h='No obtuve respuesta. Prueba a pedirlo de otra forma.';
 if(html)h+=`<div style="display:flex;gap:5px;margin-top:6px"><button class="btn sm" onclick="st.rt=0;to('result')">Ver resultado</button><button class="btn sm o" onclick="saveToggle('res')">Guardar</button></div>`;
 const b=`<div class="bub bb">${h}</div>`;st.msgs.push(b);$('#ms').insertAdjacentHTML('beforeend',b);
 if(v)BR.chat=(BR.chat||[]).concat([{r:'user',c:g.q},{r:'assistant',c:v.replace(/```[\s\S]*?(```|$)/g,'[diseño HTML entregado]').slice(0,1200)}]).slice(-12);
 brSave();R('models');if(html)R('result');setMood(err&&!v?'error':'done',2600);hap(err?'tap':'ok');if(cur()!='chat')badge('chat',1);scrollChat()}

/* ===== Resultado generado ===== */
const resView=()=>`<div class="rsv"><iframe sandbox="" title="Vista previa" srcdoc="${esc(BR.res.html)}"></iframe></div><p class="hint" style="margin:5px 0 0">${esc(BR.res.n)} · ${esc(BR.res.m)} · ${esc(BR.res.d)}</p>`;
const resCode=()=>`<pre class="rsc">${esc(BR.res.html)}</pre>`;
const resFiles=()=>`<div class="cd li"><span class="ic" style="background:#2b2f78">${I('code',13)}</span><div class="x"><b>index.html</b><small>${fsz(new Blob([BR.res.html]).size)} · HTML y CSS en un archivo</small></div></div>`;
function resExport(){if(!BR.res){T('ZIP exportado');return}const h=BR.res.html;
 const fb=()=>{const t=document.createElement('textarea');t.value=h;document.body.append(t);t.select();let ok=false;try{ok=document.execCommand('copy')}catch(e){}t.remove();T(ok?'Código copiado':'No se pudo copiar')};
 try{navigator.clipboard.writeText(h).then(()=>T('Código copiado'),fb)}catch(e){fb()}}
function resOpen(){if(!BR.res){T('Abriendo en el navegador…');return}const d=document.createElement('div');d.id='rsfull';
 d.innerHTML=`<iframe sandbox="" title="Diseño a pantalla completa" srcdoc="${esc(BR.res.html)}"></iframe><button aria-label="Cerrar" onclick="this.parentNode.remove()">${I('x',16)}</button>`;document.body.append(d)}
