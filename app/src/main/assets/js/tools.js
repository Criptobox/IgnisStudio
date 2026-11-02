/* ===== Crear app (antes «Compilar APK») y herramientas MCP reales =====
   Solo declara funciones; usa st, BR… de app.js cuando se le llama. */

/* ---------- Crear app: el diseño como app en la pantalla de inicio ---------- */
const APS=['Optimizar recursos','Generar la app','Preparar el icono','Verificar','Añadir a la pantalla de inicio'];
const apkScreen=()=>`${H('Crear app')}<div style="text-align:center;margin-bottom:8px"><div style="color:#3ddc97;filter:drop-shadow(0 0 14px #3ddc9788)">${I('android',58)}</div><h2 style="font-size:15px;margin-top:6px">Tu diseño como app</h2><p style="font-size:9px;color:var(--mut);margin-top:3px">Crea un icono en la pantalla de inicio que abre el diseño a pantalla completa, sin barra del navegador.</p></div>
 ${BR.res?`<div class="cd" style="padding:8px;margin-bottom:8px"><small class="hint" style="display:block;margin:0 0 4px">Nombre de la app</small><div class="sr" style="margin:0"><input id="apn" maxlength="24" value="${esc(String(BR.res.n).slice(0,24))}"></div></div>`:`<p class="hint" style="margin-bottom:8px">Primero pide un diseño en el chat o abre uno del historial.</p>`}
 ${APS.map((x,i)=>`<div class="ck ${i<st.ap?'on':''}"><i>${i<st.ap?I('check',9):''}</i>${x}</div>`).join('')}
 <button class="btn" style="width:100%;margin-top:12px;padding:11px" onclick="mkApp()" ${BR.res?'':'disabled'}>${st.ap>=5?'Crear otra vez':'Crear app'}</button>
 ${BR.res?`<button class="btn o" style="width:100%;margin-top:6px" onclick="saveRes()">${I('dl',12)} Guardar el HTML</button>`:''}`;
function mkApp(){if(!BR.res){T('Primero pide un diseño');return}
 const name=(($('#apn')||{}).value||BR.res.n||'Mi diseño').trim().slice(0,24);
 st.ap=0;setMood('work');R('apk');hap();
 const t=setInterval(()=>{st.ap++;R('apk');if(st.ap<4)return;clearInterval(t);
  if(window.IgnisWeb&&IgnisWeb.pinApp){const id=(BR.res.id||'p'+Date.now()).replace(/[^A-Za-z0-9_-]/g,'').slice(0,40);
   try{IgnisWeb.pinApp(id,name,BR.res.html,themeVars()[0])}catch(e){onPinned('err')}}
  else{st.ap=5;R('apk');saveRes();setMood('done',2400);T('En el navegador se descarga el HTML; en la app se crea el icono')}},350)}
window.onPinned=s=>{if(s=='ok'){st.ap=5;R('apk');setMood('done',3000);hap('ok');recordar('app','Creó la app «'+(BR.res&&BR.res.n)+'»');T('Confirma en el aviso de Android para añadir el icono')}
 else{st.ap=0;R('apk');setMood('error',2600);T(s=='nosupport'?'Tu pantalla de inicio no permite añadir accesos directos':'No se pudo crear la app')}};

/* ---------- Herramientas (MCP) ---------- */
function mcpToggle(i){st.mci[i]=!st.mci[i];BR.cfg.mci=st.mci;brSave();hap(st.mci[i]?'ok':'tap');T(st.mci[i]?MC[i][0]+' instalada':MC[i][0]+' desinstalada');R('mcp')}
function mcpUse(i){hap();
 if(i==0){if(!BR.res){T('Primero pide un diseño');return}reviewOpen();return}
 if(i==1){if(window.IgnisWeb){IgnisWeb.open('https://www.figma.com/community','Figma');T('Abre un diseño y toca ♥ Guardar para usarlo como referencia')}else window.open('https://www.figma.com/community','_blank');return}
 if(i==2){driveSheet();return}
 if(i==3){if(!BR.res){T('Primero pide un diseño');return}shareRes();T('Elige Mega en el menú de compartir');return}
 if(i==4){const q=((BR.chat||[]).filter(x=>x.r=='user').pop()||{}).c||'landing page';
  const w=q.replace(/^(crea|diseña|haz|genera)\s+(una?\s+)?/i,'').split(/\s+/).slice(0,5).join(' ');
  const u='https://dribbble.com/search/'+encodeURIComponent(w);
  if(window.IgnisWeb){IgnisWeb.open(u,'Inspiración: '+w);T('Toca ♥ Guardar en lo que te guste')}else window.open(u,'_blank')}}

