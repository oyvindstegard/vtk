<#ftl strip_whitespace=true output_format="XHTML" auto_esc=true>
<#--
  - File: ooxml-editor.ftl
  -
  -->
<#import "/lib/vtk.ftl" as vrtx />

<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<html xmlns="http://www.w3.org/1999/xhtml">
  <head>
    <title>OoXml Editor</title>
    <script type="text/javascript" src="/vrtx/__vrtx/static-resources/jquery/plugins/jquery.hotkeys.js"></script>
    <script type="text/javascript"><!--
      $(function() {
         var agent = navigator.userAgent.toLowerCase();         
         var isWin = ((agent.indexOf("win") != -1) || (agent.indexOf("16bit") != -1));
         var isLinux = (agent.indexOf("linux") != -1);
         var isMac = (agent.indexOf("mac") != -1) && !isWin && !isLinux;
         if(isWin) {
           $("#vrtx-edit-mac").hide(0); 
         } else if(isMac) {
           $("#vrtx-edit-win").hide(0); 
         }
         if(!(isWin || isMac)) {
           $("#vrtx-edit-win-mac").hide(0);
         }
         if ((($.browser.msie && $.browser.version >= 7) || /.*trident\/7\.0.*/.test(agent)) && isWin) {
           var openWebdavWrp = $("#vrtx-open-webdav-wrapper");
           openWebdavWrp.show(0);
           openWebdavWrp.on("click", "#vrtx-open-webdav", function(e) {
             var openOffice = new ActiveXObject("Sharepoint.OpenDocuments.1").EditDocument(this.href);
             e.stopPropagation();
             e.preventDefault();
           });
           
           $(document).bind('keydown', 'ctrl+o', function(e) {
             $("#vrtx-open-webdav").click();
             e.preventDefault();
             return false;
           });
         }
      });
    // -->
    </script>
  </head>
  <body id="vrtx-ooxml-editor">
    <#assign resource = resourceContext.currentResource />
    <#assign resourceTypeName = vrtx.resourceTypeName(resource) />
    <#assign document = vrtx.getMsg("resourcetype.name.structured-document") />
    <#assign webdavUrl = vrtx.linkConstructor(resource.URI, 'webdavService') />
    
    <h2><@vrtx.msg code="tabs.editorService" /> ${document?lower_case}</h2>
    
    <div id="vrtx-open-webdav-wrapper">
      <h3>${vrtx.getMsg('editor.ooxml.ie-edit')}</h3>
      <a id="vrtx-open-webdav" class="vrtx-button" href="${webdavUrl}"><@vrtx.msg code="tabs.editorService" /> <@vrtx.msg code="editor.ooxml.ie-edit-in" /> ${resourceTypeName}</a>
    </div>
    
    <h3>${vrtx.getMsg('editor.ooxml.step-by-step')}</h3>
    
    <ol class="vrtx-help-step-by-step">
      <li>${vrtx.getMsg('editor.ooxml.step-by-step.mark-webdav')}<br />
         <span class="vrtx-help-step-by-step-url">${webdavUrl}</span>
      </li>
      <li>Start <span class='vrtx-help-step-by-step-cmd'>${resourceTypeName}</span></li>
      <li id="vrtx-edit-win-mac">
          <span id="vrtx-edit-win">${vrtx.getMsg('editor.ooxml.step-by-step.win')}</span>
          <span id="vrtx-edit-mac">${vrtx.getMsg('editor.ooxml.step-by-step.mac')}</span>
      </li>
      <li>${vrtx.getMsg('editor.ooxml.step-by-step.paste-webdav')}</li>
    </ol>
    
  </body>
</html>
