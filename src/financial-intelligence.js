// All monetary inputs are integer euro cents, net of recoverable input VAT where applicable.
export function financial({venue,from,to,sales,costs,expenses}){
 if(!['NOMAD','Artist'].includes(venue))throw Error('Lokal');
 const date=v=>typeof v==='string'&&/^\d{4}-\d{2}-\d{2}$/.test(v)&&!isNaN(Date.parse(v))&&new Date(v).toISOString().slice(0,10)===v;
 if(!date(from)||!date(to)||from>to)throw Error('Razdoblje');
 const money=v=>{if(!Number.isSafeInteger(v))throw Error('Iznos u centima');return v;};
 const add=(a,b)=>money(a+b);
 const seen=new Set(),days=new Map(),saleIds=new Set(),covered=new Set();
 function check(r,kind){if(r.venue!==venue||!date(r.date)||r.date<from||r.date>to||typeof r.id!=='string'||!r.id.trim())throw Error('Opseg zapisa');const k=JSON.stringify([kind,r.id]);if(seen.has(k))throw Error('Duplikat');seen.add(k);}
 let gross=0,vat=0,consumptionTax=0,cogs=0,opex=0;
 for(const r of sales){check(r,'sale');saleIds.add(r.id);const g=money(r.gross),v=money(r.vat),p=money(r.consumptionTax);if((g>=0&&(v<0||p<0||add(v,p)>g))||(g<0&&(v>0||p>0||add(v,p)<g)))throw Error('Porezni iznosi');gross=add(gross,g);vat=add(vat,v);consumptionTax=add(consumptionTax,p);days.set(r.date,add(days.get(r.date)||0,g));}
 for(const r of costs){check(r,'cost');if(!saleIds.has(r.sale_id)||covered.has(r.sale_id))throw Error('Trošak prodaje mora biti jedinstven po računu');covered.add(r.sale_id);cogs=add(cogs,money(r.net));}
 for(const r of expenses){check(r,'expense');opex=add(opex,money(r.net));}
 const net=add(add(gross,-vat),-consumptionTax),missing=[...saleIds].filter(id=>!covered.has(id)),complete=missing.length===0;
 const margin=complete?add(net,-cogs):null;
 return {venue,from,to,gross,vat,consumptionTax,net,cogs,opex,margin,marginPercent:complete&&net>0?margin/net*100:null,operatingEstimate:complete?add(margin,-opex):null,missingCostSaleIds:missing,complete,daily:[...days].sort(([a],[b])=>a.localeCompare(b)).map(([date,gross])=>({date,gross})),review_required:true};
}
