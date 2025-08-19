create table if not exists profiles
(
    id uuid unique primary key not null,
    version integer not null ,
    name varchar(100) not null ,
    about varchar(250),
    profile_type varchar(50) not null,
    profile_status varchar(50) not null,
    activation_date timestamp,
    credential uuid unique
);



