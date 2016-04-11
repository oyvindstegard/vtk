-- Property 'recursive-listing' is now mandatory for collections:

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

-- The 'authors' property is multi-valued, and therefore has to be
-- removed (we don't support dead multi-value properties):

delete from extra_prop_entry where name_space is null and name = 'authors';

-- These properties have been promoted to the 'collection' resource type (null namespace):

update extra_prop_entry set name_space = null 
where name = 'recursive-listing' and name_space = 'http://www.uio.no/resource-types/article-listing';

update extra_prop_entry set name_space = null 
where name = 'recursive-listing' and name_space = 'http://www.uio.no/resource-types/blog-listing';

update extra_prop_entry set name_space = null 
where name = 'recursive-listing' and name_space = 'http://www.uio.no/resource-types/event-listing';

update extra_prop_entry set name_space = null 
where name = 'recursive-listing-subfolders' and name_space = 'http://www.uio.no/resource-types/article-listing';
