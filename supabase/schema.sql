-- =============================================================
-- MoniSom — Schema inicial + dados de teste (Supabase/Postgres)
-- =============================================================
-- COMO USAR:
--  1) No painel do Supabase, vá em Authentication > Users >
--     "Add user" e crie os DOIS professores com estes e-mails
--     (a senha você escolhe):
--        - professor1@monisom.com
--        - professor2@monisom.com
--     IMPORTANTE: desmarque "Auto Confirm User" (ou confirme o
--     e-mail depois) para que o login funcione.
--  2) Abra SQL Editor (Dashboard > SQL Editor > New query),
--     cole TODO este arquivo e clique em RUN.
--  3) No app, preencha SupabaseConfig.kt com a URL e a anon key.
-- =============================================================

create extension if not exists "pgcrypto";

-- ---------------------------------------------------------
-- Tabelas
-- ---------------------------------------------------------

create table if not exists public.schools (
    id uuid primary key default gen_random_uuid(),
    name text not null,
    created_at timestamptz not null default now()
);

create table if not exists public.teachers (
    id uuid primary key references auth.users(id) on delete cascade,
    name text not null,
    email text not null,
    created_at timestamptz not null default now()
);

-- Turmas (uma turma pertence a uma escola)
create table if not exists public.classes (
    id uuid primary key default gen_random_uuid(),
    school_id uuid not null references public.schools(id) on delete cascade,
    name text not null,
    created_at timestamptz not null default now()
);

-- Salas (físicas, usadas futuramente na medição acústica)
create table if not exists public.rooms (
    id uuid primary key default gen_random_uuid(),
    school_id uuid not null references public.schools(id) on delete cascade,
    name text not null,
    created_at timestamptz not null default now()
);

-- Relaciona professor <-> escola
create table if not exists public.school_teachers (
    school_id uuid not null references public.schools(id) on delete cascade,
    teacher_id uuid not null references public.teachers(id) on delete cascade,
    primary key (school_id, teacher_id)
);

-- Relaciona professor <-> turma
-- (escola + turma + professor = ambiente de monitoramento)
create table if not exists public.class_teachers (
    class_id uuid not null references public.classes(id) on delete cascade,
    teacher_id uuid not null references public.teachers(id) on delete cascade,
    primary key (class_id, teacher_id)
);

create table if not exists public.monitorings (
    id uuid primary key default gen_random_uuid(),
    class_id uuid not null references public.classes(id) on delete cascade,
    teacher_id uuid not null references public.teachers(id) on delete cascade,
    started_at timestamptz not null,
    finished_at timestamptz not null,
    perception int not null check (perception between 0 and 5),
    stress int not null check (stress between 0 and 5),
    created_at timestamptz not null default now()
);

create index if not exists monitorings_class_teacher_idx
    on public.monitorings (class_id, teacher_id, created_at desc);

-- ---------------------------------------------------------
-- Row Level Security
-- ---------------------------------------------------------

alter table public.schools enable row level security;
alter table public.teachers enable row level security;
alter table public.classes enable row level security;
alter table public.rooms enable row level security;
alter table public.school_teachers enable row level security;
alter table public.class_teachers enable row level security;
alter table public.monitorings enable row level security;

drop policy if exists "teachers_read_own_profile" on public.teachers;
create policy "teachers_read_own_profile"
    on public.teachers for select
    using (auth.uid() = id);

drop policy if exists "authenticated_read_schools" on public.schools;
create policy "authenticated_read_schools"
    on public.schools for select
    using (auth.role() = 'authenticated');

drop policy if exists "authenticated_read_classes" on public.classes;
create policy "authenticated_read_classes"
    on public.classes for select
    using (auth.role() = 'authenticated');

drop policy if exists "authenticated_read_rooms" on public.rooms;
create policy "authenticated_read_rooms"
    on public.rooms for select
    using (auth.role() = 'authenticated');

drop policy if exists "teachers_read_own_school_links" on public.school_teachers;
create policy "teachers_read_own_school_links"
    on public.school_teachers for select
    using (teacher_id = auth.uid());

