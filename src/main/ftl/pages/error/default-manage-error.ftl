<#ftl strip_whitespace=true>
<?xml version="1.0" encoding="utf-8"?>
<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<html xmlns="http://www.w3.org/1999/xhtml">
<head>
<#assign htmlTitle = "${error.errorDescription}"/>

  <title>Error</title>
</head>
<body>

  <div class="error ${error.exception.class.name?replace('.', '-')}">
    <#include "/lib/error-detail.ftl" />
  </div>

</body>
</html>
