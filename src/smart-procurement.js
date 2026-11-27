export function procurement(venue,items){
 if(!['NOMAD','Artist'].includes(venue)||!Array.isArray(items)||items.length>10000)throw Error('Neispravan opseg');
 const seen=new Set();
 return items.map(r=>{
 if(r.venue!==venue||typeof r.id!=='string'||!r.id.trim()||seen.has(r.id)||!['kg','L','kom'].includes(r.unit))throw Error('Artikl ili lokal');seen.add(r.id);
 for(const k of ['stock','dailyUse','leadDays','reviewDays','safetyStock','packSize','onOrder','unitCost'])if(typeof r[k]!=='number'||!Number.isFinite(r[k])||r[k]<0||r[k]>1e9)throw Error('Neispravan unos: '+k);
 if(r.packSize<=0)throw Error('Pakiranje');
 if(r.unit==='kom'&&![r.stock,r.safetyStock,r.packSize,r.onOrder].every(Number.isInteger))throw Error('Komadi moraju biti cijeli');
 const reorderPoint=r.dailyUse*r.leadDays+r.safetyStock,target=r.dailyUse*(r.leadDays+r.reviewDays)+r.safetyStock;
 const position=r.stock+r.onOrder,shortage=Math.max(0,target-position),packs=Math.ceil(shortage/r.packSize),quantity=packs*r.packSize;
 const estimatedCost=quantity*r.unitCost;
 if(!Number.isSafeInteger(packs)||!Number.isFinite(estimatedCost)||estimatedCost>Number.MAX_SAFE_INTEGER/100)throw Error('Prekoračenje');
 return {...r,reorderPoint,target,position,packs,quantity,estimatedCost,daysRemaining:r.dailyUse>0?r.stock/r.dailyUse:null,status:r.stock<r.dailyUse*r.leadDays?'critical':position<=reorderPoint?'reorder':'normal',review_required:true};
 });
}
