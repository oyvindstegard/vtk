<#ftl strip_whitespace=true>
<#import "/lib/vortikal.ftl" as vrtx />
<?xml version="1.0" encoding="utf-8"?>
<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<html xmlns="http://www.w3.org/1999/xhtml">
  <head>
    <title>Error</title>
  </head>
  <body>

  <div class="error" class="${error.exception.class.name?replace('.', '-')}">

    <p class="previewUnavailable">
      <@vrtx.msg code="preview.unavailable" default="The content cannot be previewed"/>
    </p>
    <p class="errorMessage">
      <@vrtx.msg code="xslt.errorMessage" default="Error message" />: 
      ${error.exception.cause.message}>
    </p>
  </div>
  </body>
</html>
