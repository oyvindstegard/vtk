<#ftl strip_whitespace=true output_format="HTML" auto_esc=false>

<#--
  - File: propertyList.ftl
  - 
  - Description: A library for displaying and editing
  - resource properties. Property listing works with lists of
  - vtk.web.controller.properties.PropertyItem objects.
  -
  - TODO: general documentation of this library.
  - 
  - Required model data:
  - 
  - Optional model data:
  -   form - an object of the class
  -   vtk.web.controller.properties.PropertyEditCommand
  -
  -->
<#import "/spring.ftl" as spring />
<#import "/lib/vtk.ftl" as vrtx />

<#assign defaultStringInputSize = 20 />

<#--
 * propertyList
 *
 * Display (and possibly edit) a list of propertyItems based on their
 * names in the data model.
 * Display is usually in this format: [propertyName]: [propertyValue] (editURL).
 * Example: <@propertyList modelName='myDataModel' itemNames=['lastModified', 'createdBy'] />
 *
 * @param modelName the name of the data model entry containing the
 *        property items
 * @param itemNames a sequence containing the names of the properties
 *        to display (the name format is [prefix:]propertyName)
 * @param toggle (optional) whether to attempt to generate toggle URLs instead of
 *        edit URLs for the properties that can be toggled. Default is false.
 * @param displayMacro (optional) name of a macro that takes
 *        parameters 'name', 'value'  and 'editURL', used to display
 *        each property. 
 *        Default is 'defaultPropertyDisplay'
 * @param editWrapperMacro (optional) name of a macro that wraps around
 *        the <form> element in edit mode, using <#nested />.
 *        Default is 'defaultEditWrapper'
 * @param formWrapperMacro (optional) name of a macro that wraps inside
 *        the <form> element in edit mode, using <#nested />.
 *        Default is 'defaultFormWrapper'
 * @param formInputWrapperMacro (optional) name of a macro that wraps around
 *        each <input> element (except submit elements) in edit mode, using <#nested />.
 *        Default is 'defaultFormInputWrapper'
 * @param formSubmitWrapperMacro (optional) name of a macro that wraps around
 *        the <input type="submit"> elements in edit mode, using <#nested />.
 *        Default is 'defaultFormSubmitWrapper'
 * @param formErrorsWrapperMacro (optional) name of a macro that wraps around
 *        the error list display in edit mode, using <#nested />.
 *        Default is 'defaultFormErrorsWrapper
 * @param formErrorWrapperMacro (optional) name of a macro that wraps around
 *        each error display in edit mode, using <#nested />.
 *        Default is 'defaultFormErrorWrapper
-->
<#macro propertyList modelName itemNames toggle=false
        propertyListWrapperMacro='defaultPropertyListWrapper'
        displayMacro='defaultPropertyDisplay'
        editWrapperMacro='defaultEditWrapper'
        formWrapperMacro='defaultFormWrapper'
        formInputWrapperMacro='defaultFormInputWrapper'
        formSubmitWrapperMacro='defaultFormSubmitWrapper'
        formErrorsWrapperMacro='defaultFormErrorsWrapper'
        formErrorWrapperMacro='defaultFormErrorWrapper'>
  <#local itemList = [] />
  <#list itemNames as propertyName>
    <#if .vars[modelName][propertyName]?exists>
      <#local itemList = itemList + [ .vars[modelName][propertyName] ] /> 
    </#if>
  </#list>
  <@propertyItemList
     propertyList=itemList
     toggle=toggle
     propertyListWrapperMacro=propertyListWrapperMacro 
     displayMacro=displayMacro
     editWrapperMacro=editWrapperMacro
     formWrapperMacro=formWrapperMacro
     formInputWrapperMacro=formInputWrapperMacro
     formSubmitWrapperMacro=formSubmitWrapperMacro
     formErrorsWrapperMacro=formErrorsWrapperMacro
     formErrorWrapperMacro=formErrorWrapperMacro/>
</#macro>



<#--
 * propertyItemList
 *
 * Display (and possibly edit) a list of propertyItems.
 * Display is usually in this format: [propertyName]: [propertyValue] (editURL).
 * Example: <@propertyItemList items=[item1, item2] />
 *
 * @param items a list of
 *        vtk.web.controller.properties.PropertyItem objects
 * @param toggle [see documentation for propertyList]
 * @param displayMacro [see documentation for propertyList]
 * @param editWrapperMacro [see documentation for propertyList]
 * @param formWrapperMacro [see documentation for propertyList]
 * @param formInputWrapperMacro [see documentation for propertyList]
 * @param formErrorsWrapperMacro [see documentation for propertyList]
 * @param formErrorWrapperMacro [see documentation for propertyList]
