export function recipeCandidates(pages){
 const rows=[];let product=null;
 for(let i=0;i<pages.length;i++)for(const raw of String(pages[i]).split(/\r?\n/)){
 const header=raw.match(/^\s*(\d+)\s+(.+?)\s{2,}.*(?:Šank|Kuhinja)\s*$/);
 if(header){product=header[1];continue;}
 const m=raw.match(/^\s*(?:(\d+)\s+)?(.+?)\s{2,}(\d+,\d{4})\s+(Kg|Komad|Litra)\s*$/);
 if(!m)continue;
 rows.push({page:i+1,product_sku:product,ingredient_sku:m[1]||null,ingredient_name:m[2].trim(),quantity:m[3].replace(',','.'),unit:{Kg:'kg',Komad:'kom',Litra:'L'}[m[4]],raw,review_required:true});
 }return rows;
}
