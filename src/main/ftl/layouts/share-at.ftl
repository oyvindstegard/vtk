<#ftl strip_whitespace=true output_format="HTML" auto_esc=true>
<#--
  - File: share-at.ftl
  - 
  - Description: Share document on social websites
  - 
  - Required model data:
  -   socialWebsiteLinks      (list of TemplateLinksProvider.Link)
  -
  -->
<#import "/lib/vtk.ftl" as vrtx />
<#import "/lib/view-utils.ftl" as viewutils />

<#if socialWebsiteLinks?has_content>
<div class="vrtx-share-at-component">
  <ul>
  <#list socialWebsiteLinks as link>
     <#assign url = link.url />
     <#assign name = link.name />

     <#if url?is_string>
       <li class="vrtx-share-at-${name}">
         <a href="${url}" target="_blank" class="${name?lower_case}">
           <@vrtx.msg code="decorating.shareAtComponent.title" args=[name] /></a>
       </li>
    </#if>
  </#list>
  </ul>
</div>
</#if>
