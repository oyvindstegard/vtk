<#ftl strip_whitespace=true output_format="HTML" auto_esc=true>
<#import "vrtx-boolean.ftl" as vrtxBoolean />
<#import "vrtx-datetime.ftl" as vrtxDateTime />
<#import "vrtx-file-ref.ftl" as vrtxFileRef />
<#import "vrtx-html.ftl" as vrtxHtml />
<#import "vrtx-image-ref.ftl" as vrtxImageRef />
<#import "vrtx-media-ref.ftl" as vrtxMediaRef />
<#import "vrtx-radio.ftl" as vrtxRadio />
<#import "vrtx-string.ftl" as vrtxString />
<#import "vrtx-shared-text.ftl" as vrtxSharedText />
<#import "vrtx-resource-ref.ftl" as vrtxResourceRef />

<#macro printPropertyEditView form elem locale contentLocale>
  <#assign localizedTitle = form.resource.getLocalizedMsg(elem.name, locale, contentLocale, null) />
  <#assign hasEdithint = elem.description.edithints?exists />

  <#switch elem.description.type>

    <#case "string">
      <#assign fieldSize = getEdithintFieldSize(elem) />

      <#assign dropdown = false />
      <#if hasEdithint && elem.description.edithints['dropdown']?exists>
        <#assign dropdown = true />
      </#if>

      <#assign classes = getEdithintClasses(elem) />

      <#if sharedTextProps?? && sharedTextProps[elem.name]?exists>
        <#if  (sharedTextProps[elem.name]?size > 0) >
              <@vrtxSharedText.printPropertyEditView
                title=localizedTitle
                inputFieldName=elem.name
                value=elem.getFormatedValue()
                classes=classes
                inputFieldSize=fieldSize
                tooltip=form.resource.getLocalizedTooltip(elem.name, locale)
                valuemap=elem.description.getValuemap(locale)
                dropdown=dropdown
                defaultValue=elem.getDefaultValue()
              />
        <#else>
            <p>Unable to load shared text '${elem.name}'</p>
        </#if>
      <#else>
        <@vrtxString.printPropertyEditView
          title=localizedTitle
          inputFieldName=elem.name
          value=elem.getFormatedValue()
          classes=classes
          inputFieldSize=fieldSize
          tooltip=form.resource.getLocalizedTooltip(elem.name, locale)
          valuemap=elem.description.getValuemap(locale)
          dropdown=dropdown
          defaultValue=elem.getDefaultValue()
          multiple=elem.description.isMultiple()
        />
      </#if>
      <#break>

    <#case "simple_html">
      <#assign cssClass =  elem.name + " vrtx-simple-html" />
      <#if hasEdithint && elem.description.edithints['size']?exists >
        <#assign cssClass = cssClass + "-" + getEdithintFieldSize(elem) />
      </#if>

      <@vrtxHtml.printPropertyEditView
        title=localizedTitle
        inputFieldName=elem.name
        value=elem.value
        classes=cssClass
        tooltip=form.resource.getLocalizedTooltip(elem.name, locale)
        editor=""
      />
      <@editor.createEditor elem.name />
      <#break>

    <#case "html">

      <#assign cssClass = "vrtx-html" />
      <#assign classes = getEdithintClasses(elem) />
      <#if classes?has_content >
        <#assign cssClass = cssClass + ' ' + classes />
      <#else>
        <#assign cssClass = cssClass + ' ' + elem.name />
      </#if>

      <@vrtxHtml.printPropertyEditView
        title=localizedTitle
        inputFieldName=elem.name
        value=elem.value
        classes=cssClass
        tooltip=form.resource.getLocalizedTooltip(elem.name, locale)
        editor=""
      />
      <@editor.createEditor elem.name true false />
      <#break>

    <#case "boolean">
    
      <#assign classes1 = getEdithintClasses(elem) />
      <#if classes1?has_content >
        <#assign classes1 = elem.name + ' ' + classes1 />
      <#else>
        <#assign classes1 = elem.name />
      </#if>
    
      <@vrtxBoolean.printPropertyEditView
        title=localizedTitle
        inputFieldName=elem.name
        value=elem.value
        classes=classes1
        description=elem.description
        tooltip=form.resource.getLocalizedTooltip(elem.name, locale)
        defaultValue=elem.getDefaultValue()
      />
      <#break>

    <#case "image_ref">
      <@vrtxImageRef.printPropertyEditView
        title=localizedTitle
        inputFieldName=elem.name
        value=elem.value
        name=elem.value
        baseFolder=resourceContext.parentURI
        classes=elem.name
        tooltip=form.resource.getLocalizedTooltip(elem.name, locale)
      />
      <#break>

    <#case "resource_ref">
      <#assign fieldSize = getEdithintFieldSize(elem) />
      <#assign classes = getEdithintClasses(elem) />
      <@vrtxResourceRef.printPropertyEditView
        title=localizedTitle
        inputFieldName=elem.name
        value=elem.getFormatedValue()
        name=elem.value
        baseFolder=resourceContext.parentURI
        classes=elem.name + " " + classes
        tooltip=form.resource.getLocalizedTooltip(elem.name, locale)
        inputFieldSize=fieldSize
      />
      <#break>

    <#case "media_ref">
      <@vrtxMediaRef.printPropertyEditView
        title=localizedTitle
        inputFieldName=elem.name
        value=elem.value
        baseFolder=resourceContext.parentURI
        classes=elem.name
        tooltip=form.resource.getLocalizedTooltip(elem.name, locale)
      />
      <#break>

    <#case "datetime">
      <@vrtxDateTime.printPropertyEditView
        title=localizedTitle
        inputFieldName=elem.name
        value=elem.value
        classes=elem.name
        tooltip=form.resource.getLocalizedTooltip(elem.name, locale)
      />
      <#break>

    <#case "json">

      <#assign editHintClasses = getEdithintClasses(elem, false) />
      <#assign cssClass =  "" />
      <#if editHintClasses?has_content >
        <#assign cssClass = " " + editHintClasses />
      </#if>

      <@printJSONPropertyEditView
        localizedTitle
        elem.name
        elem
        elem.name
        ""
        locale
        form.resource.getLocalizedTooltip(elem.name, locale)
        cssClass
      />
      <#break>

     <#default>
        No editor available for element type ${elem.description.type}

  </#switch>

