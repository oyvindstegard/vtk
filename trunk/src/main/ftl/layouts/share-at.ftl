<#ftl strip_whitespace=true>
<#--
  - File: share-at.ftl
  - 
  - Description: Share document on social websites
  - 
  - Required model data:
  -   socialWebsiteLinks      (list of TemplateLinksProvider.Link)
  -
  - Optional model data:
  -   use-facebook-api        (decorator component param)
  -
  -->
<#import "/lib/vortikal.ftl" as vrtx />
<#import "/lib/view-utils.ftl" as viewutils />

<#assign prefixLinkText = vrtx.getMsg("decorating.shareAtComponent.title") />
<#assign useFacebookAPI = .vars["use-facebook-api"]?? && .vars["use-facebook-api"]?string != "false" />

<#if socialWebsiteLinks?has_content>
<div class="vrtx-share-at-component">
  <ul>
  <#list socialWebsiteLinks as link>
     <#if (link.name = "FacebookAPI" && !useFacebookAPI)
          || (link.name = "Facebook" && useFacebookAPI)>
       <#assign url = false name = link.name />
     <#else>
       <#assign url = link.url name = link.name />
     </#if>

     <#if name = "FacebookAPI"><#assign name = "Facebook" /></#if>
     
     <#if url?is_string>
       <li>
         <a href="${url}" target="_blank" class="${name?lower_case}">${prefixLinkText} ${name}</a>
       </li>
    </#if>
  </#list>
  </ul>
</div>
</#if>