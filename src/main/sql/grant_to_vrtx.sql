grant delete, select, insert, update on SIMPLE_CONTENT_REVISION to vrtx;
grant delete, select, insert, update on REVISION_ACL_ENTRY to vrtx;
grant delete, select, insert, update on ACL_ENTRY to vrtx;                      
grant delete, select, insert, update on ACTION_TYPE to vrtx;                    
grant delete, select, insert, update on EXTRA_PROP_ENTRY to vrtx;               
grant delete, select, insert, update on VORTEX_LOCK to vrtx;                    
grant delete, select, insert, update on VORTEX_RESOURCE to vrtx;                
grant delete, select, insert, update on DELETED_RESOURCE to vrtx;
grant delete, select, insert, update on VORTEX_TMP to vrtx;  
grant delete, select, insert, update on  PROP_TYPE to vrtx;  
grant delete, select, insert, update on  CHANGELOG_ENTRY to vrtx;  
grant delete, select, insert, update on  VORTEX_COMMENT to vrtx;  

grant usage, select on all sequences in schema public to vrtx;