<#ftl strip_whitespace=true output_format="HTML" auto_esc=true>
<#--
  - File: keywords.ftl
  - 
  - Description: displays the single or list of links
  - 
  - Required model data:
  -     urls - a list of urls
  -     values - a list of values
  - 
  -->

<#import "/lib/vtk.ftl" as vrtx/>

<#if values?exists>
  <span class="vrtx-tags">
    <#if title?exists>
      <span class="title">${title}:</span>
    <#else>
      <span class="title"><@vrtx.msg code="decorating.tags" default="Tags" />:</span>
    </#if>
    <span class="vrtx-tags-links">
       <#list values as v>
         <a href="${urls[v_index]}">${v}</a><#if v_index &lt; values?size - 1>,<#t/></#if>
       </#list>
    </span>
  </span>
</#if>
