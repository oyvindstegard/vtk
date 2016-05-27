<#ftl strip_whitespace=true output_format="XHTML" auto_esc=true>
<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<html xmlns="http://www.w3.org/1999/xhtml">
<head>
  <title>403 - Forbidden</title>
</head>
<body>

<h1>403 - Access denied</h1>

<p>The web page <strong>${resourceContext.currentURI?if_exists}</strong>
is restricted to a specific set of users.</p> 

<#if debugErrors?exists && debugErrors>
  <hr />
  <#include "/lib/error-detail.ftl" />
</#if>

<p>Server-administrator: <a href="mailto:${webmaster}">${webmaster}</a></p>

</body>
</html>