drop policy if exists "teachers_read_own_class_links" on public.class_teachers;
create policy "teachers_read_own_class_links"
    on public.class_teachers for select
    using (teacher_id = auth.uid());

drop policy if exists "teachers_read_own_monitorings" on public.monitorings;
create policy "teachers_read_own_monitorings"
    on public.monitorings for select
    using (teacher_id = auth.uid());

drop policy if exists "teachers_insert_own_monitorings" on public.monitorings;
create policy "teachers_insert_own_monitorings"
    on public.monitorings for insert
    with check (teacher_id = auth.uid());

-- ---------------------------------------------------------
-- Dados de teste (seed)
-- ---------------------------------------------------------

-- 2 escolas
insert into public.schools (id, name) values
    ('a0000000-0000-4000-8000-000000000001', 'Escola Municipal Monteiro Lobato'),
    ('a0000000-0000-4000-8000-000000000002', 'Escola Estadual Cecília Meireles')
on conflict (id) do nothing;

-- 2 professores (vinculados aos usuários criados no Auth)
insert into public.teachers (id, name, email)
select id,
       case when email = 'professor1@monisom.com' then 'Professor Um'
            else 'Professor Dois' end,
       email
from auth.users
where email in ('professor1@monisom.com', 'professor2@monisom.com')
on conflict (id) do nothing;

-- 4 salas por escola
insert into public.rooms (id, school_id, name) values
    ('b0000000-0000-4000-8000-000000000011', 'a0000000-0000-4000-8000-000000000001', 'Sala 101'),
    ('b0000000-0000-4000-8000-000000000012', 'a0000000-0000-4000-8000-000000000001', 'Sala 102'),
    ('b0000000-0000-4000-8000-000000000013', 'a0000000-0000-4000-8000-000000000001', 'Sala 103'),
    ('b0000000-0000-4000-8000-000000000014', 'a0000000-0000-4000-8000-000000000001', 'Sala 104'),
    ('b0000000-0000-4000-8000-000000000021', 'a0000000-0000-4000-8000-000000000002', 'Sala 201'),
    ('b0000000-0000-4000-8000-000000000022', 'a0000000-0000-4000-8000-000000000002', 'Sala 202'),
    ('b0000000-0000-4000-8000-000000000023', 'a0000000-0000-4000-8000-000000000002', 'Sala 203'),
    ('b0000000-0000-4000-8000-000000000024', 'a0000000-0000-4000-8000-000000000002', 'Sala 204')
on conflict (id) do nothing;

-- 3 turmas por escola
insert into public.classes (id, school_id, name) values
    ('c0000000-0000-4000-8000-000000000011', 'a0000000-0000-4000-8000-000000000001', 'Turma 1A'),
    ('c0000000-0000-4000-8000-000000000012', 'a0000000-0000-4000-8000-000000000001', 'Turma 1B'),
    ('c0000000-0000-4000-8000-000000000013', 'a0000000-0000-4000-8000-000000000001', 'Turma 1C'),
    ('c0000000-0000-4000-8000-000000000021', 'a0000000-0000-4000-8000-000000000002', 'Turma 2A'),
    ('c0000000-0000-4000-8000-000000000022', 'a0000000-0000-4000-8000-000000000002', 'Turma 2B'),
    ('c0000000-0000-4000-8000-000000000023', 'a0000000-0000-4000-8000-000000000002', 'Turma 2C')
on conflict (id) do nothing;

-- Ambos os professores atuam nas duas escolas
insert into public.school_teachers (school_id, teacher_id)
select s.id, t.id
from public.schools s
cross join public.teachers t
on conflict do nothing;

-- Ambos os professores estão cadastrados em todas as turmas
insert into public.class_teachers (class_id, teacher_id)
select c.id, t.id
from public.classes c
cross join public.teachers t
on conflict do nothing;

-- Aviso caso algum professor não tenha sido criado no Auth
do $$
declare
    n int;
begin
    select count(*) into n from public.teachers;
    if n < 2 then
        raise notice 'ATENÇÃO: apenas % professor(es) vinculado(s). '
                      'Crie os usuários professor1@monisom.com e professor2@monisom.com '
                      'em Authentication > Users e rode este script novamente.', n;
    end if;
end $$;
