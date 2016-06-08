<#ftl strip_whitespace=true output_format="HTML" auto_esc=true>
<#import "/spring.ftl" as spring />

<#--
 * vtk.ftl
 *
 * (Will become) a collection of useful FreeMarker macros and functions :)
 *
 * The  "exposeSpringMacroHelpers" property on the spring FreeMarker configuration must be set.
 *
 -->

<#if !springMacroRequestContext?exists>
  <#stop "springMacroRequestContext must be exposed. See the
         'exposeSpringMacroHelpers' property of the FreeMarker
         configuration." />
</#if>


<#function getUri resource>
  <#assign uri = resource.URI />
  <#assign solrUri = vrtx.propValue(resource, "solr.url")! />
  <#if solrUri?? && solrUri?has_content>
    <#assign uri = solrUri>
  </#if>
  <#return uri />
</#function>


<#function getLocale resource>
  <#if resource.contentLocale?has_content>
    <#return resource.contentLocale />
  </#if>

  <#assign constructor = "freemarker.template.utility.ObjectConstructor"?new() />
  <#assign localeProp = vrtx.propValue(resource, 'contentLocale') />
  <#if localeProp?exists && localeProp?has_content>
    <#if localeProp?is_markup_output>
      <#return constructor("java.util.Locale", localeProp?markup_string) />
    <#else>
      <#return constructor("java.util.Locale", localeProp) />
    </#if>
  </#if>

  <#assign solrResource = vrtx.propValue(resource, 'solr.isSolrResource') />
  <#if solrResource?exists>
    <#local lang = vrtx.propValue(resource, 'solr.lang') />
    <#if lang?exists && lang?has_content>
      <#return constructor("java.util.Locale", lang) />
    </#if>
  </#if>

  <#-- Fall back on context locale -->
  <#return springMacroRequestContext.getLocale() />
</#function>


<#-- XXX: remove this when properties 'introduction' and 'description'
     are merged: -->
<#function getIntroduction resource>
  <#local introduction = vrtx.propValue(resource, "introduction")! />
  <#local resourceType = resource.resourceType />
  <#if !introduction?has_content && resourceType != 'collection' 
       && resourceType != 'event-listing' && resourceType != 'article-listing' >
    <#local introduction = vrtx.propValue(resource, "description", "", "content")! />
  </#if>
  <#if introduction?has_content><#return introduction /></#if>
</#function>


<#macro localizeMessage code default args=nullArg locale=springMacroRequestContext.getLocale() escape=true >
  <#local localizer =
    "vtk.web.view.freemarker.MessageLocalizer"?new(code, default, args, springMacroRequestContext, locale) />
    <#if escape>
      ${localizer.msg}
    <#else>
      ${localizer.msg?no_esc}
    </#if>
</#macro>


<#--
 * msg
 *
 * Get a localized, html-escaped message from the spring RequestContext. 
 * Example: <@vrtx.msg code="my.message.key" default="My default message" args="['my param']"
 *
 * @param code the code of the localized message
 * @param default (optional - set to code if not specified) the default message if the localized message did not
 *        exist for the currently selected locale.
 * @param args (optional) arguments for the message
 *
-->
<#macro msg code default=code args=[] >
  <#compress>
    <#local localizer =
    "vtk.web.view.freemarker.MessageLocalizer"?new(code, default, args, springMacroRequestContext) />
    ${localizer.msg}
  </#compress>
</#macro>


<#function resourceLanguage>
  <#if resourceLocaleResolver?exists>
     <#local l = resourceLocaleResolver.resolveLocale(null)?string />
     <#return getMsg("language."+l, l) />
  </#if>
</#function>


<#--
 * rawMsg
 *
 * Get a localized, unescaped message from the spring RequestContext. 
 * Example: <@vrtx.rawMsg code="my.message.key" default="My default message" args="['my param']"
 *
 * @param code the code of the localized message
 * @param default the default message if the localized message did not
 *        exist for the currently selected locale.
 * @param args (optional) arguments for the message
 *
