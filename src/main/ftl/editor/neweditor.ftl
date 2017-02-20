<#ftl strip_whitespace=true output_format="HTML" auto_esc=true>
<html>
<head>
    <title>NEW EDITOR</title>
</head>
    <body>
        <h1>NEW EDITOR</h1>
        <div id="output">
        </div>
        <#if content?has_content>
        <template id="content">
        ${content}
        </template>
        </#if>
    </body>
    <script>
    var content = document.getElementById("content");
    var output = document.getElementById("output");
    output.innerHTML = content.content.textContent;
    output.innerHTML = content.content.textContent;

    </script>
    <script src="/vrtx/decorating/resources/dist/doctype/helseforsk/editor.js"></script>
</html>
