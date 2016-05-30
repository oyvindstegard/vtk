<#ftl strip_whitespace=true output_format="XHTML" auto_esc=true>
<#attempt>
<#import "/spring.ftl" as spring />
<#import "/lib/vtk.ftl" as vrtx />
<#import "/lib/publish.ftl" as publish />


<#assign headerMsg = vrtx.getMsg("publish.header") />
<#assign titleMsg = vrtx.getMsg("publish.title") />
<#assign actionURL = item.url />

<h3>${headerMsg}</h3>

<@publish.publishMessage resourceContext />

<#if writePermission.permissionsQueryResult = 'true'>

  <#if !resourceContext.currentResource.isCollection()>
    <ul class="publishing-document button-row-small">
      <li class="first">
        <a id="vrtx-publish-document" title="${titleMsg}" href="${actionURL}">
          ${item.title}
        </a>
      </li>
      <li>
        <a id="advanced-publish-settings" href="${resourceContext.currentURI}?vrtx=admin&display=advanced-publish-dialog">
          <@vrtx.msg code="publishing.advanced.link" />
        </a>
      </li>    
    </ul>
  <#else>
    <a id="vrtx-publish-document" class="vrtx-button-small" title="${titleMsg}" href="${actionURL}">${item.title}</a>
  </#if>
</#if>

<#recover>
${.error}
</#recover>
