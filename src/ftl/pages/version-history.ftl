<#--
  - File: version-history.ftl
  - 
  - Description: Display version info
  - 
  - Optional model data:
  -   createErrorMessage
  -   message
  -
  -->
<#if !changesXML?exists>
  <#stop "Unable to render template: required model data
  'changesXML' missing">
</#if>

<#assign doc = changesXML.document />
<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN" "http://www.w3.org/TR/html4/loose.dtd">
<html>
<head>
  <title>${doc.properties.title}</title>
</head>

<body>
<h1>${doc.properties.title}</h1>

<#list doc.body.release as release>

  <h2>Release ${release.@version} - ${release.@date}</h2>


  <#if release["action[@type = 'add']"]?has_content>
    <h3>New features</h3>
    <ul>
      <#list release["action[@type = 'add']"] as action>
        <li>${action}</li>
      </#list>
    </ul>
  </#if>

  <#if release["action[@type = 'fix']"]?has_content>
    <h3>Fixed bugs</h3>
    <ul>
      <#list release["action[@type = 'fix']"] as action>
        <li>${action}</li>
      </#list>
    </ul>
  </#if>

  <#if release["action[@type = 'update']"]?has_content>
    <h3>Updates</h3>
    <ul>
      <#list release["action[@type = 'update']"] as action>
        <li>${action}</li>
      </#list>
    </ul>
  </#if>

  <#if release["action[@type = 'remove']"]?has_content>
    <h3>Removed</h3>
    <ul>
      <#list release["action[@type = 'remove']"] as action>
        <li>${action}</li>
      </#list>
    </ul>
  </#if>

</#list>

</body>
</html>
