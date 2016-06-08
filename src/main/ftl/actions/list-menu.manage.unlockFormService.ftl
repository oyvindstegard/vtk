<#ftl strip_whitespace=true output_format="HTML" auto_esc=true>
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
<p>${lockedBy?no_esc}</p>

<#if unlockPermission.permissionsQueryResult = 'true'>
  <#assign owner = resourceContext.currentResource.lock.principal.qualifiedName />
  <#assign currentPrincipal = resourceContext.principal.qualifiedName />
  <#if (!owner?exists || owner = currentPrincipal)>
     <#assign actionURL = vrtx.linkConstructor("", 'manage.unlockResourceService') />
     <form method="post" action="${actionURL}" name="unlockForm">
       <@vrtx.csrfPreventionToken url=actionURL />
       <input id="manage.unlockFormService" title="${titleMsg}" class="vrtx-button-small" type="submit" name="unlock" value="${item.title}" />
     </form>
  <#else>
    <a id="manage.unlockFormService" class="vrtx-button-small" title="${titleMsg}" href="${actionURL}">${item.title}</a>
  </#if>
</#if>

<#recover>
${.error}
</#recover>
