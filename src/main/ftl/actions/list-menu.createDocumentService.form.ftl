<#ftl strip_whitespace=true>
<#attempt>
<#import "/spring.ftl" as spring />
<#import "/lib/vortikal.ftl" as vrtx />
<#import "/lib/actions.ftl" as actionsLib />

<#if createDocumentForm?exists && !createDocumentForm.done>
  <div class="expandedForm vrtx-admin-form">
    <form name="createDocumentService" id="createDocumentService-form" action="${createDocumentForm.submitURL?html}"
          method="post" accept-charset="utf-8">
      <h3><@vrtx.msg code="actions.createDocumentService" default="Create Document"/></h3>
      <h4><@vrtx.msg code="actions.createDocumentService.subtitle" default="Choose a template"/></h4>
      <#compress>
        <@spring.bind "createDocumentForm" + ".sourceURI" />
        <#assign sourceURIBind = spring.status.value?default("")>
        <@actionsLib.genErrorMessages spring.status.errorMessages />
        <#assign newDocTitle = "">
        <#assign newDocName = "">
        <#-- Set the name of the new file to whatever the user already has supplied-->
        <#if createDocumentForm.title?exists>
          <#assign newDocTitle = createDocumentForm.title>
        </#if>
        <#if createDocumentForm.name?exists>
          <#assign newDocName = createDocumentForm.name>
        </#if>
        <#if createDocumentForm.isIndex?exists>
          <#assign isIndex = createDocumentForm.isIndex>
        </#if>
        <#if templates?has_content>
          <ul class="radio-buttons">
            <@vrtx.formRadioButtons "createDocumentForm.sourceURI", templates, "<li>", "</li>", descriptions, titles, true />
          </ul>
          <button id="initCreateChangeTemplate" type="button" onclick="createChangeTemplate(<#if (titles?has_content && titles[sourceURIBind]?exists)>${titles[sourceURIBind]?string}<#else>false</#if>)"></button>
          
          <#-- If POST is not AJAX (otherwise it would be a funcComplete() in completeAsyncForm()) -->
          <script type="text/javascript"><!--
            $(document).ready(createFuncComplete);
          // -->
          </script>
        </#if>
      </#compress>

      <@spring.bind "createDocumentForm" + ".title" /> 
      <#assign titleBind = spring.status.expression>
      <@actionsLib.genErrorMessages spring.status.errorMessages />
      <@spring.bind "createDocumentForm" + ".name" /> 
      <#assign nameBind = spring.status.expression>
      <@actionsLib.genErrorMessages spring.status.errorMessages />
      <@spring.bind "createDocumentForm" + ".isIndex" /> 
      <#assign isIndexBind = spring.status.expression>
      <@actionsLib.genErrorMessages spring.status.errorMessages />
      
       <script type="text/javascript"><!--
         $(document).ready(function() {
           var nameField = $("#${nameBind}");
           vrtxAdmin.createDocumentFileName = nameField.val();
           createCheckUncheckIndexFile(nameField, $("#${isIndexBind}"));
         });
       // -->
       </script>
       
      <div id="vrtx-div-file-title">
        <h4 class="vrtx-admin-label"><@vrtx.msg code="actions.createDocumentService.title" default="Title" /></h4>
        <div class="vrtx-textfield" id="vrtx-textfield-file-title">
          <input type="text" id="${titleBind?html}" name="${titleBind?html}" value="${newDocTitle?html}" size="40" />
        </div>
      </div>

      <div id="vrtx-div-file-name">
        <h4 class="vrtx-admin-label"><@vrtx.msg code="actions.createDocumentService.filename" default="Filename" /></h4>
        <div class="vrtx-textfield" id="vrtx-textfield-file-name">
          <input type="text" id="${nameBind?html}" name="${nameBind?html}" value="${newDocName?html}" size="15" maxlength="50" />
        </div>
      </div>
      <span id="vrtx-textfield-file-type"></span>
      <div class="vrtx-checkbox" id="vrtx-checkbox-is-index">
        <input type="checkbox"  id="${isIndexBind}" name="${isIndexBind}" <#if isIndex>checked="checked"</#if> />
        <label for="${isIndexBind?html}"><@vrtx.msg code="actions.createDocumentService.index" default="Is index-page" /></label>
        <abbr title="${vrtx.getMsg("actions.tooltip.isIndexPage")}" class="resource-prop-info"></abbr>
      </div>

      <@actionsLib.genOkCancelButtons "save" "cancelAction" "actions.createDocumentService.save" "actions.createDocumentService.cancel" />
    </form>
  </div>
</#if>
 
<#recover>
${.error}
</#recover>