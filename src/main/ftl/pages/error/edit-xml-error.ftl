<#ftl strip_whitespace=true output_format="HTML" auto_esc=true>
<#import "/lib/vtk.ftl" as vrtx />
<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<html xmlns="http://www.w3.org/1999/xhtml">
<head>
  <title>Error</title>
</head>
<body>
  <div class="error ${error.exception.class.name?replace('.', '-')}">

    <p class="xmlEditUnavailable"><@vrtx.msg code="xmledit.unavailable" default="The XML document cannot be edited"/></p>

    <p class="errorMessage"><@vrtx.msg code="xmledit.errorMessage" default="Error message" />: ${error.exception.message}>
      
    <#if (error.exception).cause?exists && ((error.exception).cause).message?exists >
      <p class="errorMessage"><@vrtx.msg code="xmledit.errorMessage.cause" default="Caused by" />: ${error.exception.cause.message}>
    </#if>
  </div>
</body>
</html>
