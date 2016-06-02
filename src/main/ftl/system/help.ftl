<#ftl strip_whitespace=true output_format="HTML" auto_esc=true>
<#import "/lib/vtk.ftl" as vrtx />
<#assign lang><@vrtx.requestLanguage/></#assign>
<#assign url = helpURL />
<#assign key="helpURL." + lang?markup_string />
<#if .vars[key]?exists>
   <#assign url = .vars[key] />
</#if>
<a href="${url}" target="_blank" class="help-link"><@vrtx.msg code="manage.help" default="Help in editing" /></a>
