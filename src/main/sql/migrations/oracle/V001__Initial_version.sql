-----------------------------------------------------------------------------
-- VORTEX DDL
-----------------------------------------------------------------------------

-----------------------------------------------------------------------------
-- resource
-----------------------------------------------------------------------------

drop sequence vortex_resource_seq_pk;

create sequence vortex_resource_seq_pk increment by 1 start with 1000;

drop table vortex_resource cascade constraints;

create table vortex_resource
(
    resource_id number not null,
    prev_resource_id number null, -- used when copying/moving
    uri VARCHAR2 (2048) not null,
    depth number not null,
    creation_time timestamp not null,
    created_by varchar2 (64) not null,
    content_last_modified timestamp not null,
    properties_last_modified timestamp not null,
    last_modified timestamp not null,
    content_modified_by varchar2 (64) not null,
    properties_modified_by varchar2 (64) not null,
    modified_by varchar2 (64) not null,
    resource_owner varchar2 (64) not null,
    content_type varchar2 (128) null,
    content_length number null,
    resource_type varchar2 (256) not null,
    character_encoding varchar2 (64) null,
    guessed_character_encoding varchar2 (64) null,
    user_character_encoding varchar2 (64) null,
    is_collection char(1) default 'N' not null,
    acl_inherited_from number null,
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
-- vortex_tmp - Auxiliary temp-table used to hold lists of URIs or resource-
--              IDs
-- TODO: column 'resource_id' should be renamed to 'generic_id'
-----------------------------------------------------------------------------
drop sequence vortex_tmp_session_id_seq;
create sequence vortex_tmp_session_id_seq increment by 1 start with 1000;

drop table vortex_tmp;
create table vortex_tmp (
    session_id number,
    resource_id number,
    uri varchar2(2048)
);

create index vortex_tmp_index on vortex_tmp(uri);




-----------------------------------------------------------------------------
-- vortex_lock
-----------------------------------------------------------------------------
drop sequence vortex_lock_seq_pk;

create sequence vortex_lock_seq_pk increment by 1 start with 1000;

drop table vortex_lock cascade constraints;

create table vortex_lock
(
    lock_id number not null,
    lock_type varchar2 (64) default 'EXCLUSIVE' not null,
    resource_id number not null,
    token varchar2 (128) not null,
    lock_owner varchar2 (128) not null,
    lock_owner_info varchar2 (128) not null,
    depth char (1) default '1' not null,
    timeout timestamp not null
);

alter table vortex_lock
    add constraint vortex_lock_PK
primary key (lock_id);

alter table vortex_lock
    add constraint vortex_lock_FK_1 foreign key (resource_id)
    references vortex_resource (resource_id) on delete cascade;
;

create index vortex_lock_index1 on vortex_lock(resource_id);
create index vortex_lock_index2 on vortex_lock(timeout);

-----------------------------------------------------------------------------
-- action_type
-----------------------------------------------------------------------------
drop table action_type cascade constraints;

create table action_type
(
    action_type_id number not null,
    name varchar2 (64) not null
);

alter table action_type
    add constraint action_type_PK
primary key (action_type_id);


-----------------------------------------------------------------------------
-- acl_entry
-----------------------------------------------------------------------------
drop sequence acl_entry_seq_pk;

create sequence acl_entry_seq_pk increment by 1 start with 1000;

drop table acl_entry cascade constraints;

create table acl_entry
(
    acl_entry_id number not null,
    resource_id number not null,
    action_type_id number not null,
    user_or_group_name varchar2 (64) not null,
    is_user char (1) default 'Y' not null,
    granted_by_user_name varchar2 (64) not null,
    granted_date date not null
);

alter table acl_entry
    add constraint acl_entry_PK
primary key (acl_entry_id);

alter table acl_entry
    add constraint acl_entry_FK_1 foreign key (resource_id)
    references vortex_resource (resource_id) on delete cascade
;

-- Unnecessary constraint b/c table action_type is not really in use from
-- application's point of view:
-- alter table acl_entry
--     add constraint acl_entry_FK_2 foreign key (action_type_id)
--     references action_type (action_type_id)
-- ;

create index acl_entry_index1 on acl_entry(resource_id);


-----------------------------------------------------------------------------
-- prop_type
-----------------------------------------------------------------------------
drop table prop_type cascade constraints;

create table prop_type
(
    prop_type_id number not null,
    prop_type_name varchar2 (64) not null
);

alter table prop_type
    add constraint prop_type_PK primary key (prop_type_id);


-----------------------------------------------------------------------------
-- extra_prop_entry
-----------------------------------------------------------------------------
drop sequence extra_prop_entry_seq_pk;

create sequence extra_prop_entry_seq_pk increment by 1 start with 1000;

drop table extra_prop_entry cascade constraints;

create table extra_prop_entry
(
    extra_prop_entry_id number not null,
    resource_id number not null,
    prop_type_id number default 0 not null,
    name_space varchar2 (128) null,
    name varchar2 (64) not null,
    value varchar2 (2048) not null,
    binary_content BLOB,
    binary_mimetype varchar2 (64),
    is_inheritable char(1) default 'N' not null
);

alter table extra_prop_entry
    add constraint extra_prop_entry_PK
primary key (extra_prop_entry_id);

alter table extra_prop_entry
    add constraint extra_prop_entry_FK_1 foreign key (resource_id)
    references vortex_resource (resource_id) on delete cascade
;

-- Unnecessary constraint b/c table prop_type is not really in use from
-- application's point of view:
-- alter table extra_prop_entry
--     add constraint extra_prop_entry_FK_2 foreign key (prop_type_id)
--     references prop_type(prop_type_id)
-- ;

create index extra_prop_entry_index1 on extra_prop_entry(resource_id);
create index extra_prop_entry_index2 on extra_prop_entry(is_inheritable);

-----------------------------------------------------------------------------
-- changelog_entry
-----------------------------------------------------------------------------
drop sequence changelog_entry_seq_pk;

-- NB, this sequence needs explicit ordering requirement for Oracle RAC:
create sequence changelog_entry_seq_pk increment by 1 start with 1000 cache 60 order;

drop table changelog_entry cascade constraints;

create table changelog_entry
(
    changelog_entry_id number not null,
    logger_id number not null,
    logger_type number not null,
    operation varchar2 (128) NULL,
    timestamp date not null,
    uri varchar2 (2048) not null,
    resource_id number,
    is_collection char(1) default 'N' not null
);

alter table changelog_entry
    add constraint changelog_entry_PK
primary key (changelog_entry_id);

/* DROP INDEX changelog_entry_index1; */

create unique index changelog_entry_index1
    on changelog_entry (uri, changelog_entry_id);


-----------------------------------------------------------------------------
-- simple_content_revision
-----------------------------------------------------------------------------

drop sequence simple_content_revision_seq_pk;

create sequence simple_content_revision_seq_pk increment by 1 start with 1000;

drop table simple_content_revision cascade constraints;

create table simple_content_revision (
    id number not null,
    resource_id number not null,
    revision_name varchar2 (256) not null,
    user_id varchar2 (256) not null,
    timestamp timestamp not null,
    checksum varchar2 (256) not null
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
drop sequence revision_acl_entry_seq_pk;

create sequence revision_acl_entry_seq_pk increment by 1 start with 1000;

drop table revision_acl_entry cascade constraints;

create table revision_acl_entry
(
    id number not null,
    revision_id number not null,
    action_type_id number not null,
    user_or_group_name varchar2 (64) not null,
    is_user char (1) default 'Y' not null,
    granted_by_user_name varchar2 (64) not null,
    granted_date date not null
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

drop sequence vortex_comment_seq_pk;

create sequence vortex_comment_seq_pk increment by 1 start with 1000;

drop table vortex_comment;

create table vortex_comment
(
    id number not null,
    resource_id number not null,
    author varchar2 (64) not null,
    time timestamp not null,
    title varchar2 (2048) null,
    content clob not null,
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
    vortex_resource_seq_pk.nextval,
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
select extra_prop_entry_seq_pk.nextval,
       resource_id,
       0,
       null,
       'title',
       '/',
       null,
       null,
       'N'
from vortex_resource where uri = '/';

-- Insert publish-date prop for root resource
insert into extra_prop_entry 
select extra_prop_entry_seq_pk.nextval,
       resource_id,
       3,
       null,
       'publish-date',
       to_char(current_timestamp, 'YYYY-MM-DD HH24:MI:SS'),
       null,
       null,
       'N'
from vortex_resource where uri = '/';

-- Insert published prop for root resource
insert into extra_prop_entry 
select extra_prop_entry_seq_pk.nextval,
       resource_id,
       4,
       null,
       'published',
       'true',
       null,
       null,
       'N'
from vortex_resource where uri = '/';


-- (pseudo:all (read))

insert into acl_entry (
    acl_entry_id,
    resource_id,
    action_type_id,
    user_or_group_name,
    is_user,
    granted_by_user_name,
    granted_date)
values (
    acl_entry_seq_pk.nextval,
    vortex_resource_seq_pk.currval,
    1,
    'pseudo:all',
    'Y',
    'vortex@localhost',
    sysdate
);


-- (vortex@localhost, all)

insert into acl_entry (
    acl_entry_id,
    resource_id,
    action_type_id,
    user_or_group_name,
    is_user,
    granted_by_user_name,
    granted_date)
values (
    acl_entry_seq_pk.nextval,
    vortex_resource_seq_pk.currval,
    3,
    'vortex@localhost',
    'Y',
    'vortex@localhost',
    sysdate
);

-----------------------------------------------------------------------------
-- deleted_resource (trash can)
-----------------------------------------------------------------------------

drop sequence deleted_resource_seq_pk;

create sequence deleted_resource_seq_pk increment by 1 start with 1000;

drop table deleted_resource cascade constraints;

create table deleted_resource
(
  id number not null,
  resource_trash_uri VARCHAR2 (2048) not null,
  parent_id number not null,
  deleted_by VARCHAR2 (64) not null,
  deleted_time timestamp not null,
  was_inherited_acl char(1) default 'N' not null
);

alter table deleted_resource
  add constraint deleted_resource_PK primary key (id);

-- identifier "deleted_resource_parent_id_index" is too long, causes ORA-00927.
-- Hence the short name.
create index deleted_resource_parent_id_idx on deleted_resource(parent_id);
