<#ftl strip_whitespace=true output_format="HTML" auto_esc=true>
<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<html xmlns="http://www.w3.org/1999/xhtml">
<head>
  <title>400 - Bad Request</title>
</head>
<body>

<h1>400 - Bad Request</h1>

<p>The request <strong>${resourceContext.currentURI?if_exists}</strong>
could not be understood by the server.</p> 

<#if debugErrors?exists && debugErrors>
  <hr />
  <#include "/lib/error-detail.ftl" />
</#if>

<p>Server-administrator: <a href="mailto:${webmaster}">${webmaster}</a></p>

</body>
</html>
