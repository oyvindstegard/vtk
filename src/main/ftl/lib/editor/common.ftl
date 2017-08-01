<#ftl strip_whitespace=true output_format="HTML" auto_esc=true>
<#--
  - File: common.ftl
  -
  - Add common JS old and new editor.ftl
  -
  -->
  
<#import "../vtk.ftl" as vrtx />

<#macro addCkScripts>
  <link rel="stylesheet" type="text/css" href="${webResources}/jquery/plugins/jquery.autocomplete.css" />
  <link rel="stylesheet" type="text/css" href="${webResources}/js/autocomplete/autocomplete.override.css" />
  <script type="text/javascript" src="${fckeditorBase.url}/ckeditor.js"></script>
  <script type="text/javascript" src="${webResources}/js/plugins/mustache.js"></script>
  <script type="text/javascript" src="${jsBaseURL}/vrtx-accordions.js"></script>
  <script type="text/javascript" src="${jsBaseURL}/editor.js"></script>
  <script type="text/javascript" src="${fckeditorBase.url}/adapters/jquery.js"></script>
  <script type='text/javascript' src='${webResources}/jquery/plugins/jquery.autocomplete.js'></script>
  <script type='text/javascript' src='${webResources}/js/autocomplete/autocomplete.js'></script>
  <script type='text/javascript' src='${webResources}/js/autocomplete/autocomplete-permissions.js'></script>
</#macro>

<#macro addCommonScripts language oldEditor=false>

  <script type="text/javascript"><!--
    window.messages = window.messages || {};
    messages.validationError = "<@vrtx.msg code='editor.error.validation' />";
    messages.tooLongFieldPre = "<@vrtx.msg code='editor.too-long-field-pre' />";
    messages.tooLongFieldPost = "<@vrtx.msg code='editor.too-long-field-post' />";
    
    vrtxAdmin.multipleFormGroupingMessages = {
      add: "${vrtx.getMsg('editor.add', 'Add')}",
      remove: "${vrtx.getMsg('editor.remove', 'Remove')}",
      moveUp: "${vrtx.getMsg('editor.move-up', 'Up')}",
      moveDown: "${vrtx.getMsg('editor.move-down', 'Down')}",
      browse: "${vrtx.getMsg('editor.browseImages', 'Browse ...')}",
      limitReached: "${vrtx.getMsg('editor.manually-approve-aggregation-limit-reached', 'You\'ve reached the limit of websites to add contents from.')}"
    };
	vrtxAdmin.multipleFormGroupingPaths = {
	  <#if fckeditorBase??>
	  baseBrowserURL: "${fckeditorBase.url}",
	  baseFolderURL: "${baseFolder}",
	  baseDocURL: "${fckeditorBase.documentURL}",
	  basePath: "${fckBrowse.url.pathRepresentation}"
	  </#if>
	};
	if(vrtxAdmin.hasFreeze) { // Make immutables
	  Object.freeze(vrtxAdmin.multipleFormGroupingMessages);
	  Object.freeze(vrtxAdmin.multipleFormGroupingPaths);
	}
  // -->
  </script>
  
  <#if oldEditor>
    <!-- JQuery UI (used for old datepicker) -->
    <script type="text/javascript" src="${webResources}/jquery/plugins/ui/jquery-ui-${jQueryUiVersion}.custom/jquery-ui.min.js"></script>
    <#if language = "no">
      <script type="text/javascript" src="${webResources}/jquery/plugins/ui/jquery-ui-${jQueryUiVersion}.custom/jquery.ui.datepicker-no.js"></script>
    <#elseif language = "nn">
      <script type="text/javascript" src="${webResources}/jquery/plugins/ui/jquery-ui-${jQueryUiVersion}.custom/jquery.ui.datepicker-nn.js"></script>
    </#if>
    <script type="text/javascript" src="${jsBaseURL}/datepicker/datepicker-admin-old.js"></script>
  <#else>
    <script type="text/javascript" src="${jsBaseURL}/datepicker/vrtx-datepicker.js"></script>
  </#if>
</#macro>

<#macro createEditor content completeEditor=false withoutSubSuper=false simpleHTML=false>
  <script type="text/javascript"><!--
      try {
        var editorObj = {
          name: '${content}',
          isCompleteEditor: ${completeEditor?string},
          isWithoutSubSuper: ${withoutSubSuper?string},
          defaultLanguage: '<@vrtx.requestLanguage />',
          cssFileList: cssFileList,
          simple: ${simpleHTML?string}
        }
        if (typeof vrtxEditor !== "undefined") {
          vrtxEditor.richtextEditorFacade.editorsForInit.push(editorObj);
	    } else {
	      $(document).ready(function() {
	        vrtxEditor.richtextEditorFacade.setup(editorObj);
	      });
	    }
	  } catch (e) {
	    if(typeof console !== "undefined" && console.log) {
	      console.log(e);
	    }
	  }
  //-->
  </script>
</#macro>
