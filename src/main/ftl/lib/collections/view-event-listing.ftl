<#ftl strip_whitespace=true output_format="HTML" auto_esc=true>
<#import "/lib/vtk.ftl" as vrtx />
<#import "/lib/view-utils.ftl" as viewutils />

<#macro displayEvents collection hideNumberOfComments=false displayMoreURLs=false considerDisplayType=true >

  <#local displayType = vrtx.propValue(collection, 'display-type', '', 'el')! />

  <#if considerDisplayType && displayType?has_content && displayType = 'calendar'>
    <@displayCalendar collection hideNumberOfComments displayMoreURLs />
  <#elseif searchComponents?has_content>
    <#list searchComponents as searchComponent>
      <@displayStandard searchComponent hideNumberOfComments displayMoreURLs />
    </#list>
  </#if>

</#macro>

<#macro displayStandard collectionListing hideNumberOfComments displayMoreURLs showTitle=true>
  <#local events = collectionListing.entries />
  <#if events?size &gt; 0>
    <div id="${collectionListing.name}" class="vrtx-resources ${collectionListing.name}">
      <#if collectionListing.title?exists && collectionListing.offset == 0 && showTitle>
        <h2>${collectionListing.title}</h2>
      </#if>
      <#assign count = 1 />
      <#list events as eventEntry>
        <#if events?size == count>
          <div class="vrtx-last-event">
        </#if>
        <@displayEvent collectionListing eventEntry hideNumberOfComments displayMoreURLs />
        <#if events?size == count>
          </div>
        </#if>
        <#assign count = count +1 />
      </#list>
    </div>
  </#if>
</#macro>

<#macro displayCalendar collection hideNumberOfComments displayMoreURLs>
  <#if groupedByDayEvents?has_content && groupedByDayEvents?size &gt; 0>
    <div id="vrtx-main-content" class="vrtx-calendar-listing">
  <#else>
    <div id="vrtx-main-content" class="vrtx-calendar-listing vrtx-no-daily-events">
  </#if>
  <#if allupcoming?has_content>
      <@vrtx.displayLinkOtherLang collection />
	  <h1>${allupcomingTitle}</h1>
	  <#if allupcoming.entries?size &gt; 0 >
	    <@displayStandard allupcoming hideNumberOfComments displayMoreURLs false />
	  <#else>
	    <p class="vrtx-events-no-planned">${allupcomingNoPlannedTitle}</p>
	  </#if>
  <#elseif allprevious?has_content>
    <@vrtx.displayLinkOtherLang collection />
    <h1>${allpreviousTitle}</h1>
    <#if allprevious.entries?size &gt; 0 >
      <@displayStandard allprevious hideNumberOfComments displayMoreURLs false />
    <#else>
	    <p class="vrtx-events-no-planned">${allpreviousNoPlannedTitle}</p>
    </#if>
  <#elseif specificDate?has_content && specificDate>
    <@vrtx.displayLinkOtherLang collection />
    <h1 class="vrtx-events-specific-date">${specificDateEventsTitle}</h1>
    <#if specificDateEvents?has_content && specificDateEvents.entries?size &gt; 0>
      <@displayStandard specificDateEvents hideNumberOfComments displayMoreURLs=false />
    <#else>
      <p class="vrtx-events-no-planned">${noPlannedEventsMsg}</p>
    </#if>
  <#else>
    <div class="vrtx-events-calendar-introduction">
      <@vrtx.displayLinkOtherLang collection />
      <#local title = vrtx.propValue(collection, "title", "flattened") />
      <h1>${title}</h1>
      <#local introduction = vrtx.getIntroduction(collection)! />
      <#local introductionImage = vrtx.propValue(collection, "picture")! />
      <#if introduction?has_content || introductionImage?has_content>
        <div class="vrtx-introduction">
          <#-- Image -->
          <@viewutils.displayImage collection />
          <#-- Introduction -->
          <#if introduction?has_content>
            ${introduction?no_esc}
          </#if>
        </div>
      </#if>
    </div>
    <#if groupedByDayEvents?has_content && groupedByDayEvents?size &gt; 0>
      <div id="vrtx-daily-events">
        <h2 class="vrtx-events-title">${groupedEventsTitle}</h2>
        <#assign count = 1 />
        <#list groupedByDayEvents as groupedEvents>
          <div id="vrtx-daily-events-${count}" class="vrtx-daily-events-listing">
            <div class="vrtx-daily-events-date">
              <#local todayDay = vrtx.calcDate(today, 'dd') />
              <#local todayMonth = vrtx.calcDate(today, 'MM') />
              <#local currentDay = vrtx.calcDate(groupedEvents.day, 'dd') />
              <#local currentMonth = vrtx.calcDate(groupedEvents.day, 'MM') />
              <#local todayLocalized = vrtx.getMsg("eventListing.calendar.today", "today") />

              <#if (vrtx.parseInt(currentDay) == vrtx.parseInt(todayDay))
                && (vrtx.parseInt(currentMonth) == vrtx.parseInt(todayMonth)) >
                <span class="vrtx-daily-events-date-day vrtx-daily-events-date-today">${todayLocalized}</span>
              <#else>
                <span class="vrtx-daily-events-date-day">${currentDay}</span>
              </#if>
              <span class="vrtx-daily-events-date-month"><@vrtx.date value=groupedEvents.day format='MMM' />.</span>
            </div>
            <div class="vrtx-daily-event">
              <#local eventListing = groupedEvents.events />
              <#assign subcount = 1 />
              <#list eventListing.entries as eventEntry>
                <#if groupedByDayEvents?size == count && eventListing.entries?size == subcount>
                  <div class="vrtx-last-daily-event">
                </#if>
                <@displayEvent eventListing eventEntry hideNumberOfComments displayMoreURLs=false />
                <#if groupedByDayEvents?size == count && eventListing.entries?size == subcount>
                  </div>
                </#if>
	            <#assign subcount = subcount +1 />
	          </#list>
	        </div>
	      </div>
	      <#assign count = count +1 />
	    </#list>
	  </div>
    </#if>

    <#if furtherUpcoming?has_content && furtherUpcoming.entries?size &gt; 0>
      <div class="vrtx-events-further-upcoming">
        <h2 class="vrtx-events-further-upcoming">${furtherUpcomingTitle}</h2>
        <@displayStandard furtherUpcoming hideNumberOfComments displayMoreURLs=false />
      </div>
    </#if>

    <#if furtherUpcoming?has_content && furtherUpcoming.entries?size &gt; 0>
      <div id="vrtx-events-nav">
    <#else>
      <div id="vrtx-events-nav" class="vrtx-events-nav-top-border">
    </#if>
    <#if viewAllUpcomingURL??>
    <a href="${viewAllUpcomingURL}" id="vrtx-events-nav-all-upcoming">${viewAllUpcomingTitle}</a>
    </#if>
    <#if viewAllPreviousURL??>
    <a href="${viewAllPreviousURL}" id="vrtx-events-nav-all-previous">${viewAllPreviousTitle}</a>
    </#if>
    </div>
    </#if>
    <@viewutils.pagingSubscribeServices />
  </div>

  <div id="vrtx-additional-content">
    <#if allowedDates??>
    <div id="vrtx-event-calendar">
      <#local activeDate = "" />
      <#if requestedDate?exists && requestedDate?has_content>
        <#local activeDate = requestedDate />
      </#if>
      <#local language = vrtx.getMsg("eventListing.calendar.lang", "en") />
      <script type="text/javascript">
      <!--
        $(document).ready(function() {
          eventListingCalendar(${allowedDates?no_esc}, '${activeDate}', '${dayHasPlannedEventsTitle}', '${dayHasNoPlannedEventsTitle}', '${language}');
        });
      // -->
      </script>
      <div id="datepicker"></div>
    </div>
    <#local additionalContent = vrtx.propValue(collection, "additionalContents")! />
    <#if additionalContent?has_content>
    <div id="vrtx-related-content">
    <@vrtx.invokeComponentRefs additionalContent />
   </div>
  </#if>
  </#if>
  </div>