-->
<#macro rawMsg code default=code args=[] >
  <#compress>
    <#local localizer =
    "vtk.web.view.freemarker.MessageLocalizer"?new(code, default, args, springMacroRequestContext) />
  ${localizer.msg?no_esc}
  </#compress>
</#macro>


<#--
 * getMsg
 *
 * Same as the macro 'rawMsg', but returns a value instead of printing it.
 * Example: <#assign msg = vrtx.getMsg("my.message.key", "My default message", ['my param']) />
 *
 * @param code the code of the localized message
 * @param default the default message if the localized message did not
 *        exist for the currently selected locale.
 * @param args (optional) arguments for the message
 *
-->
<#function getMsg code default=code args=[] >
    <#assign localizer =
    "vtk.web.view.freemarker.MessageLocalizer"?new(code, default, args, springMacroRequestContext) />
    <#return localizer.msg?no_esc />
</#function>


<#--
 * date
 *
 * Get a localized, formatted date string from a date object.
 * Examples: <@vrtx.date value=my.date.object format='short' />
 *           <@vrtx.date value=my.date.object format='yyyy-MM-dddd HH:mm:ss' />
 *
 * @param value the date object
 * @param format a named format, or a java DateFormat
 *        string. See vtk.repository.resourcetype.ValueFormatter
 *
-->
<#macro date value format locale=springMacroRequestContext.getLocale()>
  <#compress>
    <#if VRTX_DATE_VALUE_FORMATTER?exists>
      <#local constructor = "freemarker.template.utility.ObjectConstructor"?new() />
      <#local val = constructor("vtk.repository.resourcetype.Value", value, false) />
      ${VRTX_DATE_VALUE_FORMATTER.valueToString(val, format, locale)}
    <#else>
      Undefined
    </#if>
  </#compress>
</#macro>


<#--
 * calcDate
 *
 * Get a localized, formatted date string from a date object.
 *
 * Same as date macro except it returns the string instead of print it. 
 *
 * Examples: vrtx.date(my.date.object, 'short')
 *           vrtx.date(my.date.object, 'yyyy-MM-dddd HH:mm:ss')
 *
 * @param value the date object
 * @param format a named format, or a java DateFormat
 *        string. See vtk.repository.resourcetype.ValueFormatter
 *
-->
<#function calcDate value format>
  <#if VRTX_DATE_VALUE_FORMATTER?exists>
    <#local constructor = "freemarker.template.utility.ObjectConstructor"?new() />
    <#local val = constructor("vtk.repository.resourcetype.Value", value, false) />
    <#local locale = springMacroRequestContext.getLocale() />
    <#return VRTX_DATE_VALUE_FORMATTER.valueToString(val, format, locale) />
  <#else>
    <#return "Undefined" />
  </#if>
</#function>


<#--
 * parseInt
 *
 * Attempts to parse an integer from a string
 * Example: <#assign val = vrtx.parseInt("222") />
 *
 * @param value the string value
 *
-->
<#function parseInt value>
  <#assign constructor = "freemarker.template.utility.ObjectConstructor"?new() />
  <#assign parser = constructor("java.lang.Integer", 0) />
  <#return parser.parseInt(value) />
</#function>


<#--
 * flattenHtml
 *
 * Flattens an HTML string.
 * Example: <@vrtx.flattenHtml value='<div>foo</div>' /> (produces 'foo')
 *
 * @param value the HTML string
 * @param escape whether to HTML-escape the flattened string (default true)
 *
-->
<#macro flattenHtml value escape=true>
  <#compress>
    <#if VRTX_HTML_UTIL?exists>
      <#if escape>
        ${VRTX_HTML_UTIL.flatten(value)}
      <#else>
        ${VRTX_HTML_UTIL.flatten(value)}
      </#if>
    <#else>
      Undefined
    </#if>
  </#compress>
