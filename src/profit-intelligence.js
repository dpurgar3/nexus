export function profitability({venue,products,recipes,ingredients}){
 if(!['NOMAD','Artist'].includes(venue))throw Error('Lokal');
 const index=new Map(),formulas=new Map(),pairs=new Set(),seen=new Set();
 for(const i of ingredients){if(i.venue!==venue||!i.id||index.has(i.id)||!['kg','L','kom'].includes(i.unit))throw Error('Sirovina');index.set(i.id,i);}
 for(const r of recipes){if(r.venue!==venue||!r.product_id||!r.ingredient_id||!Number.isFinite(r.quantity)||r.quantity<=0||!['kg','L','kom'].includes(r.unit))throw Error('Normativ');const k=JSON.stringify([r.product_id,r.ingredient_id]);if(pairs.has(k))throw Error('Duplikat normativa');pairs.add(k);if(!formulas.has(r.product_id))formulas.set(r.product_id,[]);formulas.get(r.product_id).push(r);}
 return products.map(p=>{if(p.venue!==venue||!p.id||seen.has(p.id)||![p.grossCents,p.vatCents,p.consumptionTaxCents].every(x=>Number.isSafeInteger(x)&&x>=0)||p.vatCents+p.consumptionTaxCents>p.grossCents)throw Error('Proizvod ili porez');seen.add(p.id);
 const netCents=p.grossCents-p.vatCents-p.consumptionTaxCents,f=formulas.get(p.id),missing=[];let cost=0;
 if(!f)missing.push('Normativ');
 for(const r of f||[]){const i=index.get(r.ingredient_id);if(!i||i.unit!==r.unit||i.costConfirmed!==true||!Number.isFinite(i.unitCost)||i.unitCost<0){missing.push(r.ingredient_id);continue;}cost+=r.quantity*i.unitCost*100;}
 if(!Number.isFinite(cost)||cost>Number.MAX_SAFE_INTEGER)throw Error('Prekoračenje');
 const costCents=missing.length?null:Math.round(cost),marginCents=costCents===null?null:netCents-costCents;
 return {...p,netCents,costCents,marginCents,marginPercent:marginCents!==null&&netCents>0?100*marginCents/netCents:null,missing,complete:!missing.length,review_required:true};
 });
}
