<#ftl strip_whitespace=true output_format="HTML" auto_esc=true>
<#import "/lib/vtk.ftl" as vrtx />

<#assign locale = springMacroRequestContext.getLocale() />

<#if tabMessage?exists> <#-- the general one -->
  <div class="tabMessage">${tabMessage}</div>
</#if>

<#-- XXX: remove -->
<#if expiresSec?exists && expiresSec["expires-sec"]?exists>
  <#assign delay = expiresSec["expires-sec"]?number / 60>
  <#if delay &gt;= 5>
  <div class="tabMessage">
    <#assign delay = delay?string("0.###")>
    <@vrtx.msg "headerControl.expiresSec",
    "This resource uses the expires property", [delay] />
  </div>
  </#if>
</#if>
