<#ftl strip_whitespace=true output_format="HTML" auto_esc=true>
<#import "/lib/vtk.ftl" as vrtx />
<#if sharedText??>
  <#if sharedText?has_content>
    ${sharedText?no_esc}
  <#else>
    ${vrtx.getMsg("shared-text.no-text-for-id", "No snippet exists for ID " + id, [id])}
  </#if>
<#elseif !nullProp??>
  ${vrtx.getMsg("shared-text.id-does-not-exist", "No such snippet ID: " + id, [id])}
</#if>
