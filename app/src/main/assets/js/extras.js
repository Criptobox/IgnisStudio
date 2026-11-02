/* ===== Proyectos, historial, voz, imagen de referencia, compartir y plantillas =====
   Solo declara funciones; usa los datos de app.js (st, BR, M…) cuando se le llama. */

/* ---------- Proyectos: cada diseño generado queda guardado ---------- */
const PMAX=20;
function saveProject(html,raw,q,m){
 const p={id:'p'+Date.now(),html,raw,p:1,n:titleOf(raw)||q.slice(0,40),q,m,d:fecha()};
 BR.projects=[p].concat((BR.projects||[]).filter(x=>x.id!=p.id)).slice(0,PMAX);
 BR.res=p;return p}
function openProject(id){const p=(BR.projects||[]).find(x=>x.id==id);if(!p)return;BR.res=p;brSave();qmClose();st.rt=0;R('result');to('result')}
function delProject(id,ev){ev&&ev.stopPropagation();BR.projects=(BR.projects||[]).filter(x=>x.id!=id);if(BR.res&&BR.res.id==id)BR.res=BR.projects[0]||null;brSave();T('Proyecto borrado');histOpen();R('home','result')}
/* Inicio: proyectos de verdad; si aún no hay, ideas para empezar */
function recent(){const P=BR.projects||[];
 if(P.length)return `<div class="rcp"><b>Proyectos recientes</b><div class="rcl">${P.slice(0,6).map((p,i)=>`<button onclick="openProject('${p.id}')"><i style="background:linear-gradient(135deg,${TP[i%TP.length][3]})"></i>${esc(p.n)}</button>`).join('')}</div></div>`;
 return `<div class="rcp"><b>Empieza con una idea</b><div class="rcl">${TP.slice(0,5).map((t,i)=>`<button onclick="tplGo(${i})"><i style="background:linear-gradient(135deg,${t[3]})"></i>${t[0]}</button>`).join('')}</div></div>`}

/* ---------- Historial del chat (botón del reloj) ---------- */
function histOpen(){qmClose();const P=BR.projects||[],ph=$('#p-'+cur()),sh=document.createElement('div'),m=document.createElement('div');
 sh.className='igsh';sh.onclick=qmClose;m.className='igds';
 m.innerHTML=`<i class="grab"></i><div class="igdh"><div><b>Historial</b><small>${P.length?P.length+' diseños guardados':'Aún no hay diseños'}</small></div></div>
 <button class="btn" style="width:100%;margin-bottom:8px" onclick="newChat()">${I('plsq',13)} Nueva conversación</button>
 ${P.map(p=>`<div class="cd li" style="cursor:pointer" onclick="openProject('${p.id}')"><span class="ic" style="background:linear-gradient(135deg,#5b4bff,#ff5cae)">${I('code',12)}</span><div class="x"><b>${esc(p.n)}</b><small>${esc(p.m||'')} · ${esc(p.d||'')}</small></div><button class="bx" aria-label="Borrar" onclick="delProject('${p.id}',event)">${I('x',11)}</button></div>`).join('')||'<p class="hint">Pide un diseño en el chat y aparecerá aquí.</p>'}`;
 ph.append(sh,m);requestAnimationFrame(()=>{m.classList.add('igon');sh.classList.add('igon')})}
function newChat(){if(GEN){T('Espera a que Ignis termine');return}st.msgs=[];BR.chat=[];brSave();qmClose();R('chat');T('Nueva conversación · el cerebro conserva tus recuerdos')}

/* ---------- Dictado por voz ---------- */
let REC=null;
function micGo(){hap();
 if(window.IgnisWeb&&IgnisWeb.voice){try{IgnisWeb.voice();return}catch(e){}}
 const S=window.SpeechRecognition||window.webkitSpeechRecognition;if(!S){T('Este navegador no tiene dictado por voz');return}
 if(REC){REC.stop();return}REC=new S();REC.lang='es-ES';REC.interimResults=false;
 REC.onresult=e=>onVoice(e.results[0][0].transcript);REC.onerror=()=>onVoice(null);REC.onend=()=>{REC=null};REC.start();T('Escuchando…')}
window.onVoice=t=>{if(t==null){T('El dictado por voz no está disponible');return}if(!t)return;const i=$('#ci');if(!i)return;i.value=(i.value.trim()?i.value.trim()+' ':'')+t;i.focus()};

