<#ftl strip_whitespace=true output_format="XHTML" auto_esc=true>
<#--
  - File: confirm-publish.ftl
  - 
  - Description: Displays a publish confirmation dialog
  - 
  - Required model data:
  -   url
  -   name
-->
<#import "/spring.ftl" as spring />
<#import "/lib/vtk.ftl" as vrtx />

<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<html xmlns="http://www.w3.org/1999/xhtml">
<head></head>
<body>

<div class="vrtx-confirm-publish-msg">
<#if type = "publish.unpublishResourceConfirmedService">
${vrtx.getMsg("confirm-publish.confirmation.unpublish")}?
<#else>
${vrtx.getMsg("confirm-publish.confirmation.publish")}?
</#if>
</div>

<form name="vrtx-publish-resource" id="vrtx-publish-resource" action="${url}" method="post">
  <div class="submitButtons">
    <input class="vrtx-focus-button" type="submit" id="publishResourceAction" 
           name="publishResourceAction" value="${vrtx.getMsg("confirm-delete.ok")}" />
    <input class="vrtx-button" type="submit" id="publishResourceCancelAction"  
           name="publishResourceCancelAction" value="${vrtx.getMsg("confirm-delete.cancel")}"  />
  </div>
</form>

</body>
</html>
