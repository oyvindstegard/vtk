<#ftl strip_whitespace=true output_format="HTML" auto_esc=true>

<#import "/spring.ftl" as spring />
<#import "/lib/vtk.ftl" as vrtx />
<#import "/lib/actions.ftl" as actionsLib />
<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<html xmlns="http://www.w3.org/1999/xhtml">
  <head>
    <title>${(title.title)?default(resourceContext.currentResource.name)}</title>
  </head>
  <body>
    <#if createDocumentForm?exists && !createDocumentForm.done>
      <div class="expandedForm vrtx-admin-form">
        <form name="createDocumentService" id="createDocumentService-form" action="${createDocumentForm.submitURL}"
              method="post" accept-charset="utf-8">
          <h3><@vrtx.msg code="actions.createDocumentService" default="Create Document"/></h3>
          
          <div id="vrtx-create-document-templates">
            
            <@spring.bind "createDocumentForm" + ".isRecommended" /> 
            <#assign isRecommendedBind = spring.status.value>
            <@actionsLib.genErrorMessages spring.status.errorMessages />

            <#if !isRecommendedBind>
              <#assign subTitle = vrtx.getMsg("actions.createDocumentService.subtitle", "Choose a template") />
              <#assign splitAfterRecommendedTitle = "" />
            <#else>
              <#assign subTitle = vrtx.getMsg("actions.createDocumentService.subtitle.recommended", "Recommended template in this folder") />
              <#assign splitAfterRecommendedTitle></ul><div id="vrtx-create-templates-not-recommended">
              <h4><@vrtx.msg code="actions.createDocumentService.subtitle.not-recommended" default="Other available templates" /></h4>
              <ul class="radio-buttons">
        </#assign>
            </#if>

            <#if splitAfterRecommendedTitle?is_markup_output>
              <#assign splitAfterRecommendedTitle = splitAfterRecommendedTitle?markup_string />
            </#if>
            
            <h4>${subTitle}</h4>
            <#compress>
              <@spring.bind "createDocumentForm" + ".sourceURI" />
              <#assign sourceURIBind = spring.status.value!"">
              <@actionsLib.genErrorMessages spring.status.errorMessages />
              <#assign newDocTitle = "">
              <#assign newDocName = "">
              <#-- Set the name of the new file to whatever the user already has supplied-->
              <#if (createDocumentForm.title)?has_content>
                <#assign newDocTitle = createDocumentForm.title>
              </#if>
              <#if (createDocumentForm.name)?has_content>
                <#assign newDocName = createDocumentForm.name>
              </#if>
              <#if createDocumentForm.isIndex?exists>
                <#assign isIndex = createDocumentForm.isIndex>
              </#if>

              <#if templates?has_content>
                <ul class="radio-buttons">
                  <@vrtx.formRadioButtons "createDocumentForm.sourceURI", templates, 
                                          "<li>"?no_esc, "</li>"?no_esc, descriptions, 
                    titles, true, "", splitAfterRecommendedTitle />
                </ul>
                <#if isRecommendedBind>
              </div>
                </#if>
          </div>
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
              <input class="vrtx-textfield" type="text" id="${titleBind}" name="${titleBind}" value="${newDocTitle}" size="40" />
            </div>

            <div id="vrtx-div-file-name">
              <h4 class="vrtx-admin-label"><@vrtx.msg code="actions.createDocumentService.filename" default="Filename" /></h4>
              <input class="vrtx-textfield" type="text" id="${nameBind}" name="${nameBind}" value="${newDocName}" size="15" maxlength="50" />
              <span id="vrtx-textfield-file-type"></span>
              <div class="vrtx-checkbox" id="vrtx-checkbox-is-index">
                <input type="checkbox"  id="${isIndexBind}" name="${isIndexBind}" <#if isIndex>checked="checked"</#if> />
                <label for="${isIndexBind}"><@vrtx.msg code="actions.createDocumentService.index" default="Is index-page" /></label>
                <abbr tabindex="0" title="${vrtx.getMsg("actions.tooltip.isIndexPage")}" class="tooltips"></abbr>
              </div>
            </div>
            
            <@actionsLib.genOkCancelButtons "save" "cancelAction" "actions.createDocumentService.save" "actions.createDocumentService.cancel" />
        </form>
      </div>
    </#if>

  </body>
</html>