/* ---------- Imagen de referencia: se guarda y se extrae su paleta para el modelo ---------- */
function imgRef(){const f=document.createElement('input');f.type='file';f.accept='image/*';
 f.onchange=()=>{const fl=f.files[0];if(!fl)return;const r=new FileReader();r.onload=()=>{const im=new Image();im.onload=()=>{
  const z=Math.min(1,540/im.width),c=document.createElement('canvas');c.width=Math.round(im.width*z);c.height=Math.round(im.height*z);c.getContext('2d').drawImage(im,0,0,c.width,c.height);
  const id='img:'+Date.now(),x={id,n:(fl.name||'Imagen').replace(/\.[a-z0-9]+$/i,'').slice(0,30),k:'Imagen',img:c.toDataURL('image/jpeg',.8),pal:palette(c),d:fecha()};
  BR.saved.unshift(x);BR.ref=id;recordar('referencia','Imagen de referencia: '+x.n);brSave();hap('ok');T('Imagen de referencia lista · paleta: '+x.pal.slice(0,3).join(' '));R('chat','lib')};
  im.onerror=()=>T('Esa imagen no se puede leer');im.src=r.result};r.readAsDataURL(fl)};f.click()}
/* colores dominantes (hex), del más usado al menos usado */
function palette(src){try{const c=document.createElement('canvas');c.width=c.height=40;const g=c.getContext('2d');g.drawImage(src,0,0,40,40);
 const d=g.getImageData(0,0,40,40).data,cnt={};for(let i=0;i<d.length;i+=4){if(d[i+3]<128)continue;const k=(d[i]>>4)+','+(d[i+1]>>4)+','+(d[i+2]>>4);cnt[k]=(cnt[k]||0)+1}
 const out=[];for(const k of Object.keys(cnt).sort((a,b)=>cnt[b]-cnt[a])){const [r,gg,b]=k.split(',').map(v=>v*16+8);
  if(out.every(o=>Math.abs(o[0]-r)+Math.abs(o[1]-gg)+Math.abs(o[2]-b)>90))out.push([r,gg,b]);if(out.length>=5)break}
 return out.map(o=>'#'+o.map(v=>v.toString(16).padStart(2,'0')).join(''))}catch(e){return[]}}
/* paleta de las capturas del visor (se calcula al volver a la app) */
function palFill(){(BR.saved||[]).filter(x=>x.img&&!x.pal).slice(0,4).forEach(x=>{const im=new Image();im.onload=()=>{x.pal=palette(im);brSave()};im.src=x.img})}

/* ---------- Compartir y guardar el diseño ---------- */
const resName=()=>(BR.res&&BR.res.n||'diseno-ignis').replace(/[^\wáéíóúñ -]/gi,'').trim().slice(0,40)||'diseno-ignis';
function shareRes(){if(!BR.res){T('Primero pide un diseño en el chat');return}hap();
 if(window.IgnisWeb&&IgnisWeb.share){try{IgnisWeb.share(resName(),BR.res.html);return}catch(e){}}
 const f=new File([BR.res.html],resName()+'.html',{type:'text/html'});
 if(navigator.canShare&&navigator.canShare({files:[f]}))navigator.share({files:[f],title:BR.res.n}).catch(()=>{});else saveRes()}
function saveRes(){if(!BR.res){T('ZIP exportado');return}hap();
 if(window.IgnisWeb&&IgnisWeb.saveFile){try{IgnisWeb.saveFile(resName(),BR.res.html);return}catch(e){}}
 const a=document.createElement('a');a.href=URL.createObjectURL(new Blob([BR.res.html],{type:'text/html'}));a.download=resName()+'.html';a.click();setTimeout(()=>URL.revokeObjectURL(a.href),4000);T('Descargado '+resName()+'.html')}
window.onSaved=ok=>{if(ok==null)return;ok?(hap('ok'),T('Diseño guardado')):T('No se pudo guardar el archivo')};

/* ---------- Plantillas: cada una es un pedido real al agente ---------- */
const TPQ=['Crea una landing de una app fintech: hero con beneficios, tarjetas de funciones, planes de precios y testimonios',
 'Crea una landing para un producto SaaS moderno: hero oscuro, logos de clientes, funciones en tarjetas, precios y preguntas frecuentes',
 'Diseña la pantalla principal de una app móvil: saludo, tarjetas de resumen, accesos rápidos y lista de actividad',
 'Crea un portfolio personal de diseñador: presentación, proyectos destacados en tarjetas, servicios y contacto',
 'Crea una tienda online: cabecera con buscador, categorías, productos en tarjetas con precio y botón de compra',
 'Diseña un panel analítico: indicadores clave en tarjetas, gráfico de barras hecho con CSS, tabla de pedidos recientes'];
function tplGo(i){const q=TPQ[i]||('Crea un diseño: '+TP[i][0]);hap();to('chat');
 setTimeout(()=>{const c=$('#ci');if(!c)return;c.value=q;if(LLM())snd();else c.focus()},300)}
/* ¿el pedido cambia el diseño actual? entonces el modelo recibe el HTML para editarlo */
const isEdit=q=>/^(mejorar|recrear)|\b(cambia|modifica|mejora|añade|agrega|quita|elimina|pon|haz(lo)?|ahora|más|menos|otro color|ajusta)\b/i.test(q);
