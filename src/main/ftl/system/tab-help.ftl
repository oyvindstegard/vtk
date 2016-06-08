<#ftl strip_whitespace=true output_format="HTML" auto_esc=true>
<#if tabHelpURL?exists>
  <a class="tabHelpURL" <#if tabHelpURL.target?exists> target="${tabHelpURL.target}"</#if>
     href="${tabHelpURL.url}">${tabHelpURL.description}</a>
</#if>
