import {previewPrices,previewPDF} from './remaris-native.js';
import {recipeCandidates} from './remaris-recipe-preview.js';
export async function readNative(file,venue,kind,readers){
 if(!file||file.size>10000000||!['NOMAD','Artist'].includes(venue))throw Error('Odaberi datoteku do 10 MB i lokal.');
 const bytes=await file.arrayBuffer();
 if(file.name.toLowerCase().endsWith('.xlsx')){
 if(kind!=='prices')throw Error('XLSX trenutno podržava cjenik.');
 const rows=await readers.xlsx(bytes);return {kind,venue,rows:previewPrices(rows,venue),review:true};
 }
 if(file.name.toLowerCase().endsWith('.pdf')){
 const pages=await readers.pdf(bytes);const raw=previewPDF(pages);
 return {kind,venue,rows:kind==='recipes'?recipeCandidates(pages):raw,raw,review:true};
 }
 throw Error('Podržani formati: XLSX i PDF.');
}
export async function localReaders(kind='both'){
 if(!['xlsx','pdf','both'].includes(kind))throw Error('Nepodržan format');
 const x=kind!=='pdf'?await import('../vendor/xlsx.mjs'):null;
 const p=kind!=='xlsx'?await import('../vendor/pdf.mjs'):null;
 if(p)p.GlobalWorkerOptions.workerSrc=new URL('../vendor/pdf.worker.mjs',import.meta.url).href;
 return {xlsx:async bytes=>{const w=x.read(bytes,{type:'array'});return x.utils.sheet_to_json(w.Sheets[w.SheetNames[0]],{header:1,defval:null});},pdf:async bytes=>{const d=await p.getDocument({data:bytes,isEvalSupported:false,enableXfa:false}).promise;try{if(d.numPages>200)throw Error('Najviše 200 stranica.');const pages=[];for(let i=1;i<=d.numPages;i++){const page=await d.getPage(i),t=await page.getTextContent();pages.push(t.items.map(v=>v.str+(v.hasEOL?'\n':'  ')).join(''));}return pages;}finally{await d.destroy();}}};
}
