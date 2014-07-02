<#ftl strip_whitespace=true>

<#--
  - File: list-decorator-components.ftl
  - 
  - Description: 
  - 
  - Required model data: componentLib
  -  
  - Optional model data:
  -
  -->
<#import "/spring.ftl" as spring />
<#import "/lib/vortikal.ftl" as vrtx />
<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<html xmlns="http://www.w3.org/1999/xhtml">
<head>
  <title>Decorator components</title>
</head>
<body>
  <h1>Decorator components</h1>
  <#assign componentList = componentLib.components() />

<#if componentList?size == 0>
  <div class="content">
    No components defined.
  </div>
<#else>
  <#list componentList?sort_by('name')?sort_by('namespace') as component>
    <#assign componentID = component.namespace + ":" + component.name />
    <#if !hiddenComponents?exists || !hiddenComponents?seq_contains(componentID)>
      <h2 id="${componentID?html}">${componentID?html}</h2>
      <h3>Description</h3>
      <div class="componentDescription">${(component.description?html)?if_exists}</div>
      <#if (component.parameterDescriptions)?exists>
        <h3>Parameters</h3>
        <#list component.parameterDescriptions?keys as paramName>
          <dl class="parameters">
            <dt>${paramName}</dt> 
            <dd>${component.parameterDescriptions[paramName]?html}</dd>
          </dl>
        </#list>
      </#if>
      <#if (component.usageExamples)?exists && component.usageExamples?size &gt; 0>
        <h3>Examples</h3>
        <#list component.usageExamples as example>
          <div style="font-family: monospace;">
            ${example.example(componentID)}
          </div>
        </#list>
      </#if>
    </#if>
  </#list>
</#if>

<#if componentLib.errors()?exists>
  <div class="errors">
    <h2>Compilation errors</h2>
    <ul>
      <#list componentLib.errors() as error>
          <li>${error?html}</li>
      </#list>
    </ul>
  </div>
</#if>

</body>
</html>
