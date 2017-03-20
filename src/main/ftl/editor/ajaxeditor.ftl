<#ftl strip_whitespace=true output_format="HTML" auto_esc=true>
<!DOCTYPE html>
<html>
  <head>
    <title>${resource.name}</title>
    <link rel="stylesheet" type="text/css" href="${editorCssURI}" />
  </head>
  <body>
    <div id="editor"></div>

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
    <script src="${editorJsURI}"></script>
  </body>
</html>
