export function matchPrices(stock,prices,venue){
 if(!['NOMAD','Artist'].includes(venue))throw Error('Lokal');
 const map=new Map();
 for(const p of prices){if(p.venue!==venue||typeof p.sku!=='string'||!p.sku.trim()||map.has(p.sku)||typeof p.sale_price!=='number'||!Number.isFinite(p.sale_price)||p.sale_price<0||p.errors?.length)throw Error('Neispravan cjenik');map.set(p.sku,p);}
 return stock.map(s=>{if(s.venue!==venue)throw Error('Pogrešan lokal');const p=s.sku?map.get(s.sku):null;return {...s,sale_price:p?.sale_price??null,price_match:p?'sku_review_required':'unmatched',review_required:true};});
}
export function stockValuation(rows,venue){
 if(!['NOMAD','Artist'].includes(venue))throw Error('Lokal');
 let total=0,missing=0;const seen=new Set();
 const items=rows.map(r=>{if(r.venue!==venue)throw Error('Pogrešan lokal');const id=r.sku||r.id;if(!id||seen.has(id))throw Error('Nedostaje identitet ili duplikat');seen.add(id);
 const valid=Number.isFinite(r.stock)&&r.stock>=0&&Number.isFinite(r.unitCost)&&r.unitCost>=0&&r.costConfirmed===true;
 const value=valid?Math.round(r.stock*r.unitCost*100):null;
 if(value===null){missing++;}else{if(!Number.isSafeInteger(value)||!Number.isSafeInteger(total+value))throw Error('Prekoračenje');total+=value;}
 return {...r,valueCents:value};});
 return {items,knownValueCents:total,totalValueCents:missing?null:total,missing,complete:missing===0};
}
