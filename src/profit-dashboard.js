export function profitDashboard(rows,venue,targetMargin=70){
 if(!['NOMAD','Artist'].includes(venue)||!Number.isFinite(targetMargin)||targetMargin<0||targetMargin>=100)throw Error('Parametri');
 const seen=new Set(),complete=[],incomplete=[];
 for(const r of rows){if(r.venue!==venue||!r.id||seen.has(r.id)||!Number.isFinite(r.sold)||r.sold<0)throw Error('Artikl ili količina');seen.add(r.id);
 if(!r.complete){incomplete.push(r.id);continue;}
 if(![r.costCents,r.netCents].every(x=>Number.isSafeInteger(x)&&x>=0))throw Error('Iznos');
 const margin=r.netCents-r.costCents,total=Math.round(margin*r.sold),targetNet=Math.ceil(r.costCents/(1-targetMargin/100));
 if(!Number.isSafeInteger(total)||!Number.isSafeInteger(targetNet))throw Error('Prekoračenje');
 complete.push({...r,marginCents:margin,totalMarginCents:total,marginPercent:r.netCents>0?100*margin/r.netCents:null,targetNetCents:targetNet,belowTarget:r.netCents===0||100*margin/r.netCents<targetMargin});
 }
 const total=complete.reduce((s,r)=>s+r.totalMarginCents,0);if(!Number.isSafeInteger(total))throw Error('Prekoračenje');
 return {venue,ranked:complete.sort((a,b)=>b.totalMarginCents-a.totalMarginCents),incomplete,knownMarginCents:total,totalMarginCents:incomplete.length?null:total,review_required:true};
}
