-- increase size of resource_type column:

alter table vortex_resource modify resource_type varchar2 (256);
