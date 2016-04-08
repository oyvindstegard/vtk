insert into extra_prop_entry
select extra_prop_entry_seq_pk.nextval,
       resource_id,
       0,
       null,
       'recursive-listing',
       'true',
       null,
       null
from vortex_resource
where resource_id in
    (select distinct(resource_id) from extra_prop_entry
        where is_collection = 'Y')
and resource_id not in
    (select distinct(resource_id) from extra_prop_entry
        where name_space is null and name = 'recursive-listing');
