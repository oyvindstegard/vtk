<#ftl strip_whitespace=true output_format="XHTML" auto_esc=true>
<#attempt>
<#import "/spring.ftl" as spring />
<#import "/lib/vtk.ftl" as vrtx />

<#assign titleMsg = vrtx.getMsg("actions.expandArchive.title") />
<#assign actionURL = item.url />

<a id="manage.expandArchiveService" title="${titleMsg}" href="${actionURL}">${item.title}</a>

<#recover>
${.error}
</#recover>
