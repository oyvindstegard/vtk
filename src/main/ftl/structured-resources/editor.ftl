<#import "/lib/ping.ftl" as ping />
<#import "/lib/vortikal.ftl" as vrtx />
<#import "include/scripts.ftl" as scripts />
<#import "/lib/editor/common.ftl" as editor />
<#import "editor/vrtx-json-javascript.ftl" as vrtxJSONJavascript />
<#import "vrtx-types/vrtx-json-common.ftl" as vrtxJSONCommon />

<html>
<head>

  <title>Edit structured resource</title>
  <@ping.ping url=pingURL['url'] interval=300 />
  <@editor.addCkScripts />
  <@vrtxJSONJavascript.script />
  
  <script type="text/javascript" src="${jsBaseURL?html}/plugins/shortcut.js"></script>
  <script type="text/javascript" src="${jsBaseURL?html}/admin-ck-helper.js"></script>
  <script type="text/javascript" src="${jsBaseURL?html}/admin-prop-change.js"></script>
  
  <#assign language = vrtx.getMsg("eventListing.calendar.lang", "en") />
  
  <script type="text/javascript"><!--
  	
 	shortcut.add("Ctrl+S",function() {
  		$("#updateAction").click();
	});
	
	$(document).ready(function() {
      initDatePicker("${language}");
    });
  
    window.onbeforeunload = unsavedChangesInEditorMessage;
    UNSAVED_CHANGES_CONFIRMATION = "<@vrtx.msg code='manage.unsavedChangesConfirmation' />";
    COMPLETE_UNSAVED_CHANGES_CONFIRMATION = "<@vrtx.msg code='manage.completeUnsavedChangesConfirmation' />";
    
    function performSave() {
        saveDateAndTimeFields();
        if (typeof(MULTIPLE_INPUT_FIELD_NAMES) != "undefined") {
            saveMultipleInputFields();
        }
        NEED_TO_CONFIRM = false;
    }
    function cSave() {
        document.getElementById("form").setAttribute("action", "#submit");
        performSave();
    }
    //-->
  </script>
  <script type="text/javascript" src="${jsBaseURL?html}/imageref.js"></script>
  
  <@editor.addDatePickerScripts />
  
  <#if form.resource.type.scripts?exists>
    <@scripts.includeScripts form.resource.type.scripts />
  </#if>
  
  <#global baseFolder = "/" />
  <#if resourceContext.parentURI?exists>
    <#global baseFolder = resourceContext.parentURI?html />
  </#if>
  
</head>
<body>

<#assign locale = springMacroRequestContext.getLocale() />

<#assign header = form.resource.getLocalizedMsg("header", locale, null) />
<h2>${header}</h2>

<div class="submit-extra-buttons">
    <a class="help-link" href="${editorHelpURL?html}" target="new_window"><@vrtx.msg code="editor.help"/></a>
    <a class="help-link" href="${form.listComponentServiceURL?html}" target="new_window"><@vrtx.msg code="plaintextEdit.tooltip.listDecoratorComponentsService" /></a>
    <input type="button" onClick="$('#updateViewAction').click()" value="${vrtx.getMsg("editor.saveAndView")}" />
    <input type="button" onClick="$('#updateAction').click()"  value="${vrtx.getMsg("editor.save")}" />
    <input type="button" onClick="$('#cancelAction').click()"  value="${vrtx.getMsg("editor.cancel")}" />
</div>  

<form action="${form.submitURL?html}" method="post">

<#list form.elements as elementBox>

  <#if elementBox.formElements?size &gt; 1>
    <#assign groupClass = "vrtx-grouped" />
    <#if elementBox.metaData['horizontal']?exists>
      <#assign groupClass = groupClass + "-horizontal" />
    <#elseif elementBox.metaData['vertical']?exists>
      <#assign groupClass = groupClass + "-vertical" />
    </#if>
    <#if elementBox.name?exists>
      <#assign groupName = elementBox["name"] />
      <#assign groupClass = groupClass + " ${groupName?string}" />
       <div class="${groupClass}">
       <#assign localizedHeader = form.resource.getLocalizedMsg(elementBox.name, locale, null) />
       <div class="header">${localizedHeader}</div>
    <#else>
       <div class="${groupClass}">
    </#if>
  </#if>

  <#list elementBox.formElements as elem>
    <@vrtxJSONCommon.printPropertyEditView form elem locale />
  </#list>
  
  <#if elementBox.formElements?size &gt; 1>
    </div>
  </#if>
  
</#list>
<div class="submit">
    <input type="submit" id="updateViewAction" onClick="performSave();" name="updateViewAction" value="${vrtx.getMsg("editor.saveAndView")}" />
    <input type="submit" id="updateAction" onClick="performSave();" name="updateAction" value="${vrtx.getMsg("editor.save")}" />
    <input type="submit" onClick="performSave();" name="cancelAction" id="cancelAction" value="${vrtx.getMsg("editor.cancel")}" />
</div>
</form>
</body>
</html>
