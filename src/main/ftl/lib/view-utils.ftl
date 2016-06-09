<#ftl strip_whitespace=true output_format="HTML" auto_esc=true>
<#--
  - File: view-utils.ftl
  - 
  - Description: Utility macros for displaying content in views
  -   
  -->

<#import "vtk.ftl" as vrtx />

<#--
 * Displays the introduction, if any, of a resource.
 * 
 * @param resource The resource to display introduction from
-->
<#macro displayIntroduction resource>
  <#local introduction = vrtx.propValue(resource, "introduction") />
  <#if introduction != "">
    <div class="vrtx-introduction">${introduction?no_esc}</div>
  </#if>
</#macro>

<#--
 * Displays the image-property of a resource.
 * 
 * @param resource The resource to display image from
-->
<#macro displayImage resource displayAsThumbnail=false>
  
  <#local imageRes = vrtx.propResource(resource, "picture")! />
  <#local introductionImage = vrtx.propValue(resource, "picture")! />
  <#local introductionImageAlt = vrtx.propValue(resource, "pictureAlt")! />
  <#local caption = vrtx.propValue(resource, "caption")! />
  
  <#if introductionImage??>
  
    <#if imageRes??>
      <img class="vrtx-introduction-image" src="${introductionImage}" alt="${introductionImageAlt}" />
    <#else>

      <#if displayAsThumbnail>
        <#local introductionImage = vrtx.propValue(resource, "picture", "thumbnail") />
      </#if>

      <#local pixelWidth = imageRes.getValueByName("pixelWidth")!"" />
      <#local photographer = imageRes.getValueByName("photographer")!"" />
        
      <#local style="" />
      <#if pixelWidth != "">
        <#local style = "width:" + pixelWidth+ "px;" />
      </#if>
        
      <#if caption != ""><#-- Caption is set -->
        <div class="vrtx-introduction-image" <#if style?has_content>style="${style}"</#if>>
	  <img src="${introductionImage}" alt="<#if introductionImageAlt?has_content>${introductionImageAlt}</#if>" />
          <div class="vrtx-imagetext">
            <div class="vrtx-imagedescription">${caption}</div>
            <span class="vrtx-photo">
              <#if photographer != ""><#-- Image authors is set -->
                <span class="vrtx-photo-prefix"><@vrtx.msg code="article.photoprefix" />: </span>${photographer}
              </#if>
            </span>
          </div>
        </div>
      <#else>
        <#if photographer != ""><#-- No caption but image author set -->
          <div class="vrtx-introduction-image" <#if style?has_content>style="${style}"</#if>>
            <img src="${introductionImage}" alt="<#if introductionImageAlt?has_content>${introductionImageAlt}</#if>" />
            <div class="vrtx-imagetext">
              <span class="vrtx-photo">
                <span class="vrtx-photo-prefix"><@vrtx.msg code="article.photoprefix" />: </span>${photographer}
              </span>
            </div>
          </div>
        <#else><#-- No caption or image author set -->
          <img class="vrtx-introduction-image" src="${introductionImage}" alt="<#if introductionImageAlt?has_content>${introductionImageAlt}</#if>" />
        </#if>
	  </#if>
    </#if>
   </#if>
  
</#macro>


<#--
 * Shows the start- and end-date of an event, seperated by a "-".
 * If the two dates are identical, only the time of enddate is shown.
 * 
 * @param resource The resource to evaluate dates from
