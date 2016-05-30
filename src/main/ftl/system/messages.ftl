<#ftl strip_whitespace=true output_format="XHTML" auto_esc=true>
<#import "/lib/vtk.ftl" as vrtx />

<#assign requestContext = resourceContext.requestContext />

<#list requestContext.errorMessages as msg>
  <div class="errormessage ${msg.identifier}">
    ${msg.title}
    <#if (msg.messages)?exists>
      <ul class="errors">
        <#list msg.messages as subMsg>
          <li>${subMsg}</li>
        </#list>
      </ul>
    </#if>
  </div>
</#list>

<#list requestContext.infoMessages as msg>
  <div class="infomessage ${msg.identifier}">
    ${msg.title}
    <#if msg.messages?has_content>
      <ul class="infoitems">
        <#list msg.messages as subMsg>
          <li>${subMsg}</li>
        </#list>
      </ul>
    </#if>
  </div>
</#list>
