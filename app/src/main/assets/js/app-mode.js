/* Modo app: una pantalla a la vez, escalada al ancho del teléfono, botón atrás de Android vía historial */
(function(){
 const ids=SCR.map(s=>s[0]);
 function fit(){const z=innerWidth/240;document.querySelectorAll('.ph').forEach(p=>{p.style.zoom=z;p.style.height=(innerHeight/z)+'px'})}
 function show(id){if(!ids.includes(id))id='home';const pn=document.querySelector('.wrap>div.act .nv nav'),pi=pn?+pn.dataset.i:null;document.querySelectorAll('.wrap>div').forEach(d=>d.classList.toggle('act',d.querySelector('.ph').id=='p-'+id));if(id=='chat')badge('chat',0);if(id=='set'&&UP.st=='new'&&!st.upo){st.upo=1;R('set')}qmClose();const p=$('#p-'+id);p.classList.remove('fl','hid');void p.offsetWidth;p.classList.add('fl');const n=p.querySelector('.nv nav');if(n&&pi!=null&&pi!=+n.dataset.i){n.classList.remove('mv');n.classList.add('nt');n.style.setProperty('--i',pi);void n.offsetWidth;n.classList.remove('nt');n.style.setProperty('--i',n.dataset.i);n.classList.add('mv')}}
 window.to=function(id){if(location.hash.slice(1)===id)show(id);else location.hash=id};
 addEventListener('hashchange',()=>show(location.hash.slice(1)));
 addEventListener('resize',fit);
 fit();show(location.hash.slice(1)||'home');despierta();
 try{if(window.IgnisUpdate&&BR.cfg.au){const k='ig_uc',t=+localStorage.getItem(k)||0;if(Date.now()-t>216e5){localStorage.setItem(k,Date.now());setTimeout(()=>{UP.st='checking';IgnisUpdate.check()},2500)}}}catch(e){}
 /* ocultar la barra al bajar, mostrarla al subir */
 document.querySelectorAll('.scr').forEach(sc=>{let y=0;sc.addEventListener('scroll',()=>{const ph=sc.closest('.ph'),n=sc.scrollTop,d=n-y;if(n<24||d<-6)ph.classList.remove('hid');else if(d>6&&n>60)ph.classList.add('hid');y=n},{passive:true})});
 /* deslizar a los lados para cambiar de pestaña */
 const TABS=NB.map(b=>b[0]);let g=null;
 const hscroll=el=>{for(;el&&!el.classList.contains('ph');el=el.parentElement){if(el.scrollWidth>el.clientWidth+2&&/auto|scroll/.test(getComputedStyle(el).overflowX))return true;if(el.tagName=='INPUT'||el.tagName=='TEXTAREA')return true}return false};
 addEventListener('touchstart',e=>{const t=e.touches[0];g=(e.touches.length==1&&!hscroll(e.target))?{x:t.clientX,y:t.clientY,t:Date.now()}:null},{passive:true});
 addEventListener('touchend',e=>{if(!g)return;const t=e.changedTouches[0],dx=t.clientX-g.x,dy=t.clientY-g.y,cur=location.hash.slice(1)||'home',i=TABS.indexOf(cur);
  if(i>=0&&Date.now()-g.t<600&&Math.abs(dx)>70&&Math.abs(dx)>Math.abs(dy)*1.6){const j=i+(dx<0?1:-1);if(j>=0&&j<TABS.length){hap();to(TABS[j])}}g=null},{passive:true});
 /* bienvenida: en la APK la muestra Android; en el navegador, esta */
 if(!window.IgnisDevice){const sp=document.createElement('div');sp.id='splash';sp.innerHTML='<img src="img/splash.svg" alt=""><b>IGNIS STUDIO</b>';document.body.append(sp);setTimeout(()=>sp.classList.add('out'),1100);setTimeout(()=>sp.remove(),1700)}
})();