-->
<#macro propertyItemList propertyList toggle=false
        propertyListWrapperMacro='defaultPropertyListWrapper'
        displayMacro='defaultPropertyDisplay'
        editWrapperMacro='defaultEditWrapper'
        formWrapperMacro='defaultFormWrapper'
        formInputWrapperMacro='defaultFormInputWrapper'
        formSubmitWrapperMacro='defaultFormSubmitWrapper'
        formErrorsWrapperMacro='defaultFormErrorsWrapper'
        formErrorWrapperMacro='defaultFormErrorWrapper'>
  <#local wrapperMacro = resolveMacro(propertyListWrapperMacro) />
  <@wrapperMacro>
    <#list propertyList as item>
      <@editOrDisplayPropertyItem
         item=item
         toggle=toggle 
         inputSize=defaultStringInputSize
         displayMacro=displayMacro
         editWrapperMacro=editWrapperMacro
         formWrapperMacro=formWrapperMacro
         formInputWrapperMacro=formInputWrapperMacro
         formSubmitWrapperMacro=formSubmitWrapperMacro
         formErrorsWrapperMacro=formErrorsWrapperMacro
         formErrorWrapperMacro=formErrorWrapperMacro />
    </#list>
  </@wrapperMacro>
</#macro>


<#--
 * editOrDisplayProperty
 *
 * Display or edit a single property item based on its name in the
 * data model.
 * 
 * Example: <@editOrDisplayProperty modelName='myDataModel' propertyName='characterEncoding' />
 *
 * @param modelName the name of the data model entry containing the
 *        property item (should be a hash)
 * @param propertyName the name of the property item in the 
 *        hash identified by [modelName].
 * @param inputSize the size of the input field. Used for editing strings only. 
          Value greater than 99 displays a textarea. 
 * @param toggle [see documentation for propertyList]
 * @param displayMacro [see documentation for propertyList]
 * @param editWrapperMacro [see documentation for propertyList]
 * @param formWrapperMacro [see documentation for propertyList]
 * @param formInputWrapperMacro [see documentation for propertyList]
 * @param formErrorsWrapperMacro [see documentation for propertyList]
 * @param formErrorWrapperMacro [see documentation for propertyList]
-->
<#macro editOrDisplayProperty modelName propertyName toggle=false inputSize=defaultStringInputSize
        propertyListWrapperMacro='defaultPropertyListWrapper'
        displayMacro='defaultPropertyDisplay'
        editWrapperMacro='defaultEditWrapper'
        formWrapperMacro='defaultFormWrapper'
        formInputWrapperMacro='defaultFormInputWrapper'
        formSubmitWrapperMacro='defaultFormSubmitWrapper'
        formErrorsWrapperMacro='defaultFormErrorsWrapper'
        formErrorWrapperMacro='defaultFormErrorWrapper'>
  <#if .vars[modelName]?exists && .vars[modelName][propertyName]?exists>
  <#local item = .vars[modelName][propertyName] />
  <#if form?exists && form.definition?exists && form.definition = item.definition>
    <@propertyForm 
       item=item
       inputSize=inputSize
       editWrapperMacro=editWrapperMacro
       formWrapperMacro=formWrapperMacro
       formInputWrapperMacro=formInputWrapperMacro
       formSubmitWrapperMacro=formSubmitWrapperMacro
       formErrorsWrapperMacro=formErrorsWrapperMacro
       formErrorWrapperMacro=formErrorWrapperMacro/>
  <#else>
    <@propertyDisplay item=item toggle=toggle displayMacro=displayMacro/>
  </#if>
  </#if>
</#macro>

<#--
 * editOrDisplayPropertyItem
 *
 * Display or edit a single property item.
 * 
 * Example: <@editOrDisplayPropertyItem item=myPropertyItem />
 *
 * @param item an vtk.web.controller.properties.PropertyItem object
 * @param inputSize [see documentation for editOrDisplayProperty]
 * @param toggle [see documentation for propertyList]
 * @param displayMacro [see documentation for propertyList]
 * @param editWrapperMacro [see documentation for propertyList]
 * @param formWrapperMacro [see documentation for propertyList]
 * @param formInputWrapperMacro [see documentation for propertyList]
 * @param formErrorsWrapperMacro [see documentation for propertyList]
 * @param formErrorWrapperMacro [see documentation for propertyList]
