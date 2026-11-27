export const specs=Object.freeze({
items:{keys:['id'],fields:{sku:'Šifra',name:'Naziv',unit:'Jedinica',purchase_price:'Nabavna cijena'}},
stock:{keys:['item_id'],fields:{item_id:'Artikl',quantity:'Zaliha',minimum:'Minimum'}},
recipes:{keys:['product_id','ingredient_id'],fields:{product_id:'Proizvod',ingredient_id:'Sastojak',quantity:'Količina'}},
transactions:{keys:['id'],fields:{booked_on:'Datum',kind:'Vrsta',amount:'Iznos',description:'Opis'}}
});
export function scope(venue,kind){if(!['NOMAD','Artist'].includes(venue)||!Object.hasOwn(specs,kind))throw Error('Neispravna evidencija.');return specs[kind];}
export function payload(kind,values,editing=false){const s=scope('NOMAD',kind),out={};for(const k of Object.keys(s.fields)){if(editing&&s.keys.includes(k))continue;const v=String(values[k]??'').trim();if(!v&&k!=='description')throw Error('Popuni sva obavezna polja.');out[k]=v;}return out;}
export function matches(row,q){return Object.values(row).some(v=>String(v).toLocaleLowerCase('hr').includes(q.toLocaleLowerCase('hr')));}
