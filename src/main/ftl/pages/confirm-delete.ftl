<#ftl strip_whitespace=true output_format="HTML" auto_esc=true>

<#--
  - File: confirm-delete.ftl
  - 
  - Description: Displays a delete confirmation dialog
  - 
  - Required model data:
  -   url
  -   name
-->

<#import "/spring.ftl" as spring />
<#import "/lib/vtk.ftl" as vrtx />

<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<html xmlns="http://www.w3.org/1999/xhtml">
<head>
<body>
<div class="vrtx-confirm-delete-msg">
${vrtx.getMsg("collectionListing.confirmation.delete")} <span class="vrtx-confirm-delete-name"> ${name}</span>? 
</div>

<form name="vrtx-delete-resource" id="vrtx-delete-resource" action="${url}" method="post">
  <div class="submitButtons">
    <button class="vrtx-focus-button" type="submit" value="ok" id="deleteResourceAction" name="deleteResourceAction">
      ${vrtx.getMsg("confirm-delete.ok")}
    </button>
    <button class="vrtx-button" type="submit" value="cancel" id="deleteResourceCancelAction" name="deleteResourceCancelAction">
      ${vrtx.getMsg("confirm-delete.cancel")}
    </button>
  </div>
</form>
</body>
</html>
