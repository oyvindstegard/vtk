<#ftl strip_whitespace=true output_format="HTML" auto_esc=true>
<#attempt>
<#import "/spring.ftl" as spring />
<#import "/lib/vtk.ftl" as vrtx />

<#assign titleMsg = vrtx.getMsg("actions.renameService.title") />
<#assign actionURL = item.url />

<a id="renameService" title="${titleMsg}" href="${actionURL}">${item.title}</a>

<#recover>
${.error}
</#recover>
