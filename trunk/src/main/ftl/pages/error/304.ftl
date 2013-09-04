<#ftl strip_whitespace=true>

<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<html xmlns="http://www.w3.org/1999/xhtml">
<head>
  <#assign htmlTitle = "${error.errorDescription}"/>
  <title>304 - not modified</title>
</head>
<body>

  <div class="error ${error.exception.class.name?replace('.', '-')}">
    <#include "/lib/error-detail.ftl" />
  </div>

  <p>Server-administrator: <a href="mailto:${webmaster}">${webmaster}</a></p>

</body>
</html>
