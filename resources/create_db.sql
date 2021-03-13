create table if not exists tag_syncs 
(
    id serial not null
        constraint tag_syncs_pk
            primary key,
    date timestamp with time zone not null,
    tag varchar not null,
    html text
);

alter table tag_syncs owner to "todoist-sync";

create unique index if not exists tag_syncs_id_uindex
    on tag_syncs (id);

