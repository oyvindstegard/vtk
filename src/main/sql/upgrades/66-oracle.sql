
-- The 'authors' property (HTML-based articles) has been
-- removed. Since it is is multi-valued, we have to also remove it
-- from the database (we don't support dead multi-value properties):

delete from extra_prop_entry where name_space is null and name = 'authors';

-- recursive-listing-subfolders has changed its namespace:

update extra_prop_entry set name_space = null
       where name_space = 'http://www.uio.no/resource-types/article-listing'
       and name = 'recursive-listing-subfolders';


-- The 'recursive-listing' property: this property first existed for
-- HTML-based article listings, and later for new JSON-based resource
-- types and listings:
-- * Legacy type: <null>:recursive-listing
-- * Article/event/blog listing: {el,al,bl}:recursive-listing

-- This property is now to become mandatory with name space <null>. 

-- Collections can be classified with regards to this property as follows:

-- A) Resources with no recursive-listing property
-- B) Resources with only <null>:recursive-listing (no namespace)
-- C) Resources with only <notnull>:recursive-listing ('al', 'bl' or 'el' namespace)
-- D) Resources with both <null>:recursive-listing and <notnull>:recursive-listing

-- Ultimately the goal is to be left with only class B, while preserving any
-- existing values.

-- Migration strategy:

-- 1. Convert class B to class C (using a temporary name space). 
--    We are now left with classes A, C and D
-- 2. Convert class D to class C (remove <null>:recursive-listing). 
--    Now what is left is class A and C
-- 3. Convert class A to C (recursive-listing=true for resources that 
--    do not have the property). This leaves us with only class C.
-- 4. Remove name space of class C, converting it to class B

-- Step 1:
update extra_prop_entry set name_space = 'temp' 
where name = 'recursive-listing' and name_space is null and 
extra_prop_entry_id not in (
      select extra_prop_entry_id from extra_prop_entry a where a.name = 'recursive-listing' 
      and exists 
          (select b.resource_id from extra_prop_entry b 
            where b.name = a.name and b.extra_prop_entry_id != a.extra_prop_entry_id 
            and b.resource_id = a.resource_id));


-- Step 2:
delete from extra_prop_entry 
where name = 'recursive-listing' and name_space is null and 
extra_prop_entry_id in (
      select extra_prop_entry_id from extra_prop_entry a where a.name = 'recursive-listing' 
      and exists 
          (select b.resource_id from extra_prop_entry b 
            where b.name = a.name and b.extra_prop_entry_id != a.extra_prop_entry_id 
            and b.resource_id = a.resource_id));


-- Step 3:
insert into extra_prop_entry
select extra_prop_entry_seq_pk.nextval,
       resource_id,
       0,
       'temp',
       'recursive-listing',
       'true',
       null,
       null,
       'N'
from vortex_resource
where is_collection = 'Y'
and resource_id not in
    (select distinct(resource_id) from extra_prop_entry
        where name_space is not null and name = 'recursive-listing');


-- Step 4:
update extra_prop_entry
set name_space = null
where name_space in ('temp', 
                     'http://www.uio.no/resource-types/article-listing', 
                     'http://www.uio.no/resource-types/blog-listing', 
                     'http://www.uio.no/resource-types/event-listing')
and name = 'recursive-listing';