-->
<#macro editOrDisplayPropertyItem item toggle=false inputSize=defaultStringInputSize      
        propertyListWrapperMacro='defaultPropertyListWrapper'
        displayMacro='defaultPropertyDisplay'
        editWrapperMacro='defaultEditWrapper'
        formWrapperMacro='defaultFormWrapper'
        formInputWrapperMacro='defaultFormInputWrapper'
        formSubmitWrapperMacro='defaultFormSubmitWrapper'
        formErrorsWrapperMacro='defaultFormErrorsWrapper'
        formErrorWrapperMacro='defaultFormErrorWrapper'
        defaultItem=false>
  <#if form?exists && form.definition?exists && form.definition = item.definition>
    <@propertyForm 
       item=item
       defaultItem=defaultItem
       inputSize=inputSize
       editWrapperMacro=editWrapperMacro
       formWrapperMacro=formWrapperMacro
       formInputWrapperMacro=formInputWrapperMacro
       formSubmitWrapperMacro=formSubmitWrapperMacro
       formErrorsWrapperMacro=formErrorsWrapperMacro
       formErrorWrapperMacro=formErrorWrapperMacro/>
  <#else>
    <@propertyDisplay defaultItem=defaultItem item=item toggle=toggle displayMacro=displayMacro/>
  </#if>
</#macro>


<#--
 * defaultPropertyListWrapper
 *
 * Default wrapper around the property list. Uses a <table> and
 * <#nested /> to wrap
 * around the list.
 * 
-->
<#macro defaultPropertyListWrapper>
  <table>
    <#nested />
  </table>
</#macro>

<#--
 * defaultPropertyDisplay
 *
 * Default property display macro. Creates a table row for each
 * property.
 *
 * @param name the name of the property
 * @param value the value of the property
 * @editURL (optional) the edit (possibly toggle) URL of the property
 * 
-->
<#macro defaultPropertyDisplay propName name value prefix=false editURL="">
  <tr class="prop-${propName}">
    <th scope="row" class="key">
      ${name}:
    </th>
    <td class="value">
      ${value}
      <#if editURL??>
        ${editURL}
      </#if>
    </td>
  </tr>
</#macro>


<#--
 * defaultEditWrapper
 *
 * Default wrapper around the property edit <form> element. Creates a
 * table row using <tr>, <td> and <#nested />.
 * 
-->
<#macro defaultEditWrapper item>
  <tr class="expandedForm-prop-${item.definition.name} expandedForm ${item.definition.name}">
    <td colspan="2">
      <#nested /> 
    </td>
  <tr>
</#macro>


<#--
 * defaultFormWrapper
 *
 * Default wrapper inside the property edit <form> element. 
 * Creates a <h3> with the name, and then a <ul> element containing
 * the <#nested /> content.
 *
 * @param item the property item currently being edited.
 * 
-->
<#macro defaultFormWrapper item>
  <#local msgPrefix = localizationPrefix(item) />
  <#local name = vrtx.getMsg(msgPrefix, item.definition.name) />
  <h3>${name}:</h3>
  <ul class="property">
    <#nested />
  </ul>
</#macro>


<#--
 * defaultFormInputWrapper
 *
 * Default wrapper around form <input> elements. 
 * Wraps the <#nested /> content in a <li>
 *
 * @param item the property item currently being edited.
 * 
-->
<#macro defaultFormInputWrapper item>
  <li><#nested /></li>
</#macro>


<#--
 * defaultFormSubmitWrapper
 *
 * Default wrapper around form submit elements. 
 * Wraps the <#nested /> content in a <li><div>
 *
 * @param item the property item currently being edited.
 * 
-->
<#macro defaultFormSubmitWrapper item>
  <li><div class="submitButtons"><#nested /></div></li>
</#macro>

<#--
 * defaultFormErrorsWrapper
 *
 * Default wrapper around the form error list.
 * Wraps the <#nested /> content in a <ul>
 * 
