<#ftl strip_whitespace=true />
<#import "/lib/menu/list-menu.ftl" as listMenu />
<#import "/system/resource-bar.ftl" as resBar />

<#assign resource = resourceContext.currentResource />

<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<html xmlns="http://www.w3.org/1999/xhtml">
<head>
  <title>Resource bar</title>
  <link rel="stylesheet" href="/vrtx/__vrtx/static-resources/themes/default/default.css" type="text/css" /> 
  <!--[if IE 7]>
    <link rel="stylesheet" href="/vrtx/__vrtx/static-resources/themes/default/default-ie7.css" type="text/css" /> 
  <![endif]--> 
  <!--[if lte IE 6]>
    <link rel="stylesheet" href="/vrtx/__vrtx/static-resources/themes/default/default-ie6.css" type="text/css" /> 
  <![endif]--> 
  
  <style type="text/css">
    body {
      min-width: 0;
    }
  </style>
  
  <script type="text/javascript" src="/vrtx/__vrtx/static-resources/jquery/jquery.min.js"></script> 
  <script type="text/javascript" src="/vrtx/__vrtx/static-resources/jquery/plugins/ui/jquery-ui-1.8.19.custom/js/jquery-ui-1.8.19.custom.min.js"></script> 
  <script type="text/javascript" src="/vrtx/__vrtx/static-resources/js/cross-doc-com-link.js"></script>
  <script type="text/javascript" src="/vrtx/__vrtx/static-resources/js/admin-enhancements.js"></script> 
  <script type="text/javascript"><!--
    var crossDocComLink = new CrossDocComLink();
    crossDocComLink.setUpReceiveDataHandler();
    
    $(document).ready(function() {
      vrtxAdmin.completeFormAsync({
        selector: "form[name=unlockForm] input[type=submit]",
        isReplacing: false,
        updateSelectors: ["#titleContainer"],
        funcComplete: function() { crossDocComLink.postCmdToParent("redirect|.?vrtx=admin") },
        post: true
      });
    });
  // -->
  </script>
</head>
<body>

<@resBar.gen resource resourceMenuLeft resourceMenuRight />

</body>
</html>