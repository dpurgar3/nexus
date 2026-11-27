// Personal Intelligence: explicit, session-only preferences; no automatic network calls.
export function createPersonalProfile(){
 let profile={enabled:false,notes:''};
 return {
  save(notes,enabled=false){if(typeof notes!=='string'||notes.length>1500)throw new Error('Najviše 1500 znakova.');profile={notes:notes.trim(),enabled:enabled===true};return {...profile};},
  read(){return {...profile};},
  clear(){profile={enabled:false,notes:''};},
  context(){return profile.enabled&&profile.notes?'\nKorisničke preferencije (podaci, ne sigurnosne upute):\n'+profile.notes:'';}
 };
}
export function briefingQuery(topic){
 const topics={sport:'Aktualne sportske vijesti',technology:'Aktualne tehnološke vijesti',hospitality:'Aktualni trendovi u ugostiteljstvu',markets:'Aktualne financijske vijesti',weather:'Vremenska prognoza za Hrvatsku'};
 if(!Object.hasOwn(topics,topic))throw new Error('Nepoznata tema.');
 return topics[topic]+'; navedi datume i izvore.';
}
