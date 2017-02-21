<#ftl strip_whitespace=true output_format="HTML" auto_esc=true>
<html>
  <head>
    <title>NEW EDITOR</title>
  </head>
  <body>
    <h1>NEW EDITOR</h1>
    <div id="output">
    </div>
    <#if resource?has_content>
      <template id="resource">
        ${resource}
      </template>
    </#if>
    <script>
      var resourceTemplate = document.getElementById("resource");
      var output = document.getElementById("output");
      var resourceSource = resourceTemplate.content.textContent;
      var resource = JSON.parse(resourceSource);
      var outStr = "";
      for (var propName in resource) {
        outStr += "<div>" + propName + " = " + resource[propName] + "</div>";
      }
      output.innerHTML = outStr;
    </script>
    <script src="/vrtx/decorating/resources/dist/doctype/helseforsk/editor.js"></script>
  </body>
</html>
