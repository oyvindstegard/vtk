-- increase size of resource_type column:

alter table vortex_resource alter column resource_type type varchar(256);