</#macro>


<#--
 * linkResolveFilter
 *
 * Resolves relative links in an HTML string.
 *
 * @param value the HTML string
 * @param baseURL the URL to the resource that is base for the url
 * @param requestURL URL from the request
 *
-->
<#macro linkResolveFilter value baseURL requestURL protocolRelative=false>
  <#compress>
    <#if VRTX_HTML_UTIL?exists>
      ${VRTX_HTML_UTIL.linkResolveFilter(value, baseURL, requestURL, protocolRelative).getStringRepresentation()?no_esc}
    <#else>
      Undefined
    </#if>
  </#compress>
</#macro>


<#function relativeLinkConstructor resourceUri serviceName >
  <#if linkConstructor(resourceUri,serviceName)?exists >
    <#local constructedURL = linkConstructor(resourceUri,serviceName) />
  </#if>
  <#if constructedURL?exists && resourceUri?exists && !resourceUri?contains("://") >
    <#return constructedURL.getPathRepresentation() />
  <#elseif resourceUri?exists >
    <#return resourceUri />
  <#else>
    <#return "" />
  </#if>
</#function>


<#function linkConstructor resourceUri serviceName >
  <#if VRTX_LINK_CONSTRUCTOR?exists && resourceUri?exists && serviceName?exists >
    <#if VRTX_LINK_CONSTRUCTOR.construct(resourceUri,null,serviceName)?exists>
      <#return VRTX_LINK_CONSTRUCTOR.construct(resourceUri,null,serviceName) />
    </#if>
  </#if>
</#function>


<#--
 * invokeComponentRefs
 *
 * Invokes decorating components in an HTML string
 * Example: <@vrtx.invokeComponentRefs html='<div>${include:file uri=[/foo.txt]}</div>' />
 *
 * @param html the HTML string
 *
-->
<#macro invokeComponentRefs html>
  <#local frag = VRTX_HTML_UTIL.parseFragment(html) />
  ${frag.filter(VRTX_DECORATING_NODE_FILTER)}
  ${frag.stringRepresentation?no_esc}
</#macro>


<#--
 * limit
 *
 * Limits a string to a maximum number of characters
 * Example: <@vrtx.limit nchars='3'>some text</@vrtx.limit> - produces 'som'
 *
 * @param nchars the maximum number of characters
 * @param elide whether or not to append '...' to the string in case
 *        its length is greater than the maximum allowed value
 *
-->
<#macro limit nchars elide=false>
  <#compress>
    <#local val><#nested /></#local>
    <#if val?is_markup_output><#local val = val?markup_string /></#if>
    <#if val?length &lt; nchars>
      ${val}
    <#else>
      <#local cut_index = nchars />
      <#if cut_index &gt; val?length>
        <#local cut_index = val?length />
      </#if>
      <#local val = val?substring(0, nchars) />
      ${val}
      <#if elide>...</#if>
    </#if>
  </#compress>
</#macro>

<#--
 * breakSpecificChar
 * Breaks a string if longer than maximum characters on a char or recursive on nchars
 * Example: <@vrtx.breakSpecificChar nchars=2 char='@'>root@localhost</@vrtx.breakSpecificChar>
 *
 * @param nchars the maximum number of characters before split
 * @param char the char to split on
 *
-->
<#macro breakSpecificChar nchars splitClass="comment-author-part" char="">
  <#compress>
    <#local val><#nested /></#local>
    <#local val = val?markup_string />
    <#if val?length &lt; nchars>
      ${val}
    <#else>
      <#local cut_index = nchars />
      <#if (char != "" && val?index_of(char) >= 0)>
        <#local cut_index = val?index_of(char) />
        <#local newVal = "<span class='${splitClass}-one'>" + val?substring(0, cut_index) + "</span>" />
        <#local newVal = newVal + "<span class='${splitClass}-two'>" + val?substring(cut_index, val?length) + "</span>" />
        ${newVal}
      <#else>
        <@splitParts val "" splitClass cut_index 0 />
      </#if>
    </#if>
  </#compress>
