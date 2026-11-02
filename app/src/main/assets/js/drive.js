/* ===== Google Drive: Ignis busca tus diseños y se los pasa al modelo =====
   Usa window.IgnisDrive (DriveBridge.kt). Solo declara funciones; usa st, BR… de app.js. */
const DRIVE=()=>!!window.IgnisDrive;
const drvOn=()=>{try{return DRIVE()&&IgnisDrive.connected()}catch(e){return false}};
let DRQ={},DRN=0,DRR=[];           // peticiones pendientes, contador y últimos resultados
function drvReq(fn){return new Promise((res,rej)=>{const id='d'+(++DRN);DRQ[id]={res,rej};fn(id);
 setTimeout(()=>{if(DRQ[id]){delete DRQ[id];rej(new Error('Drive tardó demasiado'))}},30000)})}
window.onDrive=o=>{
 if(o.st=='connected'){hap('ok');T('Google Drive conectado');recordar('drive','Conectó Google Drive');driveSheet();R('mcp');return}
 const q=o.reqId&&DRQ[o.reqId];
 if(q){delete DRQ[o.reqId];o.st=='err'?q.rej(new Error(o.msg)):q.res(o);return}
 if(o.st=='err'){setMood('error',2400);T(o.msg)}};
const drvSearch=words=>drvReq(id=>IgnisDrive.search(JSON.stringify(words),id)).then(o=>o.files||[]);
const drvFetch=f=>drvReq(id=>IgnisDrive.fetch(f.id,f.mimeType,f.name,id));

/* palabras clave del pedido (sin palabras vacías) */
const STOP=new Set('crea crear haz hacer diseña diseñar genera generar quiero quisiera necesito puedes podrías una uno unos unas para como con del las los por que sus mis tus nuestro nuestra este esta ese esa algo más mas muy bien igual tipo estilo usando usa basado basada drive google página pagina web diseño diseños archivo archivos'.split(' '));
const kw=q=>[...new Set(String(q).toLowerCase().normalize('NFD').replace(/[̀-ͯ]/g,'').match(/[a-z0-9ñ]{4,}/g)||[])].filter(w=>!STOP.has(w)).slice(0,4);
/* ¿el pedido pide usar Drive? (o está activada la búsqueda en cada pedido) */
const driveWanted=q=>drvOn()&&(BR.cfg.drvAll||/\bdrive\b|mis (diseños|archivos|referencias)|guardad|anteriores?|como (el|los|las|la) (de|que)|basad[oa] en/i.test(q));
const kindOf=m=>m.startsWith('image/')?'imagen':m=='text/html'?'HTML':/presentation/.test(m)?'presentación':/document/.test(m)?'documento':'texto';

/* Busca en Drive y devuelve el contexto para el modelo (vacío si no hay nada útil) */
async function driveCtx(q,cloud){
 const words=kw(q);let files=await drvSearch(words);
 if(!files.length&&words.length)files=await drvSearch([]);   // nada coincide: los más recientes
 files=files.slice(0,3);if(!files.length)return'';
 const lim=cloud?12000:1400,out=[];
 for(const f of files){try{const r=await drvFetch(f);
  if(r.kind=='image'){const pal=await new Promise(ok=>{const im=new Image();im.onload=()=>ok(palette(im));im.onerror=()=>ok([]);im.src=r.data});
   out.push(`- «${f.name}» (imagen de diseño)${pal.length?': paleta '+pal.join(', '):''}`);
   if(!BR.saved.some(x=>x.drive==f.id)){BR.saved.unshift({id:'drv:'+Date.now()+out.length,n:f.name.replace(/\.[a-z0-9]+$/i,'').slice(0,30),k:'Drive',img:r.data,pal,drive:f.id,d:fecha()});BR.saved=BR.saved.slice(0,60)}}
  else{const txt=String(r.data||'').slice(0,Math.floor(lim/files.length));
   out.push(`- «${f.name}» (${kindOf(f.mimeType)}):\n\`\`\`${r.kind=='html'?'html':''}\n${txt}\n\`\`\``)}}catch(e){}}
 if(!out.length)return'';brSave();
 return 'Encontré estos archivos en el Google Drive del usuario relacionados con el pedido. Úsalos como referencia de estilo, contenido y estructura:\n'+out.join('\n')}

