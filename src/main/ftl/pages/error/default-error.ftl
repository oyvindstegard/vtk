<?xml version="1.0" encoding="utf-8"?>
<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<html xmlns="http://www.w3.org/1999/xhtml">
<head>
 <title>500 - Internal Server Error</title>
</head>
<body>

<h1>500 - Internal Server Error</h1>

<p>The web page <strong>${(resourceContext.currentURI?html)?if_exists}</strong>
cannot be displayed due to an error.</p>

<#if error.errorDescription?exists>
  <p>Description: ${error.errorDescription}</p>
</#if>

<P>The error message is: ${error.exception.message?default('No message')?html}

<p>Server-administrator: <a href="mailto:${webmaster}">${webmaster}</a></p>

</div>

<#if debugErrors?exists && debugErrors>
<hr style="width: 98%;">
<#include "/lib/error-detail.ftl" />
</#if>

</body>
</html>
