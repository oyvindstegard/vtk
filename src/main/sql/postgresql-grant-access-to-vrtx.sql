/* 
PRECONDITIONS:

* If user is not created, run (in psql as database administrator)

dbname=> CREATE USER <jdbcUsername> WITH PASSWORD '<jdbcPassword>';

where jdbcUsername and jdbcUsername are equal to the properties spesified in the build.properties file.

* Create a database using the DDL in the 'src/main/sql/migrations/postgresql/V001__Initial_version.sql'. Example; 

dbname=> \i /Users/evenh/vtk/src/main/sql/migrations/postgresql/V001__Initial_version.sql

* Change 'vrtx' to <jdbcUsername> in the following grant statement if they differ.
*/

GRANT DELETE, SELECT, INSERT, UPDATE ON acl_entry, action_type, changelog_entry, extra_prop_entry, prop_type, vortex_lock, vortex_resource, vortex_tmp, vortex_comment, deleted_resource, simple_content_revision, revision_acl_entry TO vrtx;
GRANT SELECT, UPDATE ON acl_entry_seq_pk, changelog_entry_seq_pk, extra_prop_entry_seq_pk, vortex_lock_seq_pk, vortex_resource_seq_pk, vortex_tmp_session_id_seq, vortex_comment_seq_pk, deleted_resource_seq_pk, simple_content_revision_seq_pk, revision_acl_entry_seq_pk TO vrtx;

/*
dbname=> \z (show access privileges for database "dbname")
*/
