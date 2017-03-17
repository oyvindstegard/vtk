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
      ${resourceContent?no_esc}
    </template>
    <template id="content-type">
      ${resource.contentType}
    </template>
    <template id="csrf-token">
      ${VRTX_CSRF_PREVENTION_HANDLER.newToken(url)}
    </template>
    <script>
      //<![CDATA[
      (function () {
        var contentSource = document.getElementById("content").content.textContent;
        var csrfToken = document.getElementById("csrf-token").content.textContent.trim();
        var contentType = document.getElementById("content-type").content.textContent.trim();
        var content = contentSource;
        if (contentType == "application/json") {
          content = JSON.parse(contentSource);
        }
        window.editor = {};
        window.editor.resource = {
          content: content,
          contentType: contentType
        };
        window.editor.csrfToken = csrfToken;
      })();
      //]]>
    </script>
    <script src="${editorJsURI}"></script>
  </body>
</html>