</#macro>

<#macro splitParts val newVal splitClass cut_index nr>
   <#local start = cut_index * nr />
   <#if ((cut_index + start) < val?length)>
     <#local end = cut_index + start />
     <#local newVal = newVal + "<span class='${splitClass}-split'>" + val?substring(start, end) + "</span>" />
     <@splitParts val newVal splitClass cut_index nr+1 />
   <#else>
     <#local newVal = newVal + "<span class='${splitClass}-split'>" + val?substring(start, val?length) + "</span>" />
     ${newVal}   
   </#if>
</#macro>

<#--
 * fileNamesAsLimitedList
 * Make filenames with full path into a list with only filename limited by a number
 *
 * @param files the files
 * @param limit number of filenames to list
 *
-->
<#macro fileNamesAsLimitedList files limit=10>
  <#compress>
    <#local numberOfRemainingFiles = (files?size - limit)  />
    <ul>
    <#local more = false />
    <#list files as file>
      <#if file_index == limit>
        <#local more = true />
        <#break />
      </#if>
      <li>${file?split("/")?last}</li>
    </#list>
    </ul>
    <#if more>
      <p>... <@msg code="trash-can.permanent.delete.confirm.and" default="and"/> ${numberOfRemainingFiles} <@msg code="trash-can.permanent.delete.confirm.more" default="mode"/></p>
    </#if>
  </#compress>
</#macro>


<#--
 * requestLanguage
 *
 * Gets the ISO 639-1 language code for the current request
 *
-->
<#macro requestLanguage>
  <#compress>
    ${springMacroRequestContext.locale.language}
  </#compress>
</#macro>

<#--
 * These functions needs some documentation..
 -->
<#function resourceTypeName resource>
    <#local locale = springMacroRequestContext.getLocale() />
    <#return getMsg("resourcetype.name." + resource.resourceType) />
</#function>

<#function prop resource propName prefix=''>
  <#local prop = getProp(resource, propName, prefix)!>
  <#if !(prop?has_content)>
    <#local prop = getProp(resource, propName, 'resource')!>
  </#if>
  <#if prop?has_content><#return prop /></#if>
</#function>

<#function getProp resource name prefix=''>
  <#if VRTX_RESOURCE_TYPE_TREE??>
    <#if prefix == "">
      <#if VRTX_RESOURCE_TYPE_TREE.getPropertyDefinitionByPrefix(nullArg, name)??>
        <#local def = VRTX_RESOURCE_TYPE_TREE.getPropertyDefinitionByPrefix(nullArg, name)! />
      </#if>
    <#else>
      <#if VRTX_RESOURCE_TYPE_TREE.getPropertyDefinitionByPrefix(prefix, name)?exists>
        <#local def = VRTX_RESOURCE_TYPE_TREE.getPropertyDefinitionByPrefix(prefix, name)! />
      </#if>
    </#if>
    <#if def?? && resource.getProperty(def)??>
      <#return resource.getProperty(def) />
    </#if>
  </#if>
</#function>

<#function propValue resource name format='long' prefix=''>  
  <#if getPropValue(resource, name, format, prefix)??>
    <#local propVal = getPropValue(resource, name, format, prefix) />
  <#elseif getPropValue(resource, name, format, 'resource')??>
    <#local propVal = getPropValue(resource, name, format, 'resource') />
  </#if>
  <#if propVal??>
    <#return propVal?no_esc />
  </#if>
</#function>

<#--
 * isOfType
 *
 * Function to query resource type hierarchy.
 *
 * Test if a resource type is of another [super/mixin] type.
 *
 * @param superType the super/mixin type name as a string
 * @param testType the test type name as a string.
 * @return true if testType is descendant type of superType, false otherwise.
