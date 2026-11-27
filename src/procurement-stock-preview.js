export function stockPreview(pages,venue){
 if(venue!=='NOMAD'||!Array.isArray(pages)||pages.length>200)throw Error('Provjeri lokal i dokument');
 const rows=[];
 for(let p=0;p<pages.length;p++)for(const line of String(pages[p]).split(/\r?\n/)){
 const m=line.match(/^\s*(?:(\d+)\s+)?(.+?)\s{2,}(Kg|Komad|Litra)\s+(-?\d+,\d{4})\s*$/);
 if(!m)continue;
 const stock=Number(m[4].replace(',','.'));if(!Number.isFinite(stock))throw Error('Količina');
 rows.push({venue,sourcePage:p+1,sku:m[1]||null,name:m[2].trim(),unit:{Kg:'kg',Komad:'kom',Litra:'L'}[m[3]],stock,unitCost:null,review_required:true,issues:[...(!m[1]?['Nedostaje šifra']:[]),...(stock<0?['Negativno stanje']:[]),...(/[\x00-\x1f]/.test(m[2])?['Nečitljiv naziv']:[])]});
 }return rows;
}
