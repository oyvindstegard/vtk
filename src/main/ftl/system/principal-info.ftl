<#ftl strip_whitespace=true output_format="HTML" auto_esc=true>
<#import "/lib/vtk.ftl" as vrtx />

<span class="principal">
  <span class="name">${resourceContext.principal.description}</span>
  <#if logout.url?exists>
    <form method="post" action="${logout.url}" id="logoutForm">
      <input type="hidden" name="useRedirectService" value="true" />
      <input type="submit" value="<@vrtx.msg code="manage.logout" default="logout"/>" id="logoutAction" name="logoutAction" />
    </form>
  </#if>
</span>