-->
<#function isOfType superTypeName testTypeName>
  <#if VRTX_RESOURCE_TYPE_TREE?exists>
    <#local superDef = VRTX_RESOURCE_TYPE_TREE.getResourceTypeDefinitionByName(superTypeName) />
    <#return VRTX_RESOURCE_TYPE_TREE.isContainedType(superDef, testTypeName) />
  </#if>
  <#return false />
</#function>

<#function getPropValue resource name format='long' prefix=''>
  <#if VRTX_RESOURCE_TYPE_TREE?exists>
    <#if prefix == "">
      <#if VRTX_RESOURCE_TYPE_TREE.getPropertyDefinitionByPrefix(nullArg, name)?exists>
        <#local def = VRTX_RESOURCE_TYPE_TREE.getPropertyDefinitionByPrefix(nullArg, name) />
      </#if>
    <#else>
      <#if VRTX_RESOURCE_TYPE_TREE.getPropertyDefinitionByPrefix(prefix, name)??>
        <#local def = VRTX_RESOURCE_TYPE_TREE.getPropertyDefinitionByPrefix(prefix, name) />
      </#if>
    </#if>
  </#if>
  <#if def??>
    <#local locale = springMacroRequestContext.getLocale() />
    <#if resource.getProperty(def)??>
      <#local prop = resource.getProperty(def) />
      <#if def.type != 'IMAGE_REF'>
        <#return prop.getFormattedValue(format, locale) />
      <#else>
        <#-- Hack for relative imagerefs, make sure it doesn't mess up anything else (attempt/recover) -->
        <#attempt>
          <#local imageRef = prop.getStringValue() />
          <#if !imageRef?starts_with("/") && !imageRef?starts_with("http://") && !imageRef?starts_with("https://")>
            <#local imageRef = resource.URI.getParent().expand(imageRef) />
            <#local hackedProp = def.createProperty(imageRef.toString())>
            <#return hackedProp.getFormattedValue(format, locale) />
          </#if>
        <#recover>
        </#attempt>
        <#return prop.getFormattedValue(format, locale) />
      </#if>
    </#if>
  </#if>
</#function>

<#function fixRelativeMediaFile uri uriOrPath>
  <#attempt>
  <#if !uri?starts_with("/") && !uri?starts_with("http://") && !uri?starts_with("https://")>
     <#return uriOrPath.getParent().expand(uri).toString()>
  </#if>
  <#recover></#recover>
  <#return uri />
</#function>


<#function propResource resource propName prefix="">
  <#local prop = resource.getPropertyByPrefix(prefix, propName)!>
  <#return prop />
  <#if prop??>
    <#local def = prop.definition />
    <#local type = def.type />
    <#if type = 'IMAGE_REF'>
      <#return resource.getPropResource(def)! />
    </#if>
  </#if>
</#function>

<#function getMetadata metadata key multiple=false>
  <#if multiple = true>
    <#return metadata.getValues(key)! />
  </#if>
  <#return metadata.getValue(key)! />
</#function>


<#--
 * displayUserPrincipal
 *
 * Display the principal. If URL exists wrapped with a link and full description.
 *
 * @param principal the principal
-->
<#macro displayUserPrincipal principal>
  <#compress>
    <#if (principal.URL)??>
      <a class="user-principal" title="${principal.name}" href="${principal.URL}">${principal.description}</a>
    <#else>
      ${principal.name}
    </#if>
  </#compress>
</#macro>


<#--
 * displayGroupPrincipal
 *
 * Display the principal. If URL exists wrapped with a link and full description.
 *
 * @param principal the principal
-->
<#macro displayGroupPrincipal principal>
  <#compress>
    <#if (principal.URL)??>
      <a class="group-principal" title="${principal.description}" href="${principal.URL}">${principal.name}</a>
    <#else>
      ${principal.name}
    </#if>
  </#compress>
</#macro>

