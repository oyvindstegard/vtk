<#ftl strip_whitespace=true output_format="HTML" auto_esc=true>
<!DOCTYPE html>
<html>
  <head>
    <title>${resource.name}</title>
    <!-- TODO include (VTK-4894) -->
    <link rel="stylesheet" type="text/css" href="/vrtx/__vrtx/static-resources/jquery/plugins/ui/jquery-ui-1.10.4.custom/css/smoothness/jquery-ui-1.10.4.custom.min.css"/>
    <link rel="stylesheet" type="text/css" href="/vrtx/__vrtx/static-resources/themes/default/forms.css" />
    <link rel="stylesheet" type="text/css" href="/vrtx/__vrtx/static-resources/themes/default/upload.css" />
    <link rel="stylesheet" type="text/css" href="/vrtx/__vrtx/static-resources/jquery/plugins/jquery.autocomplete.css" />
    <link rel="stylesheet" type="text/css" href="/vrtx/__vrtx/static-resources/js/autocomplete/autocomplete.override.css" />
    <link rel="stylesheet" type="text/css" href="${editorCssURI}" />
  </head>
  <body>
    <div id="editor" class="js forms-new"></div>

    <template id="content">
      ${resourceContent}
    </template>

    <script>
      //<![CDATA[
      (function () {
        var contentSource = document.getElementById("content").content.textContent;
        var csrfToken = "${VRTX_CSRF_PREVENTION_HANDLER.newToken(url)?json_string}";
        var contentType = "${resource.contentType?json_string}";
        var contentLanguage = "${resource.contentLanguage?json_string}";
        var content = contentSource;
        if (contentType == "application/json") {
          content = JSON.parse(contentSource);
        }
        window.editor = {};
        window.editor.resource = {
          content: content,
          contentType: contentType,
          contentLanguage: contentLanguage
        };
        window.editor.csrfToken = csrfToken;
      })();
      //]]>
    </script>
     <!-- TODO include (VTK-4894) -->
    <script type="text/javascript" src="/vrtx/__vrtx/static-resources/jquery/jquery.min.js"></script>
    <script type="text/javascript" src="/vrtx/__vrtx/static-resources/jquery/plugins/jquery.autocomplete.js"></script>
    <script type="text/javascript" src="/vrtx/__vrtx/static-resources/ckeditor-build/ckeditor.js"></script>
    <script type="text/javascript" src="/vrtx/__vrtx/static-resources/ckeditor-build/adapters/jquery.js"></script>
    <script type="text/javascript" src="/vrtx/__vrtx/static-resources/js/frameworks/es5-shim-dejavu.js"></script>
    <script type="text/javascript" src="/vrtx/__vrtx/static-resources/js/vrtx-simple-dialogs.js"></script>
    <script src="${editorJsURI}"></script>
  </body>
</html>