/* Revisión automática del HTML generado (herramienta Playwright) */
function review(html){const d=new DOMParser().parseFromString(html||'','text/html'),out=[];
 const ok=(c,t,f)=>out.push([c,c?t:f]);
 ok(!!d.querySelector('title')&&d.title.trim(),'Tiene título','Falta la etiqueta <title>');
 const h1=d.querySelectorAll('h1').length;ok(h1==1,'Un solo título principal (h1)',h1?'Hay '+h1+' títulos h1; mejor solo uno':'No hay título principal (h1)');
 const noAlt=[...d.images].filter(i=>!i.getAttribute('alt')).length;ok(!noAlt,'Todas las imágenes tienen descripción',noAlt+' imágenes sin texto alternativo');
 const badA=[...d.querySelectorAll('a')].filter(a=>!a.textContent.trim()&&!a.getAttribute('aria-label')).length;ok(!badA,'Los enlaces tienen texto',badA+' enlaces sin texto');
 const css=[...d.querySelectorAll('style')].map(s=>s.textContent).join('\n'),own=css.replace(/:root\{--a:[\s\S]*?footer p\{margin:0\}/,'');
 ok(own.length>200,'El diseño tiene estilos propios','El modelo no escribió CSS: se usa el tema base de Ignis');
 const wide=(own.match(/(?:^|[;{\s])(?:min-)?width\s*:\s*(\d{3,})px/g)||[]).filter(x=>+x.match(/(\d+)px/)[1]>480).length;ok(!wide,'Sin anchos fijos que rompan el móvil',wide+' anchos fijos de más de 480 px');
 const tiny=(own.match(/font-size\s*:\s*(\d+(?:\.\d+)?)px/g)||[]).filter(x=>parseFloat(x.match(/([\d.]+)px/)[1])<12).length;ok(!tiny,'Textos legibles',tiny+' tamaños de letra menores de 12 px');
 const inp=[...d.querySelectorAll('input,textarea,select')].filter(e=>!e.getAttribute('placeholder')&&!e.getAttribute('aria-label')&&!(e.id&&d.querySelector('label[for="'+e.id+'"]'))).length;ok(!inp,'Los campos de formulario se entienden',inp+' campos sin etiqueta ni texto de ayuda');
 ok(!d.querySelector('script'),'Sin JavaScript (seguro en la vista previa)','Tiene JavaScript: en la vista previa no se ejecuta');
 return out}
function reviewOpen(){qmClose();const r=review(BR.res.raw||BR.res.html),bad=r.filter(x=>!x[0]).length,ph=$('#p-'+cur()),sh=document.createElement('div'),m=document.createElement('div');
 sh.className='igsh';sh.onclick=qmClose;m.className='igds';
 m.innerHTML=`<i class="grab"></i><div class="igdh"><div><b>Revisión de «${esc(BR.res.n)}»</b><small>${bad?bad+' cosas que mejorar':'Todo correcto'}</small></div></div>
 ${r.map(x=>`<div class="mem"><b style="color:${x[0]?'#3ddc97':'#ffb84a'}">${x[0]?'✓':'⚠'} ${esc(x[1])}</b></div>`).join('')}
 ${bad?`<button class="btn" style="width:100%;margin-top:8px" onclick="reviewFix()">Pedir a Ignis que lo corrija</button>`:''}`;
 ph.append(sh,m);requestAnimationFrame(()=>{m.classList.add('igon');sh.classList.add('igon')})}
function reviewFix(){const r=review(BR.res.raw||BR.res.html).filter(x=>!x[0]).map(x=>x[1]);qmClose();to('chat');
 setTimeout(()=>{const c=$('#ci');if(!c)return;c.value='Mejora el diseño corrigiendo esto: '+r.join('; ');if(LLM()||cloudOn())snd();else c.focus()},300)}
/* tras cada diseño, si la herramienta está instalada */
function autoReview(html){if(!st.mci[0])return'';const bad=review(html).filter(x=>!x[0]).length;
 return `<div style="margin-top:5px"><button class="cbk" style="border:0;color:inherit;cursor:pointer" onclick="reviewOpen()">${I('check',10)} Revisión: ${bad?bad+' avisos':'todo correcto'}</button></div>`}
