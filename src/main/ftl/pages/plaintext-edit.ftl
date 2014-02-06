<#ftl strip_whitespace=true>

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
<#import "/lib/vortikal.ftl" as vrtx />
<#if !plaintextEditForm?exists>
  <#stop "Unable to render model: required submodel
  'plaintextEditForm' missing">
</#if>

<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<html xmlns="http://www.w3.org/1999/xhtml">
<head>
  <title>Plain text edit</title>
  <script type="text/javascript" src="${md5jsURL?html}"></script>
  <script type="text/javascript" src="/vrtx/__vrtx/static-resources/jquery/plugins/jquery.hotkeys.js"></script> 
  <script type="text/javascript"><!--
    var ajaxSaveText = "<@vrtx.msg code='editor.save-plaintext-edit-ajax-loading-title' />";

    $(document).ready(function() {
      $("#app-content").on("click", "#saveViewAction", function(e) {
        performSave();
      });
    });

    var before = null;
    var saveButton = false;
    function performSave() {
       saveButton = true;
       return true;
    }
    
    window.onload = function() {
       before = hex_md5(document.getElementById("plaintext").value);
    }

    window.onbeforeunload = function() {
       if (saveButton) return;
       var now = hex_md5(document.getElementById("plaintext").value);
       if (before == now) {
          return;
       }
       return '<@vrtx.msg code='manage.unsavedChangesConfirmation' />';
    }

    // -->
  </script>

</head>
<body id="vrtx-edit-plaintext">
  <div>
  
    <#assign backupURL = vrtx.linkConstructor(".", 'copyBackupService') />
    <#assign backupViewURL = vrtx.relativeLinkConstructor("", 'viewService') />
    <form id="backupForm" action="${backupURL?html}" method="post" accept-charset="UTF-8">
      <@vrtx.csrfPreventionToken url=backupURL />
      <input type="hidden" name="uri" value="${backupViewURL?html}" />
    </form>
  
    <form id="editor" action="${plaintextEditForm.submitURL}" method="post">
      <textarea id="plaintext" name="content" rows="30" cols="80">${plaintextEditForm.content?html}</textarea>
      <div class="vrtx-edit-plaintext-submit-buttons submitButtons">
        <input class="vrtx-button vrtx-save-button" type="submit" id="saveViewAction" name="saveViewAction" value="<@vrtx.msg code="plaintextEditForm.saveAndView" default="Save and view"/>" />
        <input class="vrtx-focus-button vrtx-save-button" type="submit" id="saveAction" name="saveAction" value="<@vrtx.msg code="plaintextEditForm.save" default="Save"/>" />
        <input class="vrtx-button" type="submit" id="cancelAction" name="cancelAction" value="<@vrtx.msg code="plaintextEditForm.cancel" default="Cancel"/>" />
        <#if plaintextEditForm.tooltips?exists>
          <#list plaintextEditForm.tooltips as tooltip>
           <div class="contextual-help">
             <a href="javascript:void(0);" onclick="javascript:open('${tooltip.url?html}', 'componentList', 'width=650, height=450, resizable=yes, right=0, top=0, screenX=0, screenY=0, scrollbars=yes');">
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