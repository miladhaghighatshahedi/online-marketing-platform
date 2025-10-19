create table if not exists credentials
(
    id       uuid unique primary key not null,
    version  integer                 not null,
    username varchar(100) unique     not null,
    password varchar(100)            not null,
    enabled  boolean                 not null
);

create table if not exists profiles
(
    id              uuid unique primary key not null,
    version         integer                 not null,
    name            varchar(100) unique     not null,
    about           text,
    profile_type    varchar(50)             not null,
    profile_status  varchar(50)             not null,
    activation_date timestamp               not null,
    credential      uuid unique references credentials (id)
);

create table if not exists catalogs
(
    id          uuid primary key    not null,
    version     integer             not null,
    name        varchar(150)        not null,
    description text,
    slug        varchar(160) unique not null,
    created_at  timestamp           not null,
    updated_at  timestamp,
    image_url   varchar(150)
);

create table if not exists categories
(
    id              uuid primary key    not null,
    version         integer             not null,
    name            VARCHAR(150)        not null,
    typedescription text,
    created_at      timestamp           not null,
    updated_at      timestamp,
    category_status varchar(50)         not null,
    slug            varchar(160) unique not null,
    image_url       varchar(300),
    catalog_id      uuid                not null REFERENCES catalogs (id)
);

create table if not exists category_closure
(
    parent_id uuid             not null references categories (id),
    child_id  uuid             not null references categories (id),
    depth     integer          not null,
    primary key (parent_id,child_id)
);

create table if not exists advertisements
(
    id                   uuid primary key not null,
    version              integer          not null,
    title                varchar(100)     not null,
    description          text,
    price                numeric(15, 2)   not null,
    advertisement_type   varchar(50)      not null,
    advertisement_status varchar(50)      not null,
    attributes           JSONB            NOT NULL DEFAULT '{}'::jsonb,
    inserted_at          timestamp        not null,
    updated_at           timestamp,
    location_id          uuid             not null REFERENCES locations (id),
    category_id          uuid             not null REFERENCES categories (id),
    owner_id             uuid             not null REFERENCES credentials (id)
);

create table if not exists locations
(
    id       uuid primary key not null,
    version  integer          not null,
    province text             not null,
    city     text             not null
);


create index if not exists index_catalog_name on catalogs(name);
create index if not exists index_catalog_slug on catalogs(slug);

create index if not exists index_category_name on categories(name);
create index if not exists index_category_slug on categories(slug);
create index if not exists index_category_status on categories(category_status);

create index if not exists index_category_closure_parent_child on category_closure(parent_id, child_id);
create index if not exists index_category_closure_descendant_depth on category_closure(child_id, depth);
create index if not exists index_category_closure_descendant_depth1 on category_closure(child_id) where depth = 1;

create index if not exists index_advertisement_title on advertisements(title);
create index if not exists index_advertisement_price on advertisements(price);
create index if not exists index_advertisement_attributes on advertisements USING gin (attributes jsonb_path_ops);


