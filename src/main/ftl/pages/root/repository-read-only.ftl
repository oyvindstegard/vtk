<#ftl strip_whitespace=true output_format="XHTML" auto_esc=true>
<#--
  - File: repository-read-only.ftl
  - 
  - Description: A HTML page that gives access to switch read only status
  - 
  - Required model data:
  - message
  -  
  - Optional model data:
  - repository.readOnlySwitchUrl 
  -->
<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<html xmlns="http://www.w3.org/1999/xhtml">
  <head>
    <title>Read only status</title>
  </head>
  <body>
    <h2>Read only status for ${resourceContext.repositoryId}</h2>
    <#if globalReadOnly>
        <p>
        Repository is globally in read only mode
        <#if unsetReadOnlyUrl?exists>
          (<a href="${unsetReadOnlyUrl.url}">switch off</a>)
        </#if>
        </p>
    <#elseif readOnlyRoots?size &gt; 0>
      <p>Repository is in read-only-mode only for some roots: <#if unsetReadOnlyUrl?exists>(<a href="${unsetReadOnlyUrl.url}">switch off</a>)</#if>
         <#if setReadOnlyUrl?exists>(<a href="${setReadOnlyUrl.url}">switch on globally</a>)</#if>
      </p>
      <ul>
      <#list readOnlyRoots as path>
        <li>${path}</li>
      </#list>
      </ul>
    <#else>
      <p>Repository is not in read-only mode. <#if setReadOnlyUrl?exists>(<a href="${setReadOnlyUrl.url}">switch on globally</a>)</#if>
      </p>
    </#if>
  </body>
</html>
