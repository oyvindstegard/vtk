<#ftl strip_whitespace=true output_format="HTML" auto_esc=true>
<#--
  - File: reauth.ftl
  - 
  - Description: Display a message with close link when user is logged in again / session restored
  -
  - Optional model data:
  -->
<#import "/lib/vtk.ftl" as vrtx />

<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<html xmlns="http://www.w3.org/1999/xhtml">
  <head>
    <meta name="viewport" content="width=1140, user-scalable=yes" /> 
    <title>${vrtx.getMsg("reauth.title")}</title>
    <link rel="stylesheet" type="text/css" href="/vrtx/__vrtx/static-resources/themes/default/default.css" />
    <style type="text/css">
      body, html { background: #fff; }
      body {
        text-align: left;
        padding: 15px 25px 25px 25px;
      }
    </style>
    <script type="text/javascript" src="/vrtx/__vrtx/static-resources/jquery/jquery.min.js"></script>
    <script type="text/javascript"><!--
      $(document).ready(function() {
        $(document).on("click", "#vrtx-reauth-close", function(e) {
          window.close();
          e.stopPropagation();
          e.preventDefault();
        });
      });
    // -->
    </script>
  </head>
  <body>
    <h1>${vrtx.getMsg("reauth.title")}</h1>
    <a id="vrtx-reauth-close" class="vrtx-button" href="javascript:void(0);">${vrtx.getMsg("reauth.close")}</a>
  </body>
</html>
