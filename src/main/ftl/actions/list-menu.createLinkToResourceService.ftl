<#ftl strip_whitespace=true output_format="HTML" auto_esc=true>
<#attempt>
<#import "/spring.ftl" as spring />
<#import "/lib/vtk.ftl" as vrtx />

${prepend}<a href="javascript:updateParent('${editField}', '${browseURL}')">${item.title}</a>${append}

<#recover>
${.error}
</#recover>
