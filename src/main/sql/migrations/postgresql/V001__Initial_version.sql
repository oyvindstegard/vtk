-----------------------------------------------------------------------------
-- VORTEX DDL 
-----------------------------------------------------------------------------

-----------------------------------------------------------------------------
-- vortex_resource
-----------------------------------------------------------------------------
create sequence vortex_resource_seq_pk INCREMENT 1 START 1000;

create table vortex_resource
(
    resource_id int not null,
    prev_resource_id int null, -- used when copying/moving
    uri varchar (2048) not null,
    depth int not null,
    creation_time timestamp not null,
    created_by varchar(64) not null,
    content_last_modified timestamp not null,
    properties_last_modified timestamp not null,
    last_modified timestamp not null,
    content_modified_by varchar (64) not null,
    properties_modified_by varchar (64) not null,
    modified_by varchar (64) not null,
    resource_owner varchar (64) not null,
    content_type varchar (128) null,
    content_length bigint null, -- NULL for collections.
    resource_type varchar(256) not null,
    character_encoding varchar (64) null,
    guessed_character_encoding varchar (64) null,
    user_character_encoding varchar (64) null,
    is_collection CHAR(1) default 'N' not null,
    acl_inherited_from int null,
    constraint resource_uri_index unique (uri)
);


alter table vortex_resource
      add constraint vortex_resource_PK primary key (resource_id);

alter table vortex_resource
      add constraint vortex_resource_FK foreign key (acl_inherited_from)
          references vortex_resource (resource_id);

create index vortex_resource_acl_index on vortex_resource(acl_inherited_from);

create index vortex_resource_d_u_index on vortex_resource(depth, uri);

-----------------------------------------------------------------------------
-- vortex_tmp 
-- Auxiliary temp-table used to hold lists of URIs or resource-IDs

-- TODO: column 'resource_id' should be renamed to 'generic_id'
-----------------------------------------------------------------------------
create sequence vortex_tmp_session_id_seq increment 1 start 1000;

create table vortex_tmp (
    session_id integer,
    resource_id integer,
    uri varchar(2048)
);

create index vortex_tmp_index on vortex_tmp(uri);


-----------------------------------------------------------------------------
-- vortex_lock
-----------------------------------------------------------------------------
create sequence vortex_lock_seq_pk INCREMENT 1 START 1000;

create table vortex_lock
(
    lock_id int not null,
    lock_type varchar(64) not null default 'EXCLUSIVE',
    resource_id int not null,
    token varchar (128) not null,
    lock_owner varchar (128) not null,
    lock_owner_info varchar (128) not null,
    depth char (1) default '1' not null,
    timeout timestamp not null
);

alter table vortex_lock
    add constraint vortex_lock_PK
primary key (lock_id);

alter table vortex_lock
    add constraint vortex_lock_FK_1 foreign key (resource_id)
    references vortex_resource (resource_id) on delete cascade;

create index vortex_lock_index1 on vortex_lock(resource_id);
create index vortex_lock_index2 on vortex_lock(timeout);

-----------------------------------------------------------------------------
-- action_type
-----------------------------------------------------------------------------
create table action_type
(
    action_type_id int not null,
    name VARCHAR (64) not null
);

alter table action_type
    add constraint action_type_PK
primary key (action_type_id);


-----------------------------------------------------------------------------
-- acl_entry
-----------------------------------------------------------------------------
create sequence acl_entry_seq_pk increment 1 start 1000;

create table acl_entry
(
    acl_entry_id int not null,
    resource_id int not null,
    action_type_id int not null,
    user_or_group_name varchar (64) not null,
    is_user char (1) default 'Y' not null,
    granted_by_user_name varchar (64) not null,
    granted_date timestamp not null
);

alter table acl_entry
    add constraint acl_entry_PK
primary key (acl_entry_id);

alter table acl_entry
    add constraint acl_entry_FK_1 foreign key (resource_id)
    references vortex_resource (resource_id) on delete cascade;


-- Unnecessary constraint, table not really in use:
-- alter table acl_entry
--     add constraint acl_entry_FK_2 foreign key (action_type_id)
--     references action_type (action_type_id)
-- ;

