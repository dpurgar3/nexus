begin;
create table public.remaris_import_batches (
 id uuid primary key default gen_random_uuid(),
 owner_id uuid not null references auth.users(id) on delete cascade,
 venue text not null check (venue in ('NOMAD','Artist')),
 report_type text not null check (report_type in ('prices','stock','recipes','taxes','kpd','sales','invoices')),
 source_hash text not null check (source_hash ~ '^[0-9a-f]{64}$'),
 created_at timestamptz not null default now(),
 unique(owner_id,venue,report_type,source_hash),
 unique(id,owner_id,venue)
);
create table public.remaris_import_candidates (
 id uuid primary key default gen_random_uuid(),
 batch_id uuid not null,
 owner_id uuid not null,
 venue text not null,
 source_row integer not null check(source_row>0),
 payload jsonb not null check(jsonb_typeof(payload)='object' and octet_length(payload::text)<=32768),
 foreign key(batch_id,owner_id,venue) references public.remaris_import_batches(id,owner_id,venue) on delete cascade,
 unique(batch_id,source_row)
);
alter table public.remaris_import_batches enable row level security;
alter table public.remaris_import_candidates enable row level security;
revoke all on public.remaris_import_batches,public.remaris_import_candidates from public,anon,authenticated;
grant select,insert,delete on public.remaris_import_batches,public.remaris_import_candidates to authenticated;
create policy batch_owner on public.remaris_import_batches for all to authenticated using (owner_id=(select auth.uid()) and (select auth.jwt()->'app_metadata'->>'nexus_ai_access')='true') with check (owner_id=(select auth.uid()) and (select auth.jwt()->'app_metadata'->>'nexus_ai_access')='true');
create policy candidate_owner on public.remaris_import_candidates for all to authenticated using (owner_id=(select auth.uid()) and (select auth.jwt()->'app_metadata'->>'nexus_ai_access')='true') with check (owner_id=(select auth.uid()) and (select auth.jwt()->'app_metadata'->>'nexus_ai_access')='true');
commit;
