<#ftl strip_whitespace=true output_format="HTML" auto_esc=true>
<#import "/lib/vtk.ftl" as vrtx />
<#if loginURL?exists>
  <#assign url = loginURL />
  <#-- XXX: remove hard-coded 'authTarget' parameter: -->
  <#if url?contains("?")>
    <#assign url = url + "&amp;authTarget=http" />
  <#else>
    <#assign url = url + "?authTarget=http" + previewUnpublishedParameter + "=" + "true" + "&amp;" + previewRefreshParameter + "=" + dateStr + "&amp;authTarget=http" />
  </#if>
  <div class="vrtx-login">
    <a href="${url}"><@vrtx.msg code="decorating.authenticationComponent.login" default="Log in"/></a>
  </div>
<#elseif logoutURL?exists>
  <div class="vrtx-logout" id="vrtx-logout">
    <span class="vrtx-user">${principal.description}</span>
    <form id="logoutForm" action="${logoutURL}" method="post" style="display:inline;">
      <@vrtx.csrfPreventionToken url=logoutURL />
      <button type="submit" id="logoutAction" name="logoutAction"><@vrtx.msg code="decorating.authenticationComponent.logout" default="logout"/></button>
    </form>
    <!-- Hide submit button, display a link instead: -->
    <script type="text/javascript"><!--
	  document.getElementById("logoutAction").style.display = "none";
	  var logoutLink = document.createElement("a");
	  logoutLink.setAttribute("href","javascript:document.getElementById('logoutForm').submit();");
	  logoutLink.innerHTML = "<@vrtx.msg code="decorating.authenticationComponent.logout" default="logout"/>"
		
	  var lp = document.createElement("span");
	  lp.innerHTML = "( ";
	  var rp = document.createElement("span");
	  rp.innerHTML = " )";
	  document.getElementById("vrtx-logout").appendChild(lp);
	  document.getElementById("vrtx-logout").appendChild(logoutLink);
	  document.getElementById("vrtx-logout").appendChild(rp);
	// -->
    </script>
  </div>
</#if>