-->
<#macro displayTimeAndPlace resource title hideEndDate=false hideLocation=false hideNumberOfComments=false>
  
  <#local start = vrtx.propValue(resource, "start-date")! />
  <#local startiso8601 = vrtx.propValue(resource, "start-date", "iso-8601")! />
  <#local startshort = vrtx.propValue(resource, "start-date", "short")! />
  <#local end = vrtx.propValue(resource, "end-date")! />
  <#local endiso8601 = vrtx.propValue(resource, "end-date", "iso-8601")! />
  <#local endshort = vrtx.propValue(resource, "end-date", "short")! />
  <#local endhoursminutes = vrtx.propValue(resource, "end-date", "hours-minutes")! />
  <#local location = vrtx.propValue(resource, "location")! />
  <#local mapurl = vrtx.propValue(resource, "mapurl")! />
  
  <#if startiso8601?has_content>
    <#local isostarthour = startiso8601?substring(11, 16) />
  </#if>
  
  <#if endiso8601?has_content>
    <#local isoendhour = endiso8601?substring(11, 16) />
  </#if>
  <#local locationMsgCode = "event.time" />
  <#if location?has_content && !hideLocation><#t/>
    <#local locationMsgCode = "event.time-and-place" />
  </#if>
  <span class="time-and-place"><@vrtx.msg code=locationMsgCode />:</span>
  <span class="summary" style="display:none;">${title}</span>
  <#if start?has_content>
    <abbr class="dtstart" title="${startiso8601}">
    <#if isostarthour?has_content && isostarthour != "00:00">
      ${start}<#t/>
    <#else>
      ${startshort}<#t/>
    </#if>
    </abbr><#t/>
  </#if>
  <#if end?has_content && !hideEndDate>
    <#if startshort == endshort>
      <#if isoendhour?has_content && isoendhour != "00:00">
        <#t /> - <abbr class="dtend" title="${endiso8601}">${endhoursminutes}</abbr><#rt />
      </#if>
    <#else>
      <#if start?has_content>
        (<@vrtx.msg code="event.ends" />) 
      <#else>
        - 
      </#if>
      <abbr class="dtend" title="${endiso8601}">
      <#if  isoendhour?has_content && isoendhour != "00:00">
        ${end}<#t/>
      <#else>
        ${endshort}<#t/>
      </#if>
      </abbr><#t/>
    </#if>
  </#if>
  <#if location?has_content && !hideLocation><#t/>,
    <span class="location">
    <#if !mapurl?has_content>
      ${location}
    <#else>
      <a href="${mapurl}">${location}</a>
    </#if>
    </span>
  </#if>
  
  <#local constructor = "freemarker.template.utility.ObjectConstructor"?new() />
  <#local currentDate = constructor("java.util.Date") />
  <#local isValidStartDate = validateStartDate(resource, currentDate)?string == 'true' />
  
  <#local numberOfComments = vrtx.prop(resource, "numberOfComments")! />
  <#if numberOfComments?has_content || isValidStartDate>	
    <div class="vrtx-number-of-comments-add-event-container">
  </#if>
  <#if !hideNumberOfComments >
    <#local locale = springMacroRequestContext.getLocale() />
    <@displayNumberOfComments resource locale />
  </#if>
  
  <#if isValidStartDate>
    <span class="vrtx-add-event">
      <#assign uri = vrtx.getUri(resource) />
      <a class="vrtx-ical" href="${uri}?vrtx=ical"><@vrtx.msg code="event.add-to-calendar" /></a><a class="vrtx-ical-help" href="${vrtx.getMsg("event.add-to-calendar.help-url")}" title="${vrtx.getMsg("event.add-to-calendar.help")}"></a>
    </span>
  </#if>
  
  <#if numberOfComments?has_content || isValidStartDate>
    </div>
  </#if>
	
</#macro>


<#--
 * Check the start date of an event to see if it's greater than the current date
 * Check if the event is "upcoming" and has a valid start date
 * 
 * @param event The resource to evaluate start date for
 * @param currentDate The date to compare the events start date to
-->
<#function validateStartDate event currentDate>
  <#local startDate = event.getPropertyByPrefix(nullArg, "start-date")?default("") />
  <#if !startDate?has_content>
    <#local startDate = event.getPropertyByPrefix('resource', "start-date")?default("") />
  </#if>
  <#if startDate != "">
    <#return startDate.getDateValue()?datetime &gt; currentDate?datetime />
  </#if>
  <#return "false"/>
</#function>

<#macro displayNumberOfComments resource locale  >
  <#local numberOfComments = vrtx.prop(resource, "numberOfComments")! />
  <#if numberOfComments?has_content >
    <#assign uri = vrtx.getUri(resource) />
    <a href="${uri}#comments" class="vrtx-number-of-comments">
    <#if numberOfComments.intValue?number &gt; 1>
      <@vrtx.localizeMessage code="viewCollectionListing.numberOfComments" default="" args=[numberOfComments.intValue] locale=locale />
    <#else>
      <@vrtx.localizeMessage code="viewCollectionListing.numberOfCommentsSingle" default="" args=[] locale=locale />
    </#if>
    </a>
  </#if>
