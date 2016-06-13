<#ftl strip_whitespace=true output_format="XML" auto_esc=true>
<?xml version="1.0" encoding="utf-8"?>
<dm:mount xmlns:dm="http://purl.org/NET/webdav/mount">
  <dm:url>${webdavService.url}</dm:url>
  <#if (resourceContext.principal)?exists>
  <dm:username>${resourceContext.principal.name}</dm:username>
  </#if>
</dm:mount>
