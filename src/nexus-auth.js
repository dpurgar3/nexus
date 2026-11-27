import { createClient } from 'https://esm.sh/@supabase/supabase-js@2.95.0';
const client = createClient('https://mosxpouapccgbwyqiyhb.supabase.co','sb_publishable_de2iOvEg0QAGzCTQZUzvlg_kaO2O1Ss',{auth:{persistSession:false,autoRefreshToken:true,detectSessionInUrl:false}});
export async function login(email,password){
 const {data,error}=await client.auth.signInWithPassword({email,password});
 if(error) throw new Error('Prijava nije uspjela.');
 return data.user;
}
export async function logout(){
 const {error}=await client.auth.signOut();
 if(error) throw error;
}
export async function currentUser(){
 const {data,error}=await client.auth.getUser();
 return error?null:data.user;
}
export function onAuthChange(callback){
 return client.auth.onAuthStateChange((event,session)=>callback(event,session?.user??null)).data.subscription;
}
export async function askAI(provider,prompt){
 if(!['gemini','groq'].includes(provider)) throw new Error('Nepoznat pružatelj.');
 if(!await currentUser()) throw new Error('Potrebna je prijava.');
 const {data,error}=await client.functions.invoke('nexus-ai',{body:{provider,prompt}});
 if(error) throw new Error('AI servis nije dostupan.');
 return data.text;
}

export async function smartSearch(prompt){
 if(typeof prompt!=='string'||!prompt.trim()||prompt.length>500) throw new Error('Unesi javni upit do 500 znakova.');
 if(!await currentUser()) throw new Error('Potrebna je prijava.');
 const {data,error}=await client.functions.invoke('nexus-ai',{body:{provider:'gemini',prompt,search:true}});
 if(error||!data?.sources?.length) throw new Error('Pretraga nije dostupna ili je kvota iscrpljena.');
 return data;
}

async function memoryOwner(expected){
 const u=await currentUser();
 if(!u||u.id!==expected||u.is_anonymous||u.app_metadata?.nexus_ai_access!==true)throw new Error('Pristup memoriji nije dopušten.');
 return u.id;
}
export async function loadMemory(owner){
 const id=await memoryOwner(owner);
 const {data,error}=await client.from('nexus_preferences').select('preferences').eq('user_id',id).maybeSingle();
 if(error)throw new Error('Učitavanje memorije nije uspjelo.');
 const p=data?.preferences;
 return {notes:typeof p?.notes==='string'?p.notes.slice(0,1500):'',enabled:p?.enabled===true};
}
export async function saveMemory(owner,notes,enabled){
 if(typeof notes!=='string'||notes.length>1500||typeof enabled!=='boolean')throw new Error('Neispravne preferencije.');
 const id=await memoryOwner(owner);
 const {error}=await client.from('nexus_preferences').upsert({user_id:id,preferences:{notes:notes.trim(),enabled}},{onConflict:'user_id'});
 if(error)throw new Error('Spremanje memorije nije uspjelo.');
}
export async function deleteMemory(owner){
 const id=await memoryOwner(owner);
 const {error}=await client.from('nexus_preferences').delete().eq('user_id',id);
 if(error)throw new Error('Brisanje memorije nije uspjelo.');
}

import {scope,payload} from './business-management.js';
export async function businessRecords(owner,venue,kind,action='list',values={},key={},offset=0){
 const spec=scope(venue,kind),id=await memoryOwner(owner),table=client.from('nexus_biz_'+kind);
 let q;
 if(action==='list'){if(!Number.isSafeInteger(offset)||offset<0)throw Error('Neispravna stranica.');q=table.select('*').eq('owner_id',id).eq('venue',venue);for(const k of spec.keys)q=q.order(k);q=q.range(offset,offset+99);}
 else if(action==='insert')q=table.insert({...payload(kind,values),owner_id:id,venue}).select().single();
 else if(action==='update'||action==='delete'){q=action==='update'?table.update(payload(kind,values,true)):table.delete();q=q.eq('owner_id',id).eq('venue',venue);for(const k of spec.keys){if(typeof key[k]!=='string'||!key[k])throw Error('Nedostaje ključ.');q=q.eq(k,key[k]);}q=q.select().single();}
 else throw Error('Nepoznata operacija.');
 const {data,error}=await q;if(error)throw Error('Operacija nije uspjela. Provjeri podatke, povezane zapise i ovlasti.');return data;
}

export async function importItems(owner,venue,rows){
 scope(venue,'items');const id=await memoryOwner(owner);
 if(!Array.isArray(rows)||!rows.length||rows.length>500)throw Error('Najviše 500 artikala po uvozu.');
 const data=rows.map(r=>({...payload('items',r),owner_id:id,venue}));
 const {error}=await client.from('nexus_biz_items').insert(data);
 if(error)throw Error('Uvoz odbijen. Provjeri duplikate i podatke.');
}
