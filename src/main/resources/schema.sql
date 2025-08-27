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

create table if not exists products
(
    id             uuid primary key not null,
    version        integer          not null,
    name           varchar(100)     not null,
    description    text,
    inserted_at    timestamp        not null,
    updated_at     timestamp,
    price          numeric(15, 2)   not null,
    product_status varchar(50)      not null,
    credential     uuid references credentials (id)
);

create table if not exists catalogs
(
    id          uuid primary key    not null,
    version     integer             not null,
    name        varchar(150)        not null,
    description text,
    slug        varchar(160) unique not null
);

create table if not exists categories
(
    id              uuid primary key    not null,
    version         integer             not null,
    name            VARCHAR(150)        not null,
    description     text,
    created_at      timestamp           not null,
    updated_at      timestamp,
    category_status varchar(50)         not null,
    slug            varchar(160) unique not null,
    category_id uuid references categories (id),
    catalog_id  uuid not null REFERENCES catalogs (id)
);

create table if not exists product_category
(
    productId  uuid not null references products (id),
    categoryId uuid not null references categories (id),
    primary key (productId, categoryId)
);

create index if not exists index_profiles_credential on products (credential);
create index if not exists index_products_credential on products (credential);

