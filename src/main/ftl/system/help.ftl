<#ftl strip_whitespace=true>
<#import "/lib/vortikal.ftl" as vrtx />
<#assign lang><@vrtx.requestLanguage/></#assign>
<#assign url = helpURL />
<#if .vars["helpURL." + lang]?exists>
   <#assign url = .vars["helpURL." + lang] />
</#if>
<a href="${url?html}" target="help"><@vrtx.msg code="manage.help" default="Help" /></a>
