<#ftl strip_whitespace=true output_format="HTML" auto_esc=true>
<#--
  - File: manage-collectionlisting.ftl
  - 
  - Description: A HTML page that displays a collection listing.
  - 
  - Required model data:
  -  
  - Optional model data:
  -
  -->
<#import "/lib/vtk.ftl" as vrtx />
<#import "/lib/collectionlisting.ftl" as col />

<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<html xmlns="http://www.w3.org/1999/xhtml">
<head>
  <title>Manage: collection listing</title>
</head>
<body id="vrtx-manage-collectionlisting">
  <#assign copyTitle = vrtx.getMsg("tabMenuRight.copyResourcesService") />
  <#assign moveTitle = vrtx.getMsg("tabMenuRight.moveResourcesService") />
  <#assign deleteTitle = vrtx.getMsg("tabMenuRight.deleteResourcesService") />
  <#assign publishTitle = vrtx.getMsg("tabMenuRight.publishResourcesService") />
  <#assign unpublishTitle = vrtx.getMsg("tabMenuRight.unpublishResourcesService") />
  
  <#assign moveUnCheckedMessage = vrtx.getMsg("tabMenuRight.moveUnCheckedMessage",
         "You must check at least one element to move") />
         
  <#assign copyUnCheckedMessage = vrtx.getMsg("tabMenuRight.copyUnCheckedMessage",
         "You must check at least one element to copy") />
         
  <script type="text/javascript"><!-- 
    var moveUncheckedMessage = '${moveUnCheckedMessage}';
    var copyUncheckedMessage = '${copyUnCheckedMessage}';
    
    var confirmAnd = '${vrtx.getMsg("tabMenuRight.resourcesAnd")}';
    var confirmMore = '${vrtx.getMsg("tabMenuRight.resourcesMore")}';
           
    var confirmDeleteTitle = '${vrtx.getMsg("tabMenuRight.deleteConfirmTitle")}'; 
    var confirmDelete = '${vrtx.getMsg("tabMenuRight.deleteResourcesMessage")}';         
    var deleteUncheckedMessage = '${vrtx.getMsg("tabMenuRight.deleteUnCheckedMessage")}';
    
    var confirmPublishTitle = '${vrtx.getMsg("tabMenuRight.publishConfirmTitle")}';
    var confirmPublish = '${vrtx.getMsg("tabMenuRight.publishResourcesMessage")}';         
    var publishUncheckedMessage = '${vrtx.getMsg("tabMenuRight.publishUnCheckedMessage")}'; 
    
    var confirmUnpublishTitle = '${vrtx.getMsg("tabMenuRight.unpublishConfirmTitle")}'; 
    var confirmUnpublish = '${vrtx.getMsg("tabMenuRight.unpublishResourcesMessage")}';         
    var unpublishUncheckedMessage = '${vrtx.getMsg("tabMenuRight.unpublishUnCheckedMessage")}'; 
    
    var copyTitle = '${copyTitle}';
    var moveTitle = '${moveTitle}';
    var deleteTitle = '${deleteTitle}';
    var publishTitle = '${publishTitle}';
    var unpublishTitle = '${unpublishTitle}';
    
    var moreTitle = '${vrtx.getMsg("tabMenuRight.moreTitle")}';
    
    var multipleFilesInfoText = '<strong>${vrtx.getMsg("tabMenuRight.fileUploadMultipleInfo.line1")}</strong><br />'
                              + '${vrtx.getMsg("tabMenuRight.fileUploadMultipleInfo.line2")}';
    var fileUploadMoreFilesTailMessage = '${vrtx.getMsg("tabMenuRight.fileUploadMoreFilesTailMessage")}';
    
    var createShowMoreTemplates = '${vrtx.getMsg("actions.createDocumentService.showMore")}';
  //-->
  </script>

  <@col.listCollection
     withForm=true
     action=action.submitURL?string
     submitActions={ "copy-resources": copyTitle,
                     "move-resources": moveTitle,
                     "delete-resources": deleteTitle,
                     "publish-resources": publishTitle,
                     "unpublish-resources": unpublishTitle
                   }/>
</body>
</html>
