/* ===== Modelos en la nube (Modelos ▸ Nube) =====
   Con la clave de API del usuario. Las respuestas llegan al chat igual que las del modelo local.
   En el navegador no existe window.IgnisCloud y la pestaña queda en modo demostración. */
const CLOUD=()=>!!window.IgnisCloud;
const CLI={chatgpt:['platform.openai.com','API keys'],claude:['console.anthropic.com','API Keys'],gemini:['aistudio.google.com','Get API key'],
 deepseek:['platform.deepseek.com','API keys'],mistral:['console.mistral.ai','API Keys'],grok:['console.x.ai','API Keys'],perplexity:['perplexity.ai/settings/api','API Keys']};
let CK=[],CM={},CLD='';
function cloudSync(){if(!CLOUD())return;try{CK=JSON.parse(IgnisCloud.connected())}catch(e){CK=[]}}
cloudSync();
const cfgC=()=>BR.cfg.cm||(BR.cfg.cm={});
/* modelo en la nube en uso, con la forma de los modelos locales (ver M en app.js) */
function cloudModel(){const c=BR.cfg.cloud;if(!CLOUD()||!c||!CK.includes(c.id)||!c.model)return null;
 const n=(CL.find(x=>x[0]==c.id)||[0,c.id])[1];return ['cloud:'+c.id,n+' · '+c.model,'Nube · 0 GB',['Nube'],c.id,'#1f4fd0,#3a86ff',0,null,'',c.id,c.model]}
const cloudOn=()=>!!cloudModel();

function cloudList(){
 const act=BR.cfg.cloud;
 return `<p class="hint" style="margin:0 0 8px">Usa tu propia clave de API. Tus pedidos y el contexto del proyecto se envían a ese servicio; la clave se guarda cifrada en el teléfono.</p>
 ${act&&cloudOn()?`<button class="btn sm o" style="width:100%;margin-bottom:8px" onclick="cloudUse(null)">${I('cube',11)} Volver al modelo local</button>`:''}
 ${CL.map(c=>{const id=c[0],on=CK.includes(id),inUse=act&&act.id==id&&cloudOn(),help=CLI[id];
  const head=`<span class="ic lg" onclick="upl('${id}')" style="width:34px;height:34px">${I(id,34)}</span>`;
  if(!help)return `<div class="cd li" style="opacity:.55">${head}<div class="x"><b>${c[1]}</b><small>No tiene una API pública para usar desde apps</small></div></div>`;
  const list=CM[id]||[],sel=cfgC()[id]||'';
  let body='';
  if(st.cko==id&&!on)body=`<div style="display:grid;gap:5px;margin-top:6px"><div class="sr" style="margin:0"><input id="ck-${id}" type="password" autocomplete="off" placeholder="Pega tu clave de API"></div><small class="hint" style="margin:0">Consíguela en ${help[0]} ▸ ${help[1]}</small><div class="g2"><button class="btn sm" onclick="cloudSave('${id}')">Guardar</button><button class="btn sm o" onclick="st.cko=0;R('models')">Cancelar</button></div></div>`;
  else if(on)body=`<div style="display:grid;gap:5px;margin-top:6px">${list.length?`<select class="csel" onchange="cloudPick('${id}',this.value)">${(sel&&!list.includes(sel)?[sel]:[]).concat(list).map(m=>`<option ${m==sel?'selected':''}>${esc(m)}</option>`).join('')}</select>`:`<small class="hint" style="margin:0">${CLD==id?'Cargando modelos…':'Toca «Modelos» para elegir uno'}</small>`}
   <div class="g3" style="gap:5px"><button class="btn sm ${inUse?'dn':''}" onclick="cloudUse('${id}')">${inUse?'En uso':'Usar'}</button><button class="btn sm o" onclick="cloudLoad('${id}')">Modelos</button><button class="btn sm o" onclick="cloudDel('${id}')">${st.ckd==id?'¿Seguro?':'Quitar'}</button></div></div>`;
  return `<div class="cd li${inUse?' lo':''}" style="align-items:flex-start">${head}<div class="x"><b>${c[1]}${inUse?'<span class="ok">En uso</span>':''}</b><small>${on?(sel?esc(sel):'Clave guardada'):c[2]}</small>${body}</div>${on||st.cko==id?'':`<button class="btn sm" onclick="st.cko='${id}';R('models');setTimeout(()=>{const i=$('#ck-${id}');i&&i.focus()},50)">Conectar</button>`}</div>`}).join('')}`}

function cloudSave(id){const i=$('#ck-'+id),v=i&&i.value.trim();if(!v){T('Pega la clave de API');return}
 if(!IgnisCloud.setKey(id,v)){T('No se pudo guardar la clave');return}
 st.cko=0;cloudSync();hap('ok');T('Clave guardada · cargando modelos…');cloudLoad(id)}
function cloudLoad(id){CLD=id;R('models');IgnisCloud.models(id)}
function cloudPick(id,m){cfgC()[id]=m;if(BR.cfg.cloud&&BR.cfg.cloud.id==id)BR.cfg.cloud.model=m;brSave();R('models','chat','home')}
function cloudUse(id){
 if(!id){BR.cfg.cloud=null;brSave();T('Vuelves a usar el modelo local');R('models','chat','home');return}
 const m=cfgC()[id];if(!m){T('Primero elige un modelo');cloudLoad(id);return}
 BR.cfg.cloud={id,model:m};brSave();hap('ok');const cm=cloudModel();
 st.msgs.push(`<div class="bub bb">Ahora trabajo con ${esc(cm[1])} en la nube. ${BR.mem.length?'Mi cerebro conserva '+rq()+' de tu proyecto.':''}</div>`);recordar('modelo','Cambio a '+cm[1]);
 T(cm[1]+' en uso');R('models','chat','home')}
function cloudDel(id){if(st.ckd!=id){st.ckd=id;R('models');setTimeout(()=>{if(st.ckd==id){st.ckd=0;R('models')}},3000);return}
 st.ckd=0;IgnisCloud.removeKey(id);delete CM[id];if(BR.cfg.cloud&&BR.cfg.cloud.id==id)BR.cfg.cloud=null;cloudSync();brSave();T('Clave borrada');R('models','chat','home')}
window.onCloud=o=>{CLD='';
 if(o.st=='models'){const pref=/^(gpt|o\d|chatgpt|claude|gemini|deepseek|mistral|codestral|grok|sonar|llama)/i;
  CM[o.id]=(o.list||[]).filter(m=>pref.test(m)&&!/embed|tts|whisper|audio|image|dall|moderation|realtime|transcribe|search-preview|vision-preview/i.test(m));
  if(o.id!='claude')CM[o.id].sort(); // Claude: el orden recomendado (Opus primero)
  if(!CM[o.id].length)CM[o.id]=o.list||[];
  if(!cfgC()[o.id]&&CM[o.id].length){cfgC()[o.id]=CM[o.id][0];brSave()}
  T(CM[o.id].length?CM[o.id].length+' modelos disponibles':'No hay modelos de texto en esta cuenta')}
 else if(o.st=='err'){setMood('error',2400);T(o.msg)}
 R('models')};
