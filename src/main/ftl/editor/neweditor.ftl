<#ftl strip_whitespace=true output_format="HTML" auto_esc=true>
<!DOCTYPE html>
<html>
  <head>
    <title>NEW EDITOR</title>
    <link rel="stylesheet" type="text/css" href="https://vortex-systest.uio.no/vrtx/decorating/resources/dist/doctype/helseforsk/editor.css" />
    <link rel="stylesheet" type="text/css" href="/vrtx/__vrtx/static-resources/jquery/plugins/jquery.autocomplete.css" />
  </head>
  <body>
    <h1>NEW EDITOR</h1>
    
    <div id="root"></div>
    
    <#if resource?has_content>
      <template id="resource">
        ${resource}
      </template>
    </#if>
    <#if url?has_content>
      <template id="csrf-token">
        ${VRTX_CSRF_PREVENTION_HANDLER.newToken(url)}
      </template>
    </#if>
    <script>
      //<![CDATA[
      (function () {
        var resourceSource = document.getElementById("resource").content.textContent;
        var csrfToken = document.getElementById("csrf-token").content.textContent;
        var resource = JSON.parse(resourceSource);
        window.editor = {};
        window.editor.resource = resource;
        window.editor.csrfToken = csrfToken;
      })();
      //]]>
    </script>
    <script type="text/javascript" src="/vrtx/__vrtx/static-resources/jquery/jquery.min.js"></script>
    <script type="text/javascript" src="/vrtx/__vrtx/static-resources/jquery/plugins/jquery.autocomplete.js"></script>
    <script type="text/javascript" src="/vrtx/__vrtx/static-resources/ckeditor-build/ckeditor.js"></script>
    <script type="text/javascript" src="/vrtx/__vrtx/static-resources/ckeditor-build/adapters/jquery.js"></script>
    <script src="https://vortex-systest.uio.no/vrtx/decorating/resources/dist/doctype/helseforsk/editor.js"></script>
  </body>
</html>