</#macro>

<#macro displayPageThroughUrls pageThroughUrls page >
  <#if pageThroughUrls?exists && (pageThroughUrls?size > 1) >
    <span class="vrtx-paging-wrapper">
    	<#list pageThroughUrls as url>
          <a href="${url.url}" class="<#if url.title == "prev">vrtx-previous<#elseif url.title="next">vrtx-next<#else>vrtx-page-number</#if><#if (url.marked) > vrtx-marked</#if>"><#compress>
          <#if (url.title) = "prev" ><@vrtx.msg code="viewCollectionListing.previous" />
          <#elseif (url.title) = "next" ><@vrtx.msg code="viewCollectionListing.next" />
          <#else >${(url.title)}
          </#if>
          </#compress></a>
        </#list>
    </span>
  </#if>
</#macro>

<#macro displayDropdown type title titleLink='' displayDropdownTitleClose=true titleLinkTip='' toggle=false>
  <div class="vrtx-${type}-component vrtx-dropdown-component <#if !displayDropdownTitleClose>vrtx-dropdown-component-toggled<#else>vrtx-dropdown-component-not-toggled</#if><#if toggle> vrtx-dropdown-component-toggle</#if>">
    <#if titleLink != ''>
      <a href="${titleLink}" class="vrtx-${type}-title-link vrtx-dropdown-title-link">${title}</a>
      <a href="javascript:void(0)" class="vrtx-${type}-link vrtx-dropdown-link"><span class="offscreen-screenreader">${titleLinkTip}</span></a>
    <#else>
      <a href="javascript:void(0)" class="vrtx-${type}-link vrtx-dropdown-link">${title}</a>
    </#if>
    <div class="vrtx-${type}-wrapper vrtx-dropdown-wrapper">
      <div class="vrtx-${type}-wrapper-inner vrtx-dropdown-wrapper-inner">
        <#if displayDropdownTitleClose>
          <div class="vrtx-${type}-top vrtx-dropdown-top">
            <div class="vrtx-${type}-title vrtx-dropdown-title">${title}
              <a href="javascript:void(0)" class="vrtx-${type}-close-link vrtx-dropdown-close-link">
                <@vrtx.msg code="decorating.shareAtComponent.close" default="Close" />
              </a>
            </div>
          </div>
        </#if>
        <#nested>
      </div>
    </div>
  </div>
</#macro>

<#macro pagingSubscribeServices>
  <#if pageThroughUrls?exists || (alternativeRepresentations?exists && !(hideAlternativeRepresentation?exists && hideAlternativeRepresentation))>
    <div class="vrtx-paging-feed-wrapper">
    <#-- Previous/next URLs: -->
    <#if pageThroughUrls?exists >
      <@viewutils.displayPageThroughUrls pageThroughUrls page />
    </#if>
      <#-- XXX: Display first link with content type = atom: -->
      <#if alternativeRepresentations?exists && !(hideAlternativeRepresentation?exists && hideAlternativeRepresentation)>
        <#if (alternativeRepresentations?size > 1)>
          <#local title = vrtx.getMsg("eventListing.subscribe") />
          <@viewutils.displayDropdown "subscribe" title>
            <ul>
            <#list alternativeRepresentations as alt>
              <#if alt.contentType = 'application/atom+xml'>
                <li><a id="vrtx-feed-link" href="${alt.url}"><@vrtx.msg code="viewCollectionListing.feed.fromThis" /></a></li>
              <#elseif alt.contentType = 'text/calendar' && (displayEventListingICalLink?? && displayEventListingICalLink)>
                <#assign altUrl = alt.url?replace("http://", "webcal://") />
                <li><a id="vrtx-ical-link" href="${altUrl}"><@vrtx.msg code="eventListing.ical.add" /></a></li>
              </#if>
            </#list>
            </ul>
          </@viewutils.displayDropdown>
      <#else>
        <#list alternativeRepresentations as alt>
          <#if alt.contentType = 'application/atom+xml'>
            <div class="vrtx-feed-link">
              <a id="vrtx-feed-link" href="${alt.url}"><@vrtx.msg code="viewCollectionListing.feed.fromThis" /></a>
            </div>
          </#if>
        </#list>
      </#if>
    </#if>
    </div>
  </#if>
</#macro>
