export const venues=Object.freeze(['NOMAD','Artist']);
function number(v){if(typeof v!=='number'||!Number.isFinite(v)||v<0||v>1e9)throw Error('Neispravan iznos ili količina.');return v;}
function cents(v){return Math.round(number(v)*100);}
function venue(v){if(!venues.includes(v))throw Error('Nepoznat lokal.');return v;}
export function finance(v,rows){venue(v);if(!Array.isArray(rows)||rows.length>10000)throw Error('Neispravni podaci.');let income=0,expense=0;for(const r of rows){if(r.venue!==v)continue;if(!['income','expense'].includes(r.type))throw Error('Nepoznata vrsta.');if(r.type==='income')income+=cents(r.amount);else expense+=cents(r.amount);}return {income:income/100,expense:expense/100,balance:(income-expense)/100};}
export function inventory(v,rows){venue(v);return rows.filter(r=>r.venue===v).map(r=>{const expected=number(r.opening)+number(r.received)-number(r.consumed),actual=number(r.actual),minimum=number(r.minimum);return {name:String(r.name),unit:String(r.unit),expected,actual,variance:actual-expected,low:actual<minimum};});}
export function pricing(cost,margin,vat){number(cost);number(margin);number(vat);if(margin>=100||vat>100)throw Error('Neispravna stopa.');const net=cost/(1-margin/100);return {net:Math.round(net*100)/100,gross:Math.round(net*(1+vat/100)*100)/100,markup:cost===0?null:(net-cost)/cost*100};}
