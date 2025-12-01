create table if not exists credentials
(
    id       uuid unique primary key not null,
    version  integer                 not null,
    username varchar(100) unique     not null,
    password varchar(100)            not null,
    enabled  boolean                 not null
);

create table if not exists auth_users
(
    id           uuid unique primary key not null,
    version      integer                 not null,
    phone_number varchar(11)             not null unique,
    joined_at      timestamp       not null,
    enabled      boolean                 not null default false
);

create table if not exists auth_roles
(
    id      uuid unique primary key not null,
    version integer                 not null,
    name    varchar(50)             not null unique
);

create table if not exists auth_permissions
(
    id              uuid unique primary key not null,
    version         integer                 not null,
    name            varchar(50)             not null unique,
    created_at      timestamp               not null,
    last_updated_at timestamp               not null
);

create table if not exists auth_user_roles
(
    user_id uuid not null references auth_users(id),
    role_id uuid not null references auth_roles(id),
    primary key (user_id,role_id)
);

create table if not exists auth_role_permissions
(
    role_id uuid not null references auth_roles(id),
    permission_id uuid not null references auth_permissions(id),
    primary key (role_id,permission_id)
);

create table if not exists auth_refresh_token
(
    id             uuid unique primary key not null,
    version        integer                 not null,
    hashed_token   char(44)                not null,
    device_id_hash varchar(100)            not null,
    expires_at     timestamp               not null,
    user_id        uuid                    not null references auth_users (id)
);

create table if not exists auth_device_binding
(
    user_id         uuid            not null references auth_users (id),
    device_id_hash  char(44) unique not null,
    version         integer         not null,
    user_agent_hash char(44)        not null,
    ip_hash         char(44)        not null,
    jti_hash        char(44)        not null,
    created_at      timestamp       not null,
    last_used_at    timestamp       not null,
    primary key (user_id, device_id_hash)
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
    description     text                not null,
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




create table if not exists provinces
(
    id      uuid primary key not null,
    version int              not null,
    name    varchar(100)     not null
);

create table if not exists cities
(
    id          uuid primary key not null,
    version     int              not null,
    name        varchar(100)     not null,
    province_id uuid             not null references provinces (id)
);

create table if not exists locations
(
    id          uuid primary key not null,
    version     integer          not null,
    latitude    decimal(9, 6)    not null,
    longitude   decimal(9, 6)    not null,
    province_id uuid             not null references provinces (id),
    city_id     uuid             not null references cities (id)
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

create table if not exists advertisement_image_metadata
(
    id               uuid primary key not null,
    version          integer          not null,
    url              varchar(100),
    is_main          boolean          not null,
    status           varchar(50)      not null,
    inserted_at      timestamp        not null,
    advertisement_id uuid             not null REFERENCES advertisements (id)
);


create index if not exists index_catalog_name on catalogs (name);
create index if not exists index_catalog_slug on catalogs (slug);

create index if not exists index_category_name on categories (name);
create index if not exists index_category_slug on categories (slug);
create index if not exists index_category_status on categories (category_status);

create index if not exists index_category_closure_parent_child on category_closure (parent_id, child_id);
create index if not exists index_category_closure_descendant_depth on category_closure (child_id, depth);
create index if not exists index_category_closure_descendant_depth1 on category_closure (child_id) where depth = 1;

create index if not exists index_city_name on cities (province_id);
create index if not exists index_city_province_id on cities (province_id);

create index if not exists index_province_name on provinces (name);

create index if not exists index_location_province_id on locations (province_id);
create index if not exists index_location_city_id on locations (city_id);

create index if not exists index_image_advertisement_id on advertisement_image_metadata (advertisement_id);
-- create unique index if not exists one_main_image_per_ad  ON advertisement_image_metadata (advertisement_id) WHERE is_main = true;

create index if not exists index_advertisement_title on advertisements (title);
create index if not exists index_advertisement_price on advertisements (price);
create index if not exists index_advertisement_type on advertisements (advertisement_type);
create index if not exists index_advertisement_status on advertisements (advertisement_status);
create index if not exists index_advertisement_attributes on advertisements USING gin (attributes jsonb_path_ops);
create index if not exists index_advertisement_location_id on advertisements (location_id);
create index if not exists index_advertisement_category_id on advertisements (category_id);
create index if not exists index_advertisement_owner_id on advertisements (owner_id);
create index if not exists index_advertisement_owner_id_title on advertisements (owner_id,title);
