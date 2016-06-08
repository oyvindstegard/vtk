<#ftl strip_whitespace=true output_format="HTML" auto_esc=true>
<#attempt>
<#import "/spring.ftl" as spring />
<#import "/lib/vtk.ftl" as vrtx />

<a id="deleteResourceService" href="${item.url}">${item.title}</a>

<#recover>
${.error}
</#recover>