create index acl_entry_index1 on acl_entry(resource_id);


-----------------------------------------------------------------------------
-- prop_type
-----------------------------------------------------------------------------
create table prop_type
(
    prop_type_id int not null,
    prop_type_name varchar(64) not null
);

alter table prop_type
    add constraint prop_type_PK primary key (prop_type_id);


-----------------------------------------------------------------------------
-- extra_prop_entry
-----------------------------------------------------------------------------
create sequence extra_prop_entry_seq_pk increment 1 start 1000;

create table extra_prop_entry
(
    extra_prop_entry_id int not null,
    resource_id int not null,
    prop_type_id int default 0 not null,
    name_space varchar (128) null,
    name varchar (64) not null,
    value varchar (2048) not null,
    binary_content oid,
    binary_mimetype varchar (64),
    is_inheritable char(1) default 'N' not null
);

alter table extra_prop_entry
    add constraint extra_prop_entry_PK
primary key (extra_prop_entry_id);

alter table extra_prop_entry
    add constraint extra_prop_entry_FK_1 foreign key (resource_id)
    references vortex_resource (resource_id) on delete cascade;


-- Unnecessary constraint, table not really in use:
-- alter table extra_prop_entry
--     add constraint extra_prop_entry_FK_2 foreign key (prop_type_id)
--     references prop_type(prop_type_id)
-- ;

create index extra_prop_entry_index1 on extra_prop_entry(resource_id);
create index extra_prop_entry_index2 on extra_prop_entry(is_inheritable);


-----------------------------------------------------------------------------
-- changelog_entry
-----------------------------------------------------------------------------
create sequence changelog_entry_seq_pk increment 1 start 1000;

create table changelog_entry
(
    changelog_entry_id int not null,
    logger_id int not null,
    logger_type int not null,
    operation varchar (128) null,
    timestamp timestamp not null,
    uri varchar (2048) not null,
    resource_id int,
    is_collection char(1) default 'N' not null
);

alter table changelog_entry
   add constraint changelog_entry_PK
primary key (changelog_entry_id);

create unique index changelog_entry_index1
   on changelog_entry (uri, changelog_entry_id);


-----------------------------------------------------------------------------
-- simple_content_revision
-----------------------------------------------------------------------------
create sequence simple_content_revision_seq_pk increment 1 start 1000;

create table simple_content_revision (
    id int not null,
    resource_id int not null,
    revision_name varchar(256) not null,
    user_id varchar(256) not null,
    timestamp timestamp not null,
    checksum varchar(256) not null
);

alter table simple_content_revision
      add constraint content_revision_pk primary key (id);

alter table simple_content_revision
      add constraint content_revision_fk foreign key (resource_id)
          references vortex_resource (resource_id) on delete cascade;


create index simple_content_revision_index1 on simple_content_revision(resource_id);

-----------------------------------------------------------------------------
-- revision_acl_entry
-----------------------------------------------------------------------------
create sequence revision_acl_entry_seq_pk increment 1 start 1000;

create table revision_acl_entry
(
    id int not null,
    revision_id int not null,
    action_type_id int not null,
    user_or_group_name varchar (64) not null,
    is_user char (1) default 'Y' not null,
    granted_by_user_name varchar (64) not null,
    granted_date timestamp not null
);

alter table revision_acl_entry
    add constraint revision_acl_entry_PK
primary key (id);

alter table revision_acl_entry
    add constraint revision_acl_entry_FK_1 foreign key (revision_id)
    references simple_content_revision (id) on delete cascade;

create index revision_acl_entry_index1 on revision_acl_entry(revision_id);

-----------------------------------------------------------------------------
-- vortex_comment
-----------------------------------------------------------------------------
create sequence vortex_comment_seq_pk increment 1 start 1000;

create table vortex_comment
(
    id int not null,
    resource_id int not null,
    author varchar(64) not null,
    time timestamp not null,
    title varchar(2048) null,
    content text not null,
    approved char(1) default 'Y' not null
);

alter table vortex_comment
      add constraint vortex_comment_pk primary key (id);

alter table vortex_comment
      add constraint vortex_comment_fk foreign key (resource_id)
          references vortex_resource (resource_id) on delete cascade;

