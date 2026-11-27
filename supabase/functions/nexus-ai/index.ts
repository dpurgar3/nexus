const headers = {'Content-Type':'application/json','Cache-Control':'no-store','Access-Control-Allow-Origin':'*','Access-Control-Allow-Headers':'authorization, apikey, content-type, x-client-info','Access-Control-Allow-Methods':'POST, OPTIONS'};
const reply=(status:number,data:unknown)=>new Response(JSON.stringify(data),{status,headers});
Deno.serve(async(req)=>{
 if(req.method==='OPTIONS') return new Response(null,{status:204,headers});
 if(req.method!=='POST') return reply(405,{error:'Method not allowed'});
 const auth=req.headers.get('Authorization')||'';
 if(!/^Bearer [^\s]+$/.test(auth)) return reply(401,{error:'Unauthorized'});
 try {
 const url=Deno.env.get('SUPABASE_URL')!;
 const key=Deno.env.get('SUPABASE_ANON_KEY')!;
 const userRes=await fetch(url+'/auth/v1/user',{headers:{apikey:key,Authorization:auth},signal:AbortSignal.timeout(8000)});
 if(!userRes.ok) return reply(401,{error:'Unauthorized'});
 const user=await userRes.json();
 if(!user.id||user.is_anonymous||user.app_metadata?.nexus_ai_access!==true) return reply(403,{error:'Access denied'});
 const raw=await req.text();
 if(raw.length>16000) return reply(413,{error:'Request too large'});
 let b;try{b=JSON.parse(raw)}catch{return reply(400,{error:'Invalid JSON'})}
 if(typeof b?.prompt!=='string'||!b.prompt.trim()||b.prompt.length>8000) return reply(400,{error:'Invalid prompt'});
 const search=b.search===true;
 const gem=b.provider==='gemini';
 if(search&&(!gem||b.prompt.length>500)) return reply(400,{error:'Search requires a short public query'});
 if(!gem&&b.provider!=='groq') return reply(400,{error:'Invalid provider'});
 const secret=Deno.env.get(gem?'GEMINI_API_KEY':'GROQ_API_KEY');
 const model=Deno.env.get(gem?'GEMINI_MODEL':'GROQ_MODEL');
 if(search&&(Deno.env.get('NEXUS_FREE_SEARCH_ENABLED')!=='true'||model!=='gemini-2.5-flash')) return reply(503,{error:'Free search not enabled'});
 if(!secret||!model) return reply(503,{error:'Provider not configured'});
 if(!/^[a-zA-Z0-9_.-]+$/.test(model)) return reply(503,{error:'Invalid configuration'});
 const serviceKey=Deno.env.get('SUPABASE_SERVICE_ROLE_KEY');
 if(!serviceKey) return reply(503,{error:'Quota unavailable'});
 const quota=await fetch(url+'/rest/v1/rpc/nexus_consume_quota',{method:'POST',headers:{apikey:serviceKey,Authorization:'Bearer '+serviceKey,'Content-Type':'application/json'},body:JSON.stringify({p_user:user.id}),signal:AbortSignal.timeout(8000)});
 if(!quota.ok) return reply(503,{error:'Quota unavailable'});
 if(await quota.json()!==true) return reply(429,{error:'Daily limit reached'});
 const upstream=await fetch(gem?'https://generativelanguage.googleapis.com/v1beta/models/'+model+':generateContent':'https://api.groq.com/openai/v1/chat/completions',{method:'POST',headers:{'Content-Type':'application/json',...(gem?{'x-goog-api-key':secret}:{Authorization:'Bearer '+secret})},body:JSON.stringify(gem?{contents:[{parts:[{text:b.prompt}]}],generationConfig:{maxOutputTokens:512},...(search?{tools:[{google_search:{}}]}:{})}:{model,messages:[{role:'user',content:b.prompt}],max_tokens:512}),signal:AbortSignal.timeout(25000)});
 if(upstream.status===429) return reply(429,{error:'Provider quota reached'});
 if(!upstream.ok) return reply(502,{error:'Provider request failed'});
 const d=await upstream.json();
 const sources=search?(d.candidates?.[0]?.groundingMetadata?.groundingChunks||[]).flatMap((c:any)=>{try{const u=new URL(c.web?.uri);return u.protocol==='https:'?[{url:u.href,title:String(c.web?.title||u.hostname).slice(0,200)}]:[]}catch{return []}}).slice(0,8):[];
 if(search&&!sources.length) return reply(503,{error:'No verified search sources'});
 return reply(200,{sources,text:gem?d.candidates?.[0]?.content?.parts?.map((p:{text?:string})=>p.text||'').join('')||'':d.choices?.[0]?.message?.content||''});
 }catch{return reply(503,{error:'Service unavailable'})}
});