export function businessAlerts({venue,stock=[],prices=[],profits=[],turnover=[],targetMargin=70,priceThreshold=5,turnoverThreshold=20}){
 if(!['NOMAD','Artist'].includes(venue)||![targetMargin,priceThreshold,turnoverThreshold].every(x=>Number.isFinite(x)&&x>=0&&x<=100))throw Error('Parametri');
 const alerts=[],skipped=[];const finite=x=>typeof x==='number'&&Number.isFinite(x);
 function scan(rows,kind,fn){const seen=new Set();for(const r of rows){if(r.venue!==venue||!r.id||seen.has(r.id))throw Error('Opseg ili duplikat');seen.add(r.id);if(r.confirmed!==true){skipped.push({kind,id:r.id});continue;}fn(r);}}
 const add=(r,kind,severity,value,action)=>alerts.push({id:r.id,kind,severity,value,action,venue});
 scan(stock,'stock',r=>{if(!finite(r.stock)){skipped.push({kind:'stock',id:r.id});return;}if(r.stock<0)add(r,'negative_stock','critical',r.stock,'Provjeri inventuru');else if(r.stock===0)add(r,'empty_stock','critical',0,'Provjeri dostupnost i nabavu');else if(finite(r.minimum)&&r.minimum>=0&&r.stock<r.minimum)add(r,'low_stock','warning',r.stock,'Pregledaj prijedlog nabave');});
 scan(prices,'price',r=>{if(!finite(r.previous)||r.previous<=0||!finite(r.current)||r.current<0){skipped.push({kind:'price',id:r.id});return;}const change=(r.current-r.previous)/r.previous*100;if(change>0&&change>=priceThreshold)add(r,'cost_increase','warning',change,'Usporedi dobavljače i maržu');});
 scan(profits,'profit',r=>{if(!finite(r.marginPercent)){skipped.push({kind:'profit',id:r.id});return;}if(r.marginPercent<targetMargin)add(r,'low_margin',r.marginPercent<0?'critical':'warning',r.marginPercent,'Provjeri normativ i prodajnu cijenu');});
 scan(turnover,'turnover',r=>{if(r.comparable!==true||!finite(r.baseline)||r.baseline<=0||!finite(r.current)||r.current<0){skipped.push({kind:'turnover',id:r.id});return;}const change=(r.current-r.baseline)/r.baseline*100;if(change!==0&&Math.abs(change)>=turnoverThreshold)add(r,'turnover_deviation',change<0?'warning':'info',change,'Provjeri uzrok odstupanja');});
 const rank={critical:0,warning:1,info:2};alerts.sort((a,b)=>rank[a.severity]-rank[b.severity]);return {venue,alerts,skipped,review_required:true};
}
