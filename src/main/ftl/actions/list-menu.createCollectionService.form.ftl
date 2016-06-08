<#ftl strip_whitespace=true output_format="HTML" auto_esc=true>
<#attempt>
<#import "/spring.ftl" as spring />
<#import "/lib/vtk.ftl" as vrtx />
<#import "/lib/actions.ftl" as actionsLib />

<#if createCollectionForm?exists && !createCollectionForm.done>
  <div class="expandedForm vrtx-admin-form">
    <form name="createCollectionService" id="createCollectionService-form" action="${createCollectionForm.submitURL}" method="post">
      <h3 class="nonul"><@vrtx.msg code="actions.createCollectionService" default="Create collection"/></h3>
      <h4><@vrtx.msg code="actions.createCollectionService.subtitle" default="Choose a folder type"/></h4>
      <@spring.bind "createCollectionForm" + ".sourceURI" /> 
      <#assign sourceURIBind = spring.status.value?default("")>
      <@actionsLib.genErrorMessages spring.status.errorMessages />
        <#assign newColTitle = "">
        <#assign newColName = "">
        <#-- Set the name of the new file to whatever the user already has supplied-->
        <#if createCollectionForm.title?exists>
          <#assign newColTitle = createCollectionForm.title>
        </#if>
        <#if createCollectionForm.name?exists>
          <#assign newColName = createCollectionForm.name>
        </#if>
      <#if templates?has_content>
        <ul class="radio-buttons">
          <@vrtx.formRadioButtons "createCollectionForm.sourceURI", templates, "<li>"?no_esc, "</li>"?no_esc />
        </ul>
        <button id="initCreateChangeTemplate" type="button" onclick="createChangeTemplate(true)"></button>
        
        <#-- If POST is not AJAX (otherwise it would be a funcComplete() in completeAsyncForm()) -->
        <script type="text/javascript"><!--
          $(document).ready(createFuncComplete);
        // -->
        </script>
      </#if>

      <@spring.bind "createCollectionForm" + ".title" />
      <#assign titleBind = spring.status.expression>
      <@actionsLib.genErrorMessages spring.status.errorMessages />
      <@spring.bind "createCollectionForm" + ".name" />
      <#assign nameBind = spring.status.expression>
      <@actionsLib.genErrorMessages spring.status.errorMessages />

      <div id="vrtx-div-collection-title">
        <h4 class="vrtx-admin-label"><@vrtx.msg code="actions.createCollectionService.title" default="Title" /></h4>
        <input class="vrtx-textfield" type="text" id="${titleBind}" name="${titleBind}" value="${newColTitle}" size="40" />
      </div>
      
      <div id="vrtx-div-collection-name">
        <h4 class="vrtx-admin-label"><@vrtx.msg code="actions.createCollectionService.collection-name" default="Folder name" /></h4>
        <input class="vrtx-textfield" type="text" id="${nameBind}" name="${nameBind}" value="${newColName}" size="15" maxlength="50" />
        <@spring.bind "createCollectionForm" + ".publish" />
        <#assign publishBind = spring.status.expression>
        <@actionsLib.genErrorMessages spring.status.errorMessages />
        <div class="vrtx-checkbox" id="vrtx-checkbox-hide-from-navigation">
          <input type="checkbox"  id="${publishBind}" name="${publishBind}" checked />
          <label for="publish"><@vrtx.msg code="publish.action.publish" default="Publish" /></label>
          <abbr tabindex="0" class="tooltips" title="<@vrtx.msg code="unpublishedCollection.info" />"></abbr>
        </div>
      </div>

      <@actionsLib.genOkCancelButtons "save" "cancelAction" "actions.createCollectionService.save" "actions.createCollectionService.cancel" />
    </form>
  </div>
</#if>

<#recover>
${.error}
</#recover>
