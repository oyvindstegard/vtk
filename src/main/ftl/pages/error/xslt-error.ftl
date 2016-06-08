<#ftl strip_whitespace=true output_format="HTML" auto_esc=true>
<#import "/lib/vtk.ftl" as vrtx />

<#assign htmlTitle = "${error.errorDescription}"/>

<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<html xmlns="http://www.w3.org/1999/xhtml">
<head>
  <title>Error</title>
</head>
<body>
  <div class="error" class="${error.exception.class.name?replace('.', '-')}">
    <p>
      <@vrtx.msg code="xslt.unableToTransform"
                 default="This document cannot be transformed." />
    </p>
    <p>
      <@vrtx.msg code="xslt.errorMessage" default="Error message" />:
      ${error.exception.message}
    </p>
  </div>
</body>
</html>
