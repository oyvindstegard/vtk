<#ftl strip_whitespace=true>
<#-- JSON contract -->

<#macro script>
  <#assign locale = springMacroRequestContext.getLocale() />

  <#assign contentLocale = form.defaultLocale />
  <#if resourceContext.currentResource.contentLanguage?exists>
    <#assign contentLocale = resourceContext.currentResource.getContentLocale() />
  </#if>

  <script type="text/javascript" src="${webResources?html}/jquery/plugins/jquery.scrollTo.min.js"></script>
  <script type="text/javascript"><!--
  
    var browseImagesPreview = '<@vrtx.msg code="editor.image.preview-title" />';
        requestLang = '<@vrtx.requestLanguage />';
   
    var ACCORDION_MOVE_TO_AFTER_CHANGE = null;
    var JSON_ELEMENTS_INITIALIZED = $.Deferred();
    
    $(document).ready(function() {
      if($("form.vrtx-course-schedule").length) return;

      // Retrieve HTML templates
      getMultipleFieldsBoxesTemplates();
      
      // Build JSON elements
      vrtxEditor.multipleBoxesTemplatesContractBuilt = $.Deferred();

      <#assign i = 0 />
      
      <#list form.elements as elementBox>
        <#assign j = 0 />
        <#list elementBox.formElements as elem>
          <#if elem.description.type == "json" && elem.description.isMultiple()>
            vrtxEditor.multipleBoxesTemplatesContract[${i}] = {};
            vrtxEditor.multipleBoxesTemplatesContract[${i}].name = "${elem.name}";
            vrtxEditor.multipleBoxesTemplatesContract[${i}].type = "${elem.description.type}";
            vrtxEditor.multipleBoxesTemplatesContract[${i}].a = [];
            
            <#list elem.description.attributes as jsonAttr>
              vrtxEditor.multipleBoxesTemplatesContract[${i}].a[${j}] = {};
              vrtxEditor.multipleBoxesTemplatesContract[${i}].a[${j}].name = "${jsonAttr.name}";
              vrtxEditor.multipleBoxesTemplatesContract[${i}].a[${j}].type = "${jsonAttr.type}";
              <#if jsonAttr.edithints?exists>
                <#if jsonAttr.edithints['dropdown']?exists>
                  vrtxEditor.multipleBoxesTemplatesContract[${i}].a[${j}].dropdown = true;
                </#if>
              </#if>
              <#if jsonAttr.getValuemap(locale)?exists >
                <#assign valuemap = jsonAttr.getValuemap(locale) />
                <#assign k = 0 />
                var valuemap = [];
                <#list valuemap?keys as key>
                  <#assign optionKey = key />
                  <#if optionKey = '""' >
                    <#assign optionKey = "''" />
                  </#if>
                  valuemap[${k}] = "${optionKey}$${valuemap[key]}";
                  <#assign k = k + 1 />
                </#list>
                vrtxEditor.multipleBoxesTemplatesContract[${i}].a[${j}].valuemap = valuemap;
              </#if>
              vrtxEditor.multipleBoxesTemplatesContract[${i}].a[${j}].title = "${form.resource.getLocalizedMsg(jsonAttr.name, locale, contentLocale, null)}";
              <#assign j = j + 1 />
            </#list>
            <#assign i = i + 1 />
          </#if>
        </#list>
      </#list>

      vrtxEditor.multipleBoxesTemplatesContractBuilt.resolve();
      initJsonMovableElements();
      
    });
  // -->
  </script>
</#macro>
