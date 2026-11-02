/* ===== Skills reales y Ajustes (Apariencia, Almacenamiento, Rendimiento) =====
   Solo declara funciones y datos; usa st, BR, M… de app.js cuando se le llama. */

/* ---------- Skills: cada una añade instrucciones al agente ---------- */
const SKP=['Usa componentes de interfaz modernos: tarjetas con sombra suave, chips, insignias, botones primarios y secundarios, iconos con emoji y estados hover.',
 'Escribe el CSS con clases utilitarias al estilo Tailwind (flex, grid, gap-4, p-6, rounded-2xl, shadow-lg, text-center…) definidas por ti dentro de <style>.',
 'Organiza el HTML en bloques reutilizables con clases BEM claras (bloque__elemento--modificador), listos para pasar a componentes de React.',
 'Diseña como una app Android: barra superior, contenido en tarjetas, botón flotante y barra de navegación inferior fija, al estilo Material Design 3.',
 'Define un sistema de diseño en :root con variables CSS de colores, tipografía, radios y espaciados, y úsalas en todo el CSS.',
 'Diseña en modo oscuro: fondos casi negros, superficies elevadas un poco más claras y textos con buen contraste.',
 'Cuida la accesibilidad: contraste AA, zonas táctiles de 44 px, textos alternativos, etiquetas en los formularios y jerarquía clara de encabezados.',
 'Añade animaciones CSS suaves: aparición de secciones, transiciones en botones y tarjetas al pasar el dedo, sin exagerar.'];
const skillPrompt=()=>SK.map((s,i)=>st.ski[i]?'- '+s[0]+': '+SKP[i]:'').filter(Boolean).join('\n');
function skToggle(i){st.ski[i]=!st.ski[i];BR.cfg.ski=st.ski;brSave();hap(st.ski[i]?'ok':'tap');
 T(st.ski[i]?SK[i][0]+' instalada · Ignis la usará en tus diseños':SK[i][0]+' desinstalada');R('skills')}

/* ---------- Ajustes desplegables ---------- */
const setRow=(k,ic,t,sub,g,body)=>`<button class="cd li" style="width:100%" onclick="st['o${k}']=!st['o${k}'];R('set')"><span class="ic" style="background:linear-gradient(135deg,${g})">${I(ic,14)}</span><div class="x"><b>${t}</b><small>${sub}</small></div><span style="color:var(--mut);display:grid;transform:rotate(${st['o'+k]?90:0}deg)">${I('chev',12)}</span></button>${st['o'+k]?`<div class="cd" style="padding:8px;margin-bottom:6px">${body()}</div>`:''}`;
const opts=(lbl,key,vals,names)=>`<div class="opt"><small>${lbl}</small><div>${vals.map((v,i)=>`<button class="pill ${BR.cfg[key]==v?'on':''}" onclick="setCfg('${key}',${JSON.stringify(v)})">${names[i]}</button>`).join('')}</div></div>`;
function setCfg(k,v){BR.cfg[k]=v;brSave();hap();
 if((k=='ctx'||k=='thr')&&LLM()&&LD.file){const m=M.find(x=>x[8]==LD.file);if(m){LD.file='';llmLoad(m);T('Recargando el modelo con el nuevo ajuste…')}}
 if(k=='vib'&&v)hap('ok');R('set')}

/* Apariencia: paleta del tema base de los diseños y vibración */
const THEMES=[['Violeta','#6d4aff','#ff6a9d'],['Océano','#0077ff','#00c2c7'],['Bosque','#1f8a4a','#a3d86b'],['Coral','#ff5a5f','#ffb347'],['Grafito','#22252f','#6b7cff']];
const themeVars=()=>{const r=refInfo();if(BR.cfg.th=='ref'&&r&&r.pal&&r.pal.length>1){const s=r.pal.filter(c=>{const n=parseInt(c.slice(1),16),R=n>>16,G=n>>8&255,B=n&255;return Math.max(R,G,B)-Math.min(R,G,B)>60});return [s[0]||r.pal[0],s[1]||r.pal[1]]}
 const t=THEMES[+BR.cfg.th]||THEMES[0];return [t[1],t[2]]};
const apBody=()=>`<small class="hint" style="display:block;margin:0 0 5px">Colores del tema base de los diseños que genera Ignis (si el modelo no elige los suyos).</small>
 <div class="g3" style="gap:5px">${THEMES.map((t,i)=>`<button class="pill ${(+BR.cfg.th||0)==i&&BR.cfg.th!='ref'?'on':''}" onclick="setCfg('th',${i})"><i style="display:inline-block;width:10px;height:10px;border-radius:50%;background:linear-gradient(135deg,${t[1]},${t[2]});margin-right:4px;vertical-align:-1px"></i>${t[0]}</button>`).join('')}<button class="pill ${BR.cfg.th=='ref'?'on':''}" onclick="setCfg('th','ref')">De la referencia</button></div>
 ${opts('Vibración al tocar','vib',[1,0],['Sí','No'])}`;
/* Almacenamiento */
function stoBody(){let F=[];try{F=LLM()?JSON.parse(IgnisLLM.models()):[]}catch(e){}
 const mod=F.reduce((a,f)=>a+(f.size||f.part||0),0),caps=(BR.saved||[]).filter(x=>x.img).length,pr=(BR.projects||[]).length,brain=JSON.stringify(BR).length;
 let free=0;try{free=LLM()?IgnisLLM.freeSpace():0}catch(e){}
 return `<div class="mem"><b>Modelos</b><small>${F.filter(f=>f.size).length} · ${fsz(mod)}${free>0?' · '+fsz(free)+' libres en el teléfono':''}</small></div>
 <div class="mem"><b>Proyectos</b><small>${pr} diseños guardados</small></div>
 <div class="mem"><b>Referencias</b><small>${caps} capturas e imágenes</small></div>
 <div class="mem"><b>Cerebro</b><small>${fsz(brain)} · ${rq()}</small></div>
 <div class="g2" style="margin-top:6px"><button class="btn sm o" onclick="to('models')">Gestionar modelos</button><button class="btn sm ${st.dpr?'':'dn'}" onclick="delProjects()">${st.dpr?'Toca otra vez':'Borrar proyectos'}</button></div>`}
function delProjects(){if(!st.dpr){st.dpr=1;R('set');setTimeout(()=>{st.dpr=0;R('set')},3000);return}
 st.dpr=0;BR.projects=[];BR.res=null;brSave();T('Proyectos borrados');R('set','home','result')}
/* Rendimiento: lo que usa el motor local */
const perfBody=()=>`${opts('Memoria de la conversación (contexto)','ctx',[0,2048,4096,8192],['Auto','2K','4K','8K'])}
 ${opts('Longitud de las respuestas','max',[0,800,4000],['Normal','Corta','Larga'])}
 ${opts('Creatividad','temp',[0.3,0.7,1.0],['Precisa','Equilibrada','Creativa'])}
 ${opts('Hilos del procesador','thr',[0,2,4,6],['Auto','2','4','6'])}
 <p class="hint" style="margin:6px 0 0">Más contexto o hilos consumen más RAM y batería. Si el teléfono se calienta o se cierra la app, baja estos valores.</p>`;
const ajRows=()=>setRow('ap','palette','Apariencia','Colores de los diseños, vibración','#c0323a,#ff6a6a',apBody)+setRow('sto','db','Almacenamiento','Modelos, proyectos y espacio','#7a3bc8,#d06bff',stoBody)+setRow('perf','bolt','Rendimiento','Contexto, longitud, hilos','#c08a1f,#ffc04a',perfBody);
