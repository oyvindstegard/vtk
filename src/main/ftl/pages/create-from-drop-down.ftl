<#ftl strip_whitespace=true output_format="XHTML" auto_esc=true>
<#import "/lib/vtk.ftl" as vrtx />

<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<html xmlns="http://www.w3.org/1999/xhtml">
<head>
</head>
<body>
  <div>
  <div id="vrtx-manage-create-content">
  <div class="vrtx-create-tree">
    <ul id="tree" class="filetree treeview-gray tree-create"></ul>
  </div>
  <div id="vrtx-create-tree-folders"><#list uris as link>${link}<#if uris[link_index+1]?exists>,</#if></#list></div>
  <div id="vrtx-create-tree-type">${type}</div>
  <#if addParam?has_content>
    <div id="vrtx-create-tree-add-param">${addParam}</div>
  </#if>
  </div></div>
</body>
</html>