/* ---------- Explorador de Drive (Herramientas ▸ Drive) ---------- */
function driveSheet(){qmClose();const on=drvOn(),ph=$('#p-'+cur()),sh=document.createElement('div'),m=document.createElement('div');
 sh.className='igsh';sh.onclick=qmClose;m.className='igds';
 m.innerHTML=`<i class="grab"></i><div class="igdh"><div><b>Google Drive</b><small>${on?'Conectado · solo lectura':'Ignis busca tus diseños y los usa como referencia'}</small></div></div>
 ${!DRIVE()?'<p class="hint">Disponible en la app Android.</p>':!on?`<button class="btn" style="width:100%" onclick="IgnisDrive.connect()">${I('drive',13)} Conectar Google Drive</button><p class="hint" style="margin-top:6px">Se pide permiso de solo lectura. Ignis nunca modifica ni borra tus archivos.</p>`:
 `<div class="sr" style="margin:0 0 6px">${I('search',12)}<input id="drq" placeholder="Buscar en tu Drive (vacío = recientes)" onkeydown="if(event.key=='Enter')drvFind()"></div>
 <div class="g2" style="margin-bottom:6px"><button class="btn sm" onclick="drvFind()">Buscar</button><button class="btn sm o" onclick="saveRes()">Guardar diseño actual</button></div>
 <button class="li" style="width:100%;padding:6px 2px;margin:0" onclick="BR.cfg.drvAll=!BR.cfg.drvAll;brSave();driveSheet()"><div class="x"><b>Buscar en Drive en cada pedido</b><small>Si no, solo cuando digas «Drive», «mis diseños», «como el de…»</small></div><span class="sw ${BR.cfg.drvAll?'on':''}"></span></button>
 <div id="drl">${drvList()}</div>
 <button class="btn sm o" style="width:100%;margin-top:8px" onclick="IgnisDrive.disconnect();T('Drive desconectado');driveSheet()">Desconectar</button>`}`;
 ph.append(sh,m);requestAnimationFrame(()=>{m.classList.add('igon');sh.classList.add('igon')})}
const drvList=()=>DRR.map((f,i)=>`<div class="cd li"><span class="ic" style="background:linear-gradient(135deg,#1f7a3a,#e8b020)">${I(f.mimeType.startsWith('image/')?'img':'code',12)}</span><div class="x"><b>${esc(f.name)}</b><small>${kindOf(f.mimeType)}${f.modifiedTime?' · '+new Date(f.modifiedTime).toLocaleDateString('es'):''}</small></div><button class="btn sm" onclick="drvUse(${i})">Usar</button></div>`).join('');
async function drvFind(){const q=(($('#drq')||{}).value||'').trim(),l=$('#drl');if(l)l.innerHTML='<p class="hint">Buscando…</p>';
 try{DRR=await drvSearch(q?q.split(/\s+/).slice(0,5):[]);const l2=$('#drl');if(l2)l2.innerHTML=drvList()||'<p class="hint">No encontré diseños con esas palabras.</p>'}
 catch(e){const l2=$('#drl');if(l2)l2.innerHTML='';T(e.message)}}
/* Usar un archivo: imagen → referencia activa; HTML → proyecto; documento → al chat como contexto */
async function drvUse(i){const f=DRR[i];if(!f)return;T('Abriendo «'+f.name+'»…');
 try{const r=await drvFetch(f);
  if(r.kind=='image'){const im=new Image();im.onload=()=>{const id='drv:'+Date.now();BR.saved.unshift({id,n:f.name.replace(/\.[a-z0-9]+$/i,'').slice(0,30),k:'Drive',img:r.data,pal:palette(im),drive:f.id,d:fecha()});BR.ref=id;recordar('referencia','Referencia de Drive: '+f.name);brSave();hap('ok');T('Ignis usará «'+f.name+'» como referencia');R('lib','chat')};im.src=r.data}
  else if(r.kind=='html'){saveProject(polish(r.data),r.data,'Diseño de Drive: '+f.name,'Google Drive');brSave();qmClose();st.rt=0;R('result','home');to('result');T('Abierto desde Drive · pídele cambios en el chat')}
  else{qmClose();to('chat');setTimeout(()=>{const c=$('#ci');if(c){c.value='Usa este contenido de «'+f.name+'» para el diseño: '+String(r.data).slice(0,1500);c.focus()}},300)}}
 catch(e){T(e.message)}}