-->
<#macro defaultFormErrorsWrapper>
  <ul class="errors"><#nested /></ul>
</#macro>

<#--
 * defaultFormErrorsWrapper
 *
 * Default wrapper around each form error.
 * Wraps the <#nested /> content in a <li>
 * 
-->
<#macro defaultFormErrorWrapper>
  <li><#nested /></li>
</#macro>



<#--
 * propertyDisplay
 *
 * Display a vtk.web.controller.properties.PropertyItem,
 * usually in this format: [propertyName]: [propertyValue] (editURL)
 * Example: <@propertyDisplay item=myItem />
 *
 * @param item a vtk.web.controller.properties.PropertyItem
 *        representing a resource property
 * @param displayMacro (optional) name of a macro that takes parameters "name", "value"
 *        and "editURL", used to display the property.
 *        Default is 'defaultPropertyDisplay'
-->
<#macro propertyDisplay item toggle=false defaultItem=false displayMacro='defaultPropertyDisplay'>
  <#local msgPrefix = localizationPrefix(item) />
  <#local name = vrtx.getMsg(msgPrefix, item.definition.name) />
  
  <#assign valueItem=item />

  <#assign prefix=false />
    <#if !defaultItem?is_boolean>
      <#local msgPrefix = localizationPrefix(item) />
      <#if item.property?exists && item.property.value == defaultItem.property.value>
        <#assign prefix = vrtx.getMsg(msgPrefix + ".set", "") />
      <#else>
        <#assign valueItem=defaultItem />
        <#assign prefix = vrtx.getMsg(msgPrefix + ".unset", "") />
      </#if>
    </#if>

  <#local value>
    <#if valueItem.property?exists>
      <#if valueItem.definition.vocabulary?exists>
        ${valueItem.property.getFormattedValue("localized", springMacroRequestContext.locale)}
      <#elseif valueItem.definition.multiple>
        <#list valueItem.property.values as val>
          ${val}<#if val_has_next>, </#if>
        </#list>
        <#if valueItem.property.values?size &lt; 0>
        </#if>
      <#else>
        <#-- type principal -->
        <#if valueItem.definition.type = "PRINCIPAL">
          <#if valueItem.property.principalValue.URL?exists>
            <a href="${valueItem.property.principalValue.URL}">${valueItem.property.principalValue.name}</a>
          <#else>
            ${valueItem.property.principalValue.name}
          </#if>
        <#-- type date -->
        <#elseif valueItem.definition.type = "DATE" || valueItem.definition.type = "TIMESTAMP">
          ${valueItem.property.dateValue?datetime?string.long}
        <#else>
          <#local label>
            <@vrtx.msg code="${msgPrefix}.value.${valueItem.property.value?string}"
                       default="${valueItem.property.value}" />
          </#local>
          ${label}
        </#if>
      </#if>
    <#else>
      <#local defaultNotSet><@vrtx.msg code="resource.property.unset" default="Not set" /></#local>
      <#local label>
        <@vrtx.msg code="${msgPrefix}.unset" default="${defaultNotSet?markup_string}" />
      </#local>
      ${label}
    </#if>
  </#local>
  <#local editURL>
    <@propertyItemEditURL item=item toggle=toggle />
  </#local>

  <#local macroCall = resolveMacro(displayMacro) />
  <@macroCall propName=item.definition.name name=name prefix=prefix value=value editURL=editURL />
</#macro>




<#--
 * propertyForm
 *
 * Display a form for editing a property contained in a
 * vtk.web.controller.properties.PropertyItem object,
 *
 * @param item a vtk.web.controller.properties.PropertyItem
 *        representing a resource property.
 * @param inputSize [see documentation for editOrDisplayProperty]
 * @param editWrapperMacro [see documentation for propertyList]
 * @param formWrapperMacro [see documentation for propertyList]
 * @param formInputWrapperMacro [see documentation for propertyList]
 * @param formErrorsWrapperMacro [see documentation for propertyList]
 * @param formErrorWrapperMacro [see documentation for propertyList]