<#--
 * cssPlaceholder
 *
 * Output css references for the given place
 *
 * @param place The name for this place
-->
<#macro cssPlaceholder place>
  <#if cssRegistry?? && place??>
    <#list cssRegistry.getMedia(place) as cssURL>
      <link rel="stylesheet" href="${cssURL}" type="text/css" />
    </#list>
  </#if>
</#macro>


<#--
 * javascriptPlaceholder
 *
 * Output css references for the given place
 *
 * @param place The name for this place
-->
<#macro javascriptPlaceholder place>
  <#if javascriptRegistry?? && place??>
    <#list javascriptRegistry.getMedia(place) as jsURL>
      <script type="text/javascript" src="${jsURL}"></script>
    </#list>
  </#if>
</#macro>


<#--
 * csrfPreventionToken
 *
 * Generate a Cross-Site Request Forgery (CSRF) prevention token for a
 * given form URL. Produces a hidden form field with the token.
 *
 * @param url the url
-->
<#macro csrfPreventionToken url>
  <input type="hidden"
         name="${statics['vtk.security.web.CSRFPreventionHandler'].TOKEN_REQUEST_PARAMETER}"
         value="${VRTX_CSRF_PREVENTION_HANDLER.newToken(url)}" />
</#macro>


<#--
 * displayTime
 *
 * Display time as HH:mm:ss based on a positive integer as representation of time
 *
 * @param timeSec the integer
-->
<#macro displayTime timeSec >
    <#local sec = (timeSec % 60) />
    <#local min = ((timeSec-sec) % 3600) / 60 />
    <#local hours = ((timeSec-(sec+(min*60))) / 3600 ) />
    <#if (hours > 0)>${hours}:</#if>${min}:<#if (sec > 0 || (hours > 0 || min > 0) )>${sec?string("00")}</#if>
</#macro>

<#--
 * calculateResourceSize
 *
 * Display bytes approx. as float in the metric system
 *
 * @param contentLength the content length in bytes
-->
<#macro calculateResourceSize contentLength>
  <#if contentLength <= 1000>
    ${contentLength} B
  <#elseif contentLength <= 1000000>
    ${(contentLength / 1000)?string("0.#")} KB
  <#elseif contentLength <= 1000000000>
    ${(contentLength / 1000000)?string("0.#")} MB
  <#elseif contentLength <= 1000000000000>
    ${(contentLength / 1000000000)?string("0.#")} GB
  <#else>
    ${contentLength} B
  </#if>
</#macro>

<#--
 * calculateResourceSizeToKB
 *
 * Display bytes approx. as integer in KB
 *
 * @param contentLength the content length in bytes
-->
<#macro calculateResourceSizeToKB contentLength>
  ${(contentLength / 1000)?string("#")} KB
</#macro>

<#--
 * getEvenlyColumnDistribution
 *
 * Get number of items per column when totalItems is evenly distributed across maxColumns (from left to right)
 *
 * Based on: http://stackoverflow.com/questions/1244338/algorithm-to-evenly-distribute-items-into-3-columns
 * (with ?floor instead of ?round to distribute e.g. 10 as 4,3,3 and not 3,3,4)
 *
 * @param totalItems the total number of items
 * @param column the column
 * @param maxColumns the max number of columns
 * @return the number of items per column
-->
<#function getEvenlyColumnDistribution totalItems column maxColumns>
  <#assign n = (totalItems / maxColumns)?floor />
  <#if ((totalItems % maxColumns) >= column)>
    <#assign n = n + 1 />
  </#if>
  <#return n />
</#function>


<#--
 * recoverableResourceToIconResolver
 *
 * Resolves icon based on resource contentType
 * RecoverableResource does not support propValue to get obsoleted and therefore we use this extra macro temporarily.
 *
 * @param resource the resource
