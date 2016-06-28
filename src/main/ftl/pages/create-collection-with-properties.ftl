<#ftl strip_whitespace=true output_format="HTML" auto_esc=true>
<#import "/lib/vtk.ftl" as vrtx />
<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<html xmlns="http://www.w3.org/1999/xhtml">
<head>
</head>
<body>
  <div id="vrtx-manage-create-content">
    <form id="create-collection-form" action="${submitURL}" method="post">

      <div>
      URI: <input type="text" name="uri" />
      </div>

      <div>
      Type: <input type="text" name="type" />
      </div>

      <#assign property_input>
        <fieldset>
          <legend>Property</legend>
          Namespace: <input type="text" name="propertyNamespace" />
          Name: <input type="text" name="propertyName" />
          Value: <input type="text" name="propertyValue" />
        </fieldset>
      </#assign>
      <#assign js_property_input = property_input?markup_string?js_string />

      <div id="property-inputs">
      ${property_input}
      ${property_input}
      ${property_input}
      </div>

      <script type="text/javascript"><!--
        function appendProperty() {
           var inputs = document.getElementById('property-inputs').getElementsByTagName('fieldset');
           var node = inputs.item(inputs.length - 1).cloneNode(true)
           document.getElementById('property-inputs').appendChild(node);
        }
      //-->
      </script>
      <input type="button" onclick="appendProperty();" value="1 more" /> <input type="Submit" value="Submit" />
    </form>
  </div>
</body>
</html>
