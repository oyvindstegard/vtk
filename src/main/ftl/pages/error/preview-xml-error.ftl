<#ftl strip_whitespace=true output_format="HTML" auto_esc=true>
<#import "/lib/vtk.ftl" as vrtx />

<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<html xmlns="http://www.w3.org/1999/xhtml">
  <head>
    <title>Error</title>
  </head>
  <body>

  <div class="error" class="${error.exception.class.name?replace('.', '-')}">

    <p class="previewUnavailable">
      <@vrtx.msg code="preview.unavailable" default="The content cannot be previewed"/>
    
      <span class="errorMessage">
        <@vrtx.msg code="xslt.errorMessage" default="Error message" />: 
        ${error.exception.cause.message}>
      </span>
    </p>
  </div>
  </body>
</html>
