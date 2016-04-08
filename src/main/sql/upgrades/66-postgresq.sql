insert into extra_prop_entry
select nextval('extra_prop_entry_seq_pk'),
       resource_id,
       0,
       null,
       'recursive-listing',
       'true',
       null,
       null
from vortex_resource
where is_collection = 'Y'
and resource_id not in
    (select distinct(resource_id) from extra_prop_entry
        where name_space is null and name = 'recursive-listing');
