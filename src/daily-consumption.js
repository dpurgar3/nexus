export function dailyConsumption({venue,days,sales,recipes}){
 if(!['NOMAD','Artist'].includes(venue)||!Number.isInteger(days)||days<1||days>366)throw Error('Opseg');
 const formulas=new Map(),pairs=new Set(),units=new Map(),totals=new Map(),missing=new Set(),seen=new Set();
 for(const r of recipes){if(r.venue!==venue||!r.product_id||!r.ingredient_id||!['kg','L','kom'].includes(r.unit)||!Number.isFinite(r.quantity)||r.quantity<=0)throw Error('Normativ');const k=JSON.stringify([r.product_id,r.ingredient_id]);if(pairs.has(k)||units.has(r.ingredient_id)&&units.get(r.ingredient_id)!==r.unit)throw Error('Duplikat ili jedinica');pairs.add(k);units.set(r.ingredient_id,r.unit);if(!formulas.has(r.product_id))formulas.set(r.product_id,[]);formulas.get(r.product_id).push(r);}
 for(const s of sales){if(s.venue!==venue||!s.id||seen.has(s.id)||!s.product_id||!Number.isFinite(s.quantity)||s.quantity<0)throw Error('Prodaja');seen.add(s.id);const f=formulas.get(s.product_id);if(!f){missing.add(s.product_id);continue;}for(const r of f){const q=(totals.get(r.ingredient_id)||0)+s.quantity*r.quantity;if(!Number.isFinite(q))throw Error('Prekoračenje');totals.set(r.ingredient_id,q);}}
 return {venue,days,complete:missing.size===0,missing:[...missing],items:[...totals].map(([id,total])=>({id,unit:units.get(id),total,dailyUse:total/days})),review_required:true};
}
