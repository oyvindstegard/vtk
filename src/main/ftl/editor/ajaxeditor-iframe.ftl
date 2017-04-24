<#ftl strip_whitespace=true output_format="HTML" auto_esc=true>
<#--
  - File: ajaxeditor-iframe.ftl
  -
  -->
<#import "/spring.ftl" as spring />
<#import "/system/javascript.ftl" as spring />
<#import "/lib/vtk.ftl" as vrtx />

<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<html xmlns="http://www.w3.org/1999/xhtml">
  <head>
    <title>Editor</title>
  </head>
  <body>
    <iframe title="${vrtx.getMsg("iframe.title.edit")}" class="edit" name="editIframe" id="editIframe" src="${resourceReference?no_esc}" marginwidth="0" marginheight="0" scrolling="auto" frameborder="0" style="overflow:visible; width:100%; ">
      ${vrtx.getMsg("iframe.not-supported")} ${vrtx.getMsg("iframe.not-supported.title-prefix")} "${vrtx.getMsg("iframe.title.preview")}". <@vrtx.msg code="iframe.not-supported.link" args=[resourceReference] />
    </iframe>
    <script><!--
      $(function () {
        setIframeHeight(); // Default
      });
      
      function setIframeHeight() {
        var height = $("#app-content").height();
        $("#editIframe").attr("height", height);
      }
     
      $(window).on('message', function(e) {
        if (e.originalEvent) e = e.originalEvent;
        var data = e.data;
        var origin = e.origin;
        var regex = new RegExp("^" + location.protocol + "//" + location.host);
        
        if (regex.test(origin) && data.type == 'set-editor-height') {
          if(data.height && !isNaN(data.height)) {
            $("#editIframe").attr("height", height);
          }
        }
      });
    // -->
    </script>
  </body>
</html>
  
