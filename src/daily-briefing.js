export const categories=Object.freeze({weather:'Vrijeme u Novom Marofu i Hrvatskoj',sport:'Sport, Dinamo Zagreb i hrvatski sport',hospitality:'Ugostiteljski trendovi u Hrvatskoj',technology:'Informatika, AI i sigurnost',markets:'Financijska tržišta i gospodarstvo',fashion:'Modni trendovi'});
export function buildBriefing(ids){
 if(!Array.isArray(ids)||!ids.length||ids.length>6||ids.some(id=>!Object.hasOwn(categories,id)))throw Error('Odaberi valjane kategorije.');
 return 'Na hrvatskom sastavi sažet aktualni izvještaj za: '+[...new Set(ids)].map(id=>categories[id]).join('; ')+'. Navedi datume i izvore. Jasno označi neprovjerene podatke.';
}
export function createBriefingRunner(search,now=()=>Date.now()){
 let busy=false,last=-Infinity,count=0,day='';
 return async ids=>{
 const query=buildBriefing(ids),t=now(),d=new Date(t).toISOString().slice(0,10);
 if(d!==day){day=d;count=0;}
 if(busy||t-last<60000||count>=3)throw Error('Ograničenje: 3 pokušaja dnevno po sesiji, razmak 60 sekundi.');
 busy=true;last=t;count++;
 try{const r=await search(query);const sources=(Array.isArray(r?.sources)?r.sources:[]).filter(x=>typeof x?.url==='string'&&/^https:\/\/[^\s\\]+$/i.test(x.url)).slice(0,8);if(typeof r?.text!=='string'||!r.text.trim()||!sources.length)throw Error('Nema izvještaja s izvorima.');return {text:r.text,sources};}finally{busy=false;}
 };
}
