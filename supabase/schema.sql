-- 1) Devices registered for manager notifications
create table if not exists public.manager_devices (
  device_id uuid primary key,
  name text,
  fcm_token text unique not null,
  platform text not null default 'android',
  is_active boolean not null default true,
  last_seen_at timestamptz not null default now(),
  created_at timestamptz not null default now()
);

-- 2) Calls / requests from tables
create type public.call_status as enum ('open', 'taken', 'cancelled');

create table if not exists public.table_calls (
  id uuid primary key default gen_random_uuid(),
  zone text not null,
  table_no int not null,
  status public.call_status not null default 'open',
  created_at timestamptz not null default now(),
  taken_at timestamptz,
  taken_by_device uuid references public.manager_devices(device_id),
  taken_by_name text
);

create index if not exists idx_table_calls_open on public.table_calls(status, created_at desc);

-- 3) Atomic claim (returns row if claim succeeds)
create or replace function public.claim_table_call(p_call_id uuid, p_device_id uuid, p_name text)
returns table (claimed boolean, status public.call_status)
language plpgsql
as $$
begin
  update public.table_calls
    set status = 'taken',
        taken_at = now(),
        taken_by_device = p_device_id,
        taken_by_name = p_name
  where id = p_call_id
    and status = 'open';

  if found then
    return query select true, 'taken'::public.call_status;
  else
    -- not claimed (already taken or missing)
    return query
      select false, coalesce((select status from public.table_calls where id = p_call_id), 'cancelled'::public.call_status);
  end if;
end;
$$;