-->
<#macro propertyForm item inputSize=defaultStringInputSize formValue=''
        editWrapperMacro='defaultEditWrapper'
        formWrapperMacro='defaultFormWrapper'
        formInputWrapperMacro='defaultFormInputWrapper'
        formSubmitWrapperMacro='defaultFormSubmitWrapper'
        formErrorsWrapperMacro='defaultFormErrorsWrapper'
        formErrorWrapperMacro='defaultFormErrorWrapper'
        defaultItem=false>

  <#local msgPrefix = localizationPrefix(item) />

    <#local editWrapper = resolveMacro(editWrapperMacro) />
    <#local formWrapper = resolveMacro(formWrapperMacro) />
    <#local formInputWrapper = resolveMacro(formInputWrapperMacro) />
    <#local formSubmitWrapper = resolveMacro(formSubmitWrapperMacro) />
    <#local formErrorsWrapper = resolveMacro(formErrorsWrapperMacro) />
    <#local formErrorWrapper = resolveMacro(formErrorWrapperMacro) />

    <@editWrapper item>
    <form id="propertyForm" action="${form.submitURL}" method="post">
      <@formWrapper item>
        <#-- Display radio buttons for a value set of 2: -->

        <#if form.possibleValues?exists && form.possibleValues?size = 2>
          <#list form.possibleValues as alternative>
            <@formInputWrapper item>
            <#if alternative?has_content>
              <#local label><@vrtx.msg code="${msgPrefix}.value.${alternative}" default="${alternative}" /></#local>
              <input id="${alternative}" type="radio" name="value" value="${alternative}"
                         <#if form.value?has_content && form.value = alternative>checked</#if> />
               <label for="${alternative}">${label}</label>
            <#else>
              <#local defaultNotSet><@vrtx.msg code="resource.property.unset" default="Not set" /></#local>
              <#local label><@vrtx.msg code="${msgPrefix}.unset" default="${defaultNotSet?markup_string}" /></#local>
                <input id="unset" type="radio" name="value" value=""
                           <#if !form.value?has_content>checked</#if> />
                  <label for="unset">${label}</label>
            </#if>
            </@formInputWrapper>
          </#list>

        <#-- Display drop down list for value sets > 2: -->

        <#elseif form.possibleValues?exists && form.possibleValues?size &gt; 2>
          <@formInputWrapper item>
          <select name="value">
          <#list form.possibleValues as alternative>
            <#if alternative?has_content>
              <#local constructor = "freemarker.template.utility.ObjectConstructor"?new() />
              <#local label>${item.definition.valueFormatter.valueToString(constructor("vtk.repository.resourcetype.Value", alternative, item.definition.type), "localized", springMacroRequestContext.locale)}</#local>
              <option value="${alternative}" <#if form.value?has_content && form.value = alternative>selected="true"</#if> label="${label}">${label}</option>
            <#else>
              <#local defaultNotSet><@vrtx.msg code="resource.property.unset" default="Not set" /></#local>
              <#local label><@vrtx.msg code="${msgPrefix}.unset" default="${defaultNotSet?markup_string}" /></#local>
              <option id="unset" value="" <#if !form.value?has_content>selected="true"</#if> label="${label}">${label}</option>
            </#if>
          </#list>
          </select>

          <#if form.hierarchicalHelpUrl?exists>
            <script type="text/javascript">
                function popitup(url) {
                  var fixedUrl = url + '&selected=' + document.getElementById('value').value;
	          var newwindow=window.open(fixedUrl,'vocabulary','scrollbars=1');
	          if (window.focus) {newwindow.focus()}
	          return false;
                }
          </script>

          <a target="vocabulary" href="${form.hierarchicalHelpUrl}" onclick="return popitup('${form.hierarchicalHelpUrl}')" ><@vrtx.msg code="propertyEditor.browse" default="Browse"/></a>
        </#if>
          </@formInputWrapper>

        <#-- Display regular input field for plain values: -->

        <#else>
          <#local value>
            <#compress>
            <#if formValue != "">
              ${formValue}
            <#elseif form.value?exists>
              ${form.value}
            <#elseif !defaultItem?is_boolean>
              ${defaultItem.property.value?string}
            </#if>
            </#compress>
          </#local>

          <@formInputWrapper item>
            <#if inputSize &gt; 99>
              <textarea name="value" rows="5" cols="60">${value}</textarea>
            <#else>
              <input class="vrtx-textfield" type="text" id="value" name="value" value="${value}" size="${inputSize}" />
              <#if item.format?exists>(${item.format})</#if>
            </#if>
            <#if form.hierarchicalHelpUrl?exists>
          <script type="text/javascript"><!--
            function popitup(url) {
              var fixedUrl = url + '&selected=' + document.getElementById('value').value;
	          var newwindow = window.open(fixedUrl,'vocabulary','scrollbars=1');
	          if (window.focus) {
	            newwindow.focus()}
	            return false;
              }
          // -->
          </script>

          <a target="vocabulary" href="${form.hierarchicalHelpUrl}" onclick="return popitup('${form.hierarchicalHelpUrl}')" ><@vrtx.msg code="propertyEditor.browse" default="Browse"/></a>
        </#if>
          </@formInputWrapper>

        </#if>

      <@spring.bind "form.value"/>
      <#if spring.status.errorCodes?size &gt; 0>
        <@formErrorsWrapper>
          <#list spring.status.errorCodes as error> 
            <@formErrorWrapper>${error}</@formErrorWrapper>
          </#list>
        </@formErrorsWrapper>
      </#if>

      <@formSubmitWrapper item>
        <input class="vrtx-focus-button" type="submit" name="save" value="<@vrtx.msg code="propertyEditor.save" default="Save"/>" />
        <input class="vrtx-button" type="submit" name="cancelAction" value="<@vrtx.msg code="propertyEditor.cancel" default="Cancel"/>" />
      </@formSubmitWrapper>
      </@formWrapper>
    </form>
    </@editWrapper>
