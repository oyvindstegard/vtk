<#ftl strip_whitespace=true output_format="XML" auto_esc=true>
<?xml version="1.0" encoding="utf-8" ?>
<Connector command="${command}" resourceType="${resourceType}" domains="${acceptableDomains}">
  <CurrentFolder path="${currentFolder}" url="${currentFolder}" />
  <#if folders?exists>
  <Folders>
  <#list folders?keys as uri>
    <Folder name="${folders[uri].resource.name}" />
  </#list>
  </Folders>
  </#if>
  <#if files?exists>
  <Files>
  <#list files?keys as uri>
    <File name="${files[uri].resource.name}" size="${(files[uri].resource.contentLength/1000)}" />
  </#list>
  </Files>
  </#if>
  <#if error?exists>
  <#if customMessage?exists>
  <Error number="${error}" msg="${customMessage?if_exists}" />
  <#else>
  <Error number="${error}" />
  </#if>
  </#if>
</Connector>