create index vortex_comment_index1 on vortex_comment(resource_id);


-----------------------------------------------------------------------------
-- initial application data
-----------------------------------------------------------------------------

-- Action types

insert into action_type (action_type_id, name) values (1, 'read');
insert into action_type (action_type_id, name) values (2, 'read-write');
insert into action_type (action_type_id, name) values (3, 'all');
insert into action_type (action_type_id, name) values (4, 'read-processed');
insert into action_type (action_type_id, name) values (5, 'create-with-acl');
insert into action_type (action_type_id, name) values (6, 'add-comment');
insert into action_type (action_type_id, name) values (7, 'read-write-unpublished');

-- Property value types
-- This data currently corresponds to definitions in 
-- vtk.repository.resourcetype.PropertyType
insert into prop_type (prop_type_id, prop_type_name) values (0, 'String');
insert into prop_type (prop_type_id, prop_type_name) values (1, 'Integer');
insert into prop_type (prop_type_id, prop_type_name) values (2, 'Long');
insert into prop_type (prop_type_id, prop_type_name) values (3, 'Date');
insert into prop_type (prop_type_id, prop_type_name) values (4, 'Boolean');
insert into prop_type (prop_type_id, prop_type_name) values (5, 'Principal');

-- root resource

insert into vortex_resource (
    resource_id,
    prev_resource_id,
    uri,
    depth,
    creation_time,
    created_by,
    content_last_modified,
    properties_last_modified,
    last_modified,
    content_modified_by,
    properties_modified_by,
    modified_by,
    resource_owner,
    content_type,
    character_encoding,
    is_collection,
    acl_inherited_from,
    content_length,
    resource_type)
values (
    nextval('vortex_resource_seq_pk'),
    null,
    '/',
    0,
    current_timestamp,
    'vortex@localhost',
    current_timestamp,
    current_timestamp,
    current_timestamp,
    'vortex@localhost',
    'vortex@localhost',
    'vortex@localhost',
    'vortex@localhost',
    null,
    null,
    'Y',
    null,
    null,
    'collection'
);

-- Insert title property for root resource:
insert into extra_prop_entry 
select nextval('extra_prop_entry_seq_pk'),
       resource_id,
       0,
       null,
       'title',
       '/',
       null,
       null
from vortex_resource where uri = '/';

-- Insert publish-date prop for root resource
insert into extra_prop_entry 
select nextval('extra_prop_entry_seq_pk'),
       resource_id,
       3,
       null,
       'publish-date',
       current_timestamp,
       null,
       null,
       'N'
from vortex_resource where uri = '/';

-- Insert published prop for root resource
insert into extra_prop_entry 
select nextval('extra_prop_entry_seq_pk'),
       resource_id,
       4,
       null,
       'published',
       'true',
       null,
       null,
       'N'
from vortex_resource where uri = '/';


-- (pseudo:all, read)

insert into acl_entry (
    acl_entry_id,
    resource_id,
    action_type_id,
    user_or_group_name,
    is_user,
    granted_by_user_name,
    granted_date)
values (
    nextval('acl_entry_seq_pk'),
    currval('vortex_resource_seq_pk'),
    1,
    'pseudo:all',
    'Y',
    'vortex@localhost',
    current_timestamp
);


-- (vortex@localhost, all)

insert into ACL_ENTRY (
    acl_entry_id,
    resource_id,
    action_type_id,
    user_or_group_name,
    is_user,
    granted_by_user_name,
    granted_date)
values (
    nextval('acl_entry_seq_pk'),
    currval('vortex_resource_seq_pk'),
    3,
    'vortex@localhost',
    'Y',
    'vortex@localhost',
    current_timestamp
);

-----------------------------------------------------------------------------
-- deleted_resource (trash can)
-----------------------------------------------------------------------------
create sequence deleted_resource_seq_pk INCREMENT 1 START 1000;

create table deleted_resource
(
  id int not null,
  resource_trash_uri varchar (2048) not null,
  parent_id int not null,
  deleted_by varchar(64) not null,
  deleted_time timestamp not null,
  was_inherited_acl CHAR(1) default 'N' not null
);

alter table deleted_resource
  add constraint deleted_resource_PK primary key (id);