</#macro>

<#macro printJSONPropertyEditView title inputFieldName elem id tooltip locale inputFieldSize=20 cssClass="">
  <div class="vrtx-json${cssClass}">
    <div id="${id}" class="fieldset">
      <div class="header">${title}</div>
    
      <#if "${tooltip}" != ""><div class="tooltip">${tooltip}</div></#if>

      <#local counter = 0 />
      <#local locale = springMacroRequestContext.getLocale() />
    
      <#-- JSON-property that has no value - we need to create empty fields -->

      <#if !elem.value?exists >
          <div class="vrtx-json-element last" id="vrtx-json-element-${inputFieldName}-${counter}">
            <#list elem.description.attributes as jsonAttr>
              <#assign attrName = jsonAttr.name />
              <#assign tmpName = inputFieldName + "." + attrName + "." + counter />
              <#assign jsonAttrLocalizedTitle = form.resource.getLocalizedMsg(attrName, locale, contentLocale, null) />
              <@printJsonProperyEditView jsonAttr.type jsonAttrLocalizedTitle tmpName "" elem attrName jsonAttr locale />
            </#list>
            <input type="hidden" class="id" value="${counter}" />
            <button class="vrtx-button vrtx-remove-button" title="">${vrtx.getMsg("editor.remove")}</button>
          </div>
        </#if>

        <#-- JSON-property that has a value and is a list / multiple property -->

       <#if elem.value?exists && elem.value?is_enumerable>
         <#list elem.value as map>
           <div class="vrtx-json-element<#if (counter == (elem.value?size - 1))> last</#if>" id="vrtx-json-element-${inputFieldName}-${counter}">
             <#list elem.description.attributes as jsonAttr>
               <#assign attrName = jsonAttr.name />
               <#assign tmpName = inputFieldName + "." + attrName + "." + counter />
               <#assign jsonAttrLocalizedTitle = form.resource.getLocalizedMsg(attrName, locale, contentLocale, null) />
               <#if map[attrName]?exists >
                 <@printJsonProperyEditView jsonAttr.type jsonAttrLocalizedTitle tmpName map[attrName] elem attrName jsonAttr locale />
               <#else>
                 <@printJsonProperyEditView jsonAttr.type jsonAttrLocalizedTitle tmpName "" elem attrName jsonAttr locale />
               </#if>
             </#list>
             
       	     <input type="hidden" class="id" value="${counter}" />
       	     
             <button class="vrtx-button vrtx-remove-button" title="">${vrtx.getMsg("editor.remove")}</button>
       	     <#if !cssClass?contains("vrtx-multiple-immovable")>
               <#if (counter > 0) >
                 <button class="vrtx-button vrtx-move-up-button" title="${vrtx.getMsg("editor.move-up")}"><span class='moveup-arrow'></span></button>
               </#if>
               <#if map_has_next >
                 <button class="vrtx-button vrtx-move-down-button" title="${vrtx.getMsg("editor.move-down")}"><span class='movedown-arrow'></span></button>
               </#if>
             </#if>
           </div>
           <#local counter = counter + 1 />
         </#list>
       </#if>

       <#-- JSON-property has at least one value and is not a list / multiple -->

       <#if elem.value?exists && elem.value?is_hash >
         <#list elem.description.attributes as jsonAttr>
           <#assign attrName = jsonAttr.name />
           <#assign tmpName = inputFieldName + "." + attrName + "." + counter />
           <#if elem.value[attrName]?exists >
             <@printJsonProperyEditView jsonAttr.type jsonAttrLocalizedTitle tmpName elem.value[attrName] elem attrName jsonAttr locale />
           <#else>
             <@printJsonProperyEditView jsonAttr.type jsonAttrLocalizedTitle tmpName "" elem attrName jsonAttr locale />
           </#if>
         </#list>
       </#if>

    </div>
  </div>

