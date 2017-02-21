<#ftl strip_whitespace=true output_format="HTML" auto_esc=true>
<html>
  <head>
    <title>NEW EDITOR</title>
  </head>
  <body>
    <h1>NEW EDITOR</h1>
    
    <div id="root"></div>
    
    <#if resource?has_content>
      <template id="resource">
        ${resource}
      </template>
    </#if>
    <script>
      var resourceTemplate = document.getElementById("resource");
      var resourceSource = resourceTemplate.content.textContent;
      var resource = JSON.parse(resourceSource);
    </script>
    <script src="https://vortex-systest.uio.no/vrtx/decorating/resources/dist/doctype/helseforsk/editor.js"></script>
  </body>
</html>
