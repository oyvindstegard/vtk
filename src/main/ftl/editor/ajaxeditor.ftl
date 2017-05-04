<#ftl strip_whitespace=true output_format="HTML" auto_esc=true>
<!DOCTYPE html>
<html>
  <head>
    <title>${resourceName}</title>
    <link rel="stylesheet" type="text/css" href="${editorCssURI}" />
  </head>
  <body class="js forms-new">
    <div id="editor"></div>

    <template id="content">
      ${resourceContent}
    </template>

    <script>
      //<![CDATA[
      (function () {
        function getCookie(name) {
            var value = " " + document.cookie;
            var c_start = value.indexOf(" " + name + "=");
            if (c_start == -1) {
                value = null;
            }
            else {
                c_start = value.indexOf("=", c_start) + 1;
                var c_end = value.indexOf(";", c_start);
                if (c_end == -1) {
                    c_end = value.length;
                }
                value = unescape(value.substring(c_start,c_end));
            }
            return value;
        }
        var contentSource = document.getElementById("content").content.textContent;
        var contentType = "${contentType}";
        var content = contentSource;
        if (contentType == "application/json") {
          content = JSON.parse(contentSource);
        }

        window.editor = {};
        window.editor.resource = {
          content: content,
          contentType: contentType,
          contentLanguage: "${contentLanguage}",
          uri: "${resourceURI}"
        };
        window.editor.csrfToken = getCookie("csrfToken");
        window.editor.locale = "${locale}"
        window.editor.user = {
          username: "${userName}",
          name: "${userDescription}"
        }
        <#if userUrl?has_content>
        window.editor.user.url = "${userUrl}";
        </#if>
      })();
      //]]>
    </script>
    <script src="${editorJsURI}"></script>
  </body>
</html>