</#macro>

<#macro printJsonProperyEditView type jsonAttr tmpName value elem key json locale >

  <#switch type >

    <#case "string">
      <#assign fieldSize = getEdithintFieldSize(elem) />

      <#assign dropdown = false />
      <#if json.edithints?exists && json.edithints['dropdown']?exists >
        <#assign dropdown = true />
      </#if>

      <@vrtxString.printPropertyEditView
        title=jsonAttr
        inputFieldName=tmpName
        value=value
        classes=key
        inputFieldSize=fieldSize
        tooltip=""
        valuemap=json.getValuemap(locale)
        dropdown=dropdown
        />
      <#break>

    <#case "simple_html">
      <#assign cssClass = tmpName + " vrtx-simple-html" />

      <@vrtxHtml.printPropertyEditView
        title=jsonAttr
        inputFieldName=tmpName
        value=value
        editor=""
        classes=cssClass />

      <@editor.createEditor tmpName />
      <#break>

    <#case "html">
      <@vrtxHtml.printPropertyEditView
        title=jsonAttr
        inputFieldName=tmpName
        value=value
        editor=""
        classes="vrtx-html " + tmpName />

      <@editor.createEditor tmpName true false />
      <#break>

    <#case "boolean">
      <@vrtxBoolean.printPropertyEditView
        title=jsonAttr
        inputFieldName=tmpName
        value=value
        classes=""  />
      <#break>

   <#case "image_ref">
     <@vrtxImageRef.printPropertyEditView
       title=jsonAttr
       inputFieldName=tmpName
       value=value
       name=value
       baseFolder=resourceContext.parentURI
       classes="" />
     <#break>

   <#case "resource_ref">
     <#assign fieldSize = getEdithintFieldSize(elem) />

    <@vrtxResourceRef.printPropertyEditView 
      title=jsonAttr
      inputFieldName=tmpName
      value=value
      name=value 
      baseFolder=resourceContext.parentURI
      classes=""
      inputFieldSize=fieldSize />
    <#break>
    
    <#case "media_ref">
      <@vrtxMediaRef.printPropertyEditView
        title=jsonAttr
        inputFieldName=tmpName
        value=value
        baseFolder=resourceContext.parentURI
        classes=""  />
      <#break>

    <#case "datetime">
      <@vrtxDateTime.printPropertyEditView
        title=jsonAttr
        inputFieldName=tmpName
        value=value
        classes=""  />
      <#break>

  </#switch>

</#macro>

<#function getEdithintFieldSize element>

  <#-- Default value -->
  <#assign fieldSize = "40" />

  <#if element.description.edithints?exists && element.description.edithints['size']?exists >
    <#assign editHintSize = element.description.getEdithint('size') />
    <#if editHintSize == "large" >
      <#assign fieldSize="60" />
    <#elseif editHintSize == "small" >
      <#assign fieldSize="20"/>
    <#else>
      <#assign fieldSize = editHintSize />
    </#if>
  </#if>

  <#return fieldSize />

</#function>

<#function getEdithintClasses element includeElementName=true>

  <#-- Default value -->
  <#assign classes = "" />
  <#if includeElementName>
    <#assign classes = element.name />
  </#if>

  <#if element.description.edithints?exists && element.description.edithints['class']?exists >
    <#assign classList = element.description.edithints['class'] />
    <#list classList as class>
      <#if classes = "">
        <#assign classes = class />
      <#else>
        <#assign classes = classes + ' ' + class />
      </#if>
    </#list>
  </#if>

  <#return classes />

</#function>
