export function parseCSV(text,sep=';'){
 if(![',',';','\t'].includes(sep)||typeof text!=='string'||text.length>2000000)throw Error('Neispravna datoteka.');
 const rows=[];let row=[],field='',quoted=false,closed=false;
 text=text.replace(/^\uFEFF/,'');
 for(let i=0;i<text.length;i++){const c=text[i];if(quoted){if(c==='"'){if(text[i+1]==='"'){field+='"';i++;}else{quoted=false;closed=true;}}else field+=c;continue;}
 if(closed&&c!==sep&&c!=='\r'&&c!=='\n')throw Error('Neispravan CSV navodnik.');
 if(c==='"'){if(field||closed)throw Error('Neispravan CSV navodnik.');quoted=true;}
 else if(c===sep){row.push(field);field='';closed=false;}
 else if(c==='\r'||c==='\n'){if(c==='\r'&&text[i+1]==='\n')i++;row.push(field);if(row.some(x=>x.trim()))rows.push(row);row=[];field='';closed=false;}
 else field+=c;
 }if(quoted)throw Error('Nezatvoren navodnik.');row.push(field);if(row.some(x=>x.trim()))rows.push(row);
 if(rows.length<2||rows.length>5001||rows[0].length>100||rows.some(r=>r.length!==rows[0].length))throw Error('Neispravna struktura ili veličina tablice.');return rows;
}
export function previewItems(rows,map,existing=[]){
 const keys=['sku','name','unit','purchase_price'];if(keys.some(k=>!Number.isInteger(map[k])||map[k]<0||map[k]>=rows[0].length)||new Set(keys.map(k=>map[k])).size!==4)throw Error('Mapiraj četiri različita stupca.');
 const seen=new Set(existing.map(x=>String(x.sku).trim().toLowerCase()));
 return rows.slice(1).map((r,i)=>{const v=Object.fromEntries(keys.map(k=>[k,String(r[map[k]]??'').trim()]));const errors=[];const sku=v.sku.toLowerCase();if(!sku||v.sku.length>80||!v.name||v.name.length>200)errors.push('Šifra ili naziv');if(!['kom','kg','L'].includes(v.unit))errors.push('Jedinica');const price=v.purchase_price.replace(',','.');if(!/^\d{1,10}(\.\d{1,4})?$/.test(price))errors.push('Cijena');else v.purchase_price=price;if(seen.has(sku))errors.push('Duplikat');seen.add(sku);return {line:i+2,values:v,errors};});
}
export function usage(sales,recipes){const totals=new Map(),missing=new Set();for(const s of sales){if(!s.product_id||!Number.isFinite(s.quantity)||s.quantity<0)throw Error('Neispravna prodaja.');const rr=recipes.filter(r=>r.product_id===s.product_id);if(!rr.length)missing.add(s.product_id);for(const r of rr){const q=Number(r.quantity);if(!r.ingredient_id||!Number.isFinite(q)||q<=0)throw Error('Neispravan normativ.');totals.set(r.ingredient_id,(totals.get(r.ingredient_id)||0)+q*s.quantity);}}return {totals:[...totals].map(([item_id,quantity])=>({item_id,quantity})),missing:[...missing]};}
