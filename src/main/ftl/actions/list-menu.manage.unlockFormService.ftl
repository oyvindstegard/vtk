<#ftl strip_whitespace=true>
<#attempt>
<#import "/spring.ftl" as spring />
<#import "/lib/vtk.ftl" as vrtx />

<#assign headerMsg = vrtx.getMsg("actions.lockedBy") />
<#assign titleMsg = vrtx.getMsg("manage.unlock.title") />
<#assign actionURL = item.url />

<#assign lockedBy = resourceContext.currentResource.lock.principal.name />
<#if resourceContext.currentResource.lock.principal.URL?exists>
    <#assign lockedBy  = '<a href="' + resourceContext.currentResource.lock.principal.URL + '">' 
                       + resourceContext.currentResource.lock.principal.description + '</a>' />
</#if>

<h3>${headerMsg}</h3>
<p>${lockedBy}</p>

<#if unlockPermission.permissionsQueryResult = 'true'>
  <#assign owner = resourceContext.currentResource.lock.principal.qualifiedName />
  <#assign currentPrincipal = resourceContext.principal.qualifiedName />
  <#if (!owner?exists || owner = currentPrincipal)>
     <#assign actionURL = vrtx.linkConstructor("", 'manage.unlockResourceService') />
     <form method="post" action="${actionURL?html}" name="unlockForm">
       <@vrtx.csrfPreventionToken url=actionURL />
       <input id="manage.unlockFormService" title="${titleMsg}" class="vrtx-button-small" type="submit" name="unlock" value="${item.title?html}" />
     </form>
  <#else>
    <a id="manage.unlockFormService" class="vrtx-button-small" title="${titleMsg}" href="${actionURL?html}">${item.title?html}</a>
  </#if>
</#if>

<#recover>
${.error}
</#recover>
