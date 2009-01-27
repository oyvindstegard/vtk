<#ftl strip_whitespace=true>

<#--
  - File: tags.ftl
  - 
  - Description: Article view
  - 
  - Required model data:
  -   resource
  -   tag
-->

<#import "/spring.ftl" as spring />
<#import "/lib/vortikal.ftl" as vrtx />

<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<html xmlns="http://www.w3.org/1999/xhtml">
<head>
<body>
<div class="vrtx-confirm-delete-msg">
${vrtx.getMsg("collectionListing.confirmation.delete")} <span class="vrtx-confirm-delete-name"> ${name}</span>? 
</div>   

<form name="vrtx-delete-resource" id="vrtx-delete-resource" action="${url}" method="post">
	<input type="hidden" value="delete" name="action" id="action" />
	<button tabindex="1" type="submit" value="ok" id="saveAction" name="saveAction">${vrtx.getMsg("confirm-delete.ok")}</button>
    <button tabindex="2" type="submit" onclick="tb_remove();" value="cancel" id="cancelAction" name="cancelAction">${vrtx.getMsg("confirm-delete.cancel")}</button>
</form>

<script language="javascript">
	function focus(){
		$("#vrtx-delete").focus();
	}
	
	$(document).ready(function(){
		setTimeout("focus();",0);
		$("#cancelAction").remove();
		$("#vrtx-delete-resource").append('<button tabindex="2" type="button" onclick="tb_remove();" id="cancelAction" name="cancelAction">${vrtx.getMsg("confirm-delete.cancel")}</button>');
	});
</script>
</body>
</html>
