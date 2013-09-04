<#ftl strip_whitespace=true>
<#import "/lib/vortikal.ftl" as vrtx />
<#if sharedText??>
  <#if sharedText?has_content>
    ${sharedText}
  <#else>
    ${vrtx.getMsg("shared-text.no-text-for-id")}
  </#if>
<#elseif !nullProp??>
  ${vrtx.getMsg("shared-text.id-does-not-exist")}
</#if>