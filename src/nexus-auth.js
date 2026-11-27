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
