export function inventory({venue,items,sales,recipes}){
 if(!['NOMAD','Artist'].includes(venue))throw Error('Lokal');
 const num=(v)=>{if(typeof v!=='number'||!Number.isFinite(v)||v<0)throw Error('Neispravna količina ili cijena');return v;};
 const scoped=r=>{if(r.venue!==venue)throw Error('Pogrešan lokal');};
 const stock=new Map(), formulas=new Map(), pairs=new Set(),missing=new Set();
 for(const r of items){scoped(r);if(!r.id||stock.has(r.id)||!['kg','L','kom'].includes(r.unit))throw Error('Artikl');stock.set(r.id,{...r,opening:num(r.opening),received:num(r.received),actual:num(r.actual),minimum:num(r.minimum),purchase_price:num(r.purchase_price),used:0});}
 for(const r of recipes){scoped(r);const item=stock.get(r.ingredient_id);if(!r.product_id||!item||item.unit!==r.unit||num(r.quantity)===0)throw Error('Normativ');const key=JSON.stringify([r.product_id,r.ingredient_id]);if(pairs.has(key))throw Error('Duplikat normativa');pairs.add(key);if(!formulas.has(r.product_id))formulas.set(r.product_id,[]);formulas.get(r.product_id).push(r);}
 const seen=new Set();
 for(const r of sales){scoped(r);if(!r.id||seen.has(r.id)||!r.product_id)throw Error('Prodaja');seen.add(r.id);const qty=num(r.quantity),f=formulas.get(r.product_id);if(!f){missing.add(r.product_id);continue;}for(const n of f)stock.get(n.ingredient_id).used+=qty*n.quantity;}
 const rows=[...stock.values()].map(r=>{const expected=r.opening+r.received-r.used;return {...r,expected,difference:r.actual-expected,stock_value:r.actual*r.purchase_price,below_minimum:r.actual<r.minimum,suggested_to_minimum:Math.max(0,r.minimum-r.actual)};});
 if(rows.some(r=>Object.values(r).some(v=>typeof v==='number'&&!Number.isFinite(v))))throw Error('Prekoračenje');
 return {venue,complete:missing.size===0,missing_recipes:[...missing],rows,stock_value:rows.reduce((s,r)=>s+r.stock_value,0),review_required:true};
}
