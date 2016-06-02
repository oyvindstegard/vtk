<#ftl strip_whitespace=true output_format="HTML" auto_esc=true>
<#--
  - File: plaintext-edit.ftl
  - 
  - Description: HTML page that displays a form for editing the
  - contents of a plain-text resource
  - 
  - Required model data:
  -  pingURL
  - Optional model data:
  -
  -->
<#import "/lib/vtk.ftl" as vrtx />
<#if !plaintextEditForm?exists>
  <#stop "Unable to render model: required submodel
  'plaintextEditForm' missing">
</#if>

<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<html xmlns="http://www.w3.org/1999/xhtml">
<head>
  <title>Plain text edit</title>
  <script type="text/javascript" src="${md5jsURL}"></script>
  <script type="text/javascript" src="/vrtx/__vrtx/static-resources/jquery/plugins/jquery.hotkeys.js"></script> 
  <script type="text/javascript"><!--
  
    var UNSAVED_CHANGES_CONFIRMATION = '<@vrtx.msg code='manage.unsavedChangesConfirmation' />';
    var ajaxSaveText = "<@vrtx.msg code='editor.save-plaintext-edit-ajax-loading-title' />";

    var before = null;
    var saveButton = false;
    
    function performSave() {
      saveButton = true;
      return true;
    }
    
    function saveInitChangesInPlaintextEditor() {
      before = hex_md5(document.getElementById("foo").value);
    }

    function unsavedChangesInPlaintextEditorMessage() {
      if (saveButton) return;
      var now = hex_md5(document.getElementById("foo").value);
      if (before == now) {
        return;
      }
      return UNSAVED_CHANGES_CONFIRMATION;
    }
    
    window.onload = saveInitChangesInPlaintextEditor;
    window.onbeforeunload = unsavedChangesInPlaintextEditorMessage;

    // -->
  </script>

</head>
<body id="vrtx-edit-plaintext">
  <div>
  
    <#assign backupURL = vrtx.linkConstructor(".", 'copyBackupService') />
    <#assign backupViewURL = vrtx.relativeLinkConstructor("", 'viewService') />
    <form id="backupForm" action="${backupURL}" method="post" accept-charset="UTF-8">
      <@vrtx.csrfPreventionToken url=backupURL />
      <input type="hidden" name="uri" value="${backupViewURL}" />
    </form>
  
    <form id="editor" action="${plaintextEditForm.submitURL}" method="post">
      <textarea id="foo" name="content" rows="30" cols="80">${plaintextEditForm.content}</textarea>
      <div class="vrtx-edit-plaintext-submit-buttons submitButtons">
        <input class="vrtx-button vrtx-save-button" type="submit" id="saveViewAction" name="saveViewAction" value="<@vrtx.msg code="plaintextEditForm.saveAndView" default="Save and view"/>" />
        <input class="vrtx-focus-button vrtx-save-button" type="submit" id="saveAction" name="saveAction" value="<@vrtx.msg code="plaintextEditForm.save" default="Save"/>" />
        <input class="vrtx-button" type="submit" id="cancelAction" name="cancelAction" value="<@vrtx.msg code="plaintextEditForm.cancel" default="Cancel"/>" />
        <#if plaintextEditForm.tooltips?exists>
          <#list plaintextEditForm.tooltips as tooltip>
           <div class="contextual-help">
             <a href="javascript:void(0);" onclick="javascript:open('${tooltip.url}', 'componentList', 'width=650, height=450, resizable=yes, right=0, top=0, screenX=0, screenY=0, scrollbars=yes');">
               <@vrtx.msg code=tooltip.messageKey default=tooltip.messageKey/>
             </a>
           </div>
          </#list>
        </#if>
      </div>
    </form>
  </div>
</body>
</html>