</#macro>

<#macro displayEvent parent eventEntry hideNumberOfComments displayMoreURLs >

  <#local event = eventEntry.propertySet />
  <#local locale = springMacroRequestContext.getLocale() />

  <#local title = vrtx.propValue(event, 'title')! />
  <#local introImg = vrtx.prop(event, 'picture')!  />
  <#local intro = vrtx.prop(event, 'introduction')!  />
  <#local location  = vrtx.prop(event, 'location')!  />
  <#local caption = vrtx.propValue(event, 'caption')!  />
  <#local endDate = vrtx.prop(event, 'end-date')! />
  <#local hideEndDate = !endDate?has_content || parent.hasDisplayPropDef("hide-end-date") />
  <#local hideLocation = !location?has_content || parent.hasDisplayPropDef("hide-location") />

  <div class="vrtx-resource vevent">
    <#if introImg?has_content && !parent.hasDisplayPropDef("hide-introduction-image")>
      <#local introImgURI = vrtx.propValue(event, 'picture')! />
      <#if introImgURI?has_content>
	<#local thumbnail =  vrtx.relativeLinkConstructor(introImgURI, 'displayThumbnailService') />
      <#else>
	<#local thumbnail = "" />
      </#if>
      <#local introImgAlt = vrtx.propValue(event, 'pictureAlt')! />
      <a class="vrtx-image" href="${eventEntry.url}">
        <img src="${thumbnail}" alt="<#if introImgAlt?has_content>${introImgAlt}</#if>" />
      </a>
    </#if>
    <div class="vrtx-title">
      <a class="vrtx-title summary" href="${eventEntry.url}">${title}</a>
    </div>

    <div class="time-and-place">
      <@viewutils.displayTimeAndPlace event title hideEndDate hideLocation hideNumberOfComments />
    </div>

    <#if intro?has_content && !parent.hasDisplayPropDef("hide-introduction")>
      <div class="description introduction">
        <@vrtx.linkResolveFilter intro.value eventEntry.url requestURL />
      </div>
    </#if>

    <#local hasBody = vrtx.propValue(event, 'hasBodyContent')!'true' />
    <#if displayMoreURLs && hasBody == 'true'>
      <div class="vrtx-read-more">
        <a href="${eventEntry.url}" class="more" title="${title}">
          <@vrtx.msg code="viewCollectionListing.readMore" />
        </a>
      </div>
    </#if>
  </div>
</#macro>
