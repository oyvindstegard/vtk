<#ftl strip_whitespace=true output_format="HTML" auto_esc=true>
<#--
  - File: webconsole.ftl
  - 
  - Description: 
  - 
  - Required model data:
  -  
  - Optional model data:
  -
  -->
<#import "/spring.ftl" as spring />
<#import "/lib/vtk.ftl" as vrtx />

<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<html xmlns="http://www.w3.org/1999/xhtml">
<head>
  <title>Web console</title>
  <style type="text/css">
    .webConsoleResults {
       font-family: monospace;
    }
  </style>
</head>
<body>
  <#if commandForm?exists>
  <p>
    <form class="webConsole" action="${commandForm.submitURL}" method="post">
      <pre>Enter command:</pre>
      <input class="vrtx-textfield" type="text" name="command" size="60" />
    </form>
  </p>
  </#if>
  <#if (commandForm.result)?exists>
    <p>
      <pre>
        <#if (commandForm.command)?exists>[${commandForm.command}]:</#if>
      </pre>
      <textarea cols="80" rows="20">${commandForm.result}</textarea>
    </p>
  </#if>
</body>
</html>