-->
<#macro recoverableResourceToIconResolver resource>
  <#compress>
    <#local iconText = "" />
    <#if resource.resourceType??>
      <#local iconText = resource.resourceType />
    </#if>
    <#local contentType = "" />
    <#if resource.contentType??>
      <#local contentType = resource.contentType />
    </#if>
    <#if iconText = "file">
      <#local iconText = resourceContentTypeToIconResolver(contentType) />
    </#if>
    ${iconText}
  </#compress>
</#macro>

<#--
 * resourceToIconResolver
 *
 * Resolves icon based on resource contentType
 *
 * @param resource the resource
-->
<#macro resourceToIconResolver resource>
  <#compress>
    <#local iconText = "" />
    <#if resource.resourceType??>
      <#local iconText = resource.resourceType />
    </#if>
    <#local contentType = propValue(resource, "contentType")! />
    <#if iconText = "file" && contentType??>
      <#local iconText = resourceContentTypeToIconResolver(contentType?markup_string) />
    </#if>
    <#if resource.published?? >
        <#local unpublishedColl = propValue(resource, 'unpublishedCollection')??>
        <#if unpublishedColl || !resource.published>
            <#local iconText = iconText + " unpublished">
        </#if>
    </#if>
    ${iconText}
  </#compress>
</#macro>

<#--
 * resourceContentTypeToIconResolver
 *
 * Resolves icon based on resource contentType
 *
 * @param contentType the contentTyoe
 * @return icon text
-->
<#function resourceContentTypeToIconResolver contentType>
  <#if contentType = "application/octet-stream">
    <#return "binary" />
  <#elseif contentType = "application/x-apple-diskimage">
    <#return "dmg" />
  <#elseif contentType = "application/zip"
        || contentType = "application/x-gzip"
        || contentType = "application/x-bzip2"
        || contentType = "application/x-7z-compressed"
        || contentType = "application/x-compress">
    <#return "zip" />
  <#elseif contentType = "application/java-archive">
    <#return "jar" />
  <#else>
    <#return "file" />
  </#if>
</#function>

<#-- REWORKED SPRINGS VERSION
 * 
 * formRadioButtons
 *
 * Show radio buttons.
 *
 * @param path the name of the field to bind to
 * @param options a map (value=label) of all the available options
 * @param separator the html tag or other character list that should be used to
 *        separate each option. Typically '&nbsp;', '<br>' or in most cases '<li>'
 * @param attributes any additional attributes for the element (such as class
 *        or CSS styles or size
-->

<#-- FIXME: Only works for CreateDocument -->
<#macro formRadioButtons path options pre post descriptions=[] titles=[] cTN=false attributes="" splitAfterFirstTitle="">
  <@spring.bind path/>
  <#local c = 0 />
  <#list options?keys as key>
    ${pre}
    <input type="radio" name="${spring.status.expression}" id="${key}" value="${key}"
      <#if spring.status.value?default("") == key>checked="checked"</#if>
      <#if (cTN && titles?has_content && titles[key]?exists)>onclick="createChangeTemplate(${titles[key]?string})"</#if> ${attributes} <@spring.closeTag/>
    <label for="${key}">${options[key]}</label>
    <#if (descriptions?has_content && descriptions[key]?exists)>
      <div class="radioDescription" id="${key}_description">${descriptions[key]}</div>
    </#if>
    ${post}
    <#if c = 0 && splitAfterFirstTitle != "">
      ${splitAfterFirstTitle}
    </#if>
    <#local c = c + 1 />
  </#list>
</#macro>


<#macro displayLinkOtherLang resource>
  <#assign linkOtherLanguage = vrtx.propValue(resource, "linkOtherLanguage")! />
  <#if linkOtherLanguage??>
    <a id="vrtx-change-language-link" href="${linkOtherLanguage}"><@vrtx.msg code="link-other-language" /><span class="offscreen-screenreader"> <@vrtx.msg code="link-other-language.offscreen" /></span></a>
  </#if>
</#macro>
