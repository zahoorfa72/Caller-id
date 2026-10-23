-- CallerSphere caller directory MVP.
-- Only vetted public business labels and sufficiently reviewed spam labels
-- should be inserted into caller_directory. Never seed private-person identities.
create extension if not exists pgcrypto;

create table if not exists public.caller_directory (
    id uuid primary key default gen_random_uuid(),
    phone_e164 text not null unique,
    label text not null,
    category text not null check (category in ('business', 'spam')),
    verified boolean not null default false,
    report_count integer not null default 0 check (report_count >= 0),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint caller_directory_phone_format check (phone_e164 ~ '^\+[1-9][0-9]{7,14}$')
);

create table if not exists public.caller_reports (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null,
    phone_e164 text not null,
    reason text not null check (reason in ('spam')),
    status text not null default 'pending' check (status in ('pending', 'reviewed', 'rejected')),
    created_at timestamptz not null default now(),
    constraint caller_reports_phone_format check (phone_e164 ~ '^\+[1-9][0-9]{7,14}$')
);

create table if not exists public.caller_lookup_events (
    id bigint generated always as identity primary key,
    user_id uuid not null,
    action text not null check (action in ('lookup', 'report_spam')),
    created_at timestamptz not null default now()
);

create index if not exists caller_lookup_events_user_created_idx
    on public.caller_lookup_events (user_id, created_at desc);
create index if not exists caller_reports_phone_created_idx
    on public.caller_reports (phone_e164, created_at desc);

alter table public.caller_directory enable row level security;
alter table public.caller_reports enable row level security;
alter table public.caller_lookup_events enable row level security;

-- Intentionally no client-facing RLS policies. Requests must pass through
-- the Edge Function, which authenticates the user and applies rate limits.
revoke all on public.caller_directory from anon, authenticated;
revoke all on public.caller_reports from anon, authenticated;
revoke all on public.caller_lookup_events from anon, authenticated;

grant usage on schema public to service_role;
grant all on public.caller_directory to service_role;
grant all on public.caller_reports to service_role;
grant all on public.caller_lookup_events to service_role;