</#macro>


<#--
 * propertyEditURL
 *
 * Display a URL to edit a property item based on its name in the data
 * model. 
 *
 * @param modelName the name of the data model entry containing the
 *        property item (should be a hash)
 * @param propertyName the name of the property item in the 
 *        hash identified by [modelName].
 * @param toggle (optional) whether to attempt to generate a toggle URL instead of
 *        a regular edit URL for the propery. Default is false.
-->
<#macro propertyEditURL modelName propertyName toggle=false>
  <#if .vars[modelName][propertyName]?exists>
      <#local item =  .vars[modelName][propertyName] /> 
      <@propertyItemEditURL item=item toggle=toggle />
  </#if>
</#macro>

<#--
 * propertyEditURL
 *
 * Display a URL to edit a property item based on its name in the data
 * model. 
 *
 * @param item a vtk.web.controller.properties.PropertyItem
 *        representing a resource property.
 * @param toggle (optional) whether to attempt to generate a toggle URL instead of
 *        a regular edit URL for the propery. Default is false.
-->
<#macro propertyItemEditURL item toggle=false>
  <#if toggle && item.toggleURL?exists>
    <#local msgPrefix = localizationPrefix(item) />
    <#local defaultToggle>
      <@vrtx.msg code="propertyEditor.toggle" default="toggle" />
    </#local>
    <#local label>
      <#if item.toggleValue?exists>
        <@vrtx.msg
           code="${msgPrefix}.toggle.value.${item.toggleValue}" 
           default="${defaultToggle}" />
        <#else>
        <@vrtx.msg
           code="${msgPrefix}.toggle.unset" default="${defaultToggle}" />
      </#if>
    </#local>
     &nbsp;<a class="vrtx-button-small" href="${item.toggleURL}">${label}</a>
  <#elseif item.editURL?exists><#noautoesc>
     &nbsp;<a class="vrtx-button-small" href="${item.editURL}"><@vrtx.msg code="propertyEditor.edit" default="edit" /></a></#noautoesc>
  </#if>
</#macro>


<#--
 * resolveMacro
 *
 * Resolves a macro based on its name in either the current or
 * 'global' namespace. (Mostly intended for internal use in this
 * library.)
 *
 * @param macroName the (possibly fully qualified) name of the macro
-->
<#function resolveMacro macroName>
  <#local macroCall = "" />
  <#if macroName?eval?exists && macroName?eval?is_macro>
    <#local macroCall = macroName?eval />
  <#elseif .main[macroName]?exists && .main[macroName]?is_macro>
    <#local macroCall = .main[macroName] />
  <#else>
    <#stop "No such macro: ${macroName}" />
  </#if>
  <#return macroCall />
</#function>


<#--
 * localizationPrefix
 *
 * Internal utility function.
 *
-->
<#function localizationPrefix item>
  <#if item.definition.namespace.prefix?exists>
    <#return 'property.' + item.definition.namespace.prefix + ':' + item.definition.name />
   <#else>
     <#return 'property.' + item.definition.name />
   </#if>
</#function>
