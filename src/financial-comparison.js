export const expenseCategories=Object.freeze(['Plaće','Najam','Režije','Održavanje','Marketing','Bankovne naknade','Ostalo']);
export function comparePeriods(a,b){
 if(!a||!b||a.venue!==b.venue||a.to>=b.from)throw Error('Potrebna su dva odvojena kronološka razdoblja istog lokala');
 const days=r=>(Date.parse(r.to)-Date.parse(r.from))/86400000+1;
 const metrics={};for(const k of ['gross','net','cogs','opex','margin','operatingEstimate']){
 const x=a[k],y=b[k];if(x===null||y===null||((k==='cogs'||k==='margin'||k==='operatingEstimate')&&(!a.complete||!b.complete))){metrics[k]=null;continue;}
 if(!Number.isSafeInteger(x)||!Number.isSafeInteger(y)||!Number.isSafeInteger(y-x))throw Error('Neispravan iznos');
 metrics[k]={previous:x,current:y,change:y-x,percent:x>0?(y-x)/x*100:null,previousDaily:x/days(a),currentDaily:y/days(b)};
 }return {venue:a.venue,equalDuration:days(a)===days(b),metrics};
}
export function categorizeExpenses(rows,venue){
 if(!['NOMAD','Artist'].includes(venue))throw Error('Lokal');
 const totals=Object.fromEntries(expenseCategories.map(k=>[k,0])),seen=new Set();
 for(const r of rows){if(r.venue!==venue||!r.id||seen.has(r.id)||!expenseCategories.includes(r.category)||!Number.isSafeInteger(r.net))throw Error('Neispravan trošak');seen.add(r.id);totals[r.category]+=r.net;if(!Number.isSafeInteger(totals[r.category]))throw Error('Prekoračenje');}
 return totals;
}
