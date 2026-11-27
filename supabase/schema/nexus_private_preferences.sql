create table public.nexus_preferences (user_id uuid primary key references auth.users(id) on delete cascade, preferences jsonb not null default '{}'::jsonb check (jsonb_typeof(preferences)='object' and octet_length(preferences::text)<=16384));
alter table public.nexus_preferences enable row level security;
revoke all on public.nexus_preferences from public, anon, authenticated;
grant select,insert,update,delete on public.nexus_preferences to authenticated;
create policy own_select on public.nexus_preferences for select to authenticated using ((select auth.uid())=user_id);
create policy own_insert on public.nexus_preferences for insert to authenticated with check ((select auth.uid())=user_id);
create policy own_update on public.nexus_preferences for update to authenticated using ((select auth.uid())=user_id) with check ((select auth.uid())=user_id);
create policy own_delete on public.nexus_preferences for delete to authenticated using ((select auth.uid())=user_id);