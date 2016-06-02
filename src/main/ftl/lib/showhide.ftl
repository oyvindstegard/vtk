<#ftl strip_whitespace=true output_format="HTML" auto_esc=true>
<#-- Show and hide functionality -->

<#macro addShowHide script>
  <#local parameters = '' />
  <#list script.params?keys as param>
   	<#list script.params[param] as value >
   	  <#if parameters == ''>
   	    <#local parameters = "div." + value?string />
   	  <#else>
   	    <#local parameters = parameters + ", div." + value?string />
   	  </#if>
   	</#list>
  </#list>
  setShowHideBooleanNewEditor('${script.name}', '${parameters}', true);
</#macro>
