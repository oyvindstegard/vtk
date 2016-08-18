<#ftl strip_whitespace=true output_format="HTML" auto_esc=true>
<#import "/lib/vtk.ftl" as vrtx />
<#import "/lib/view-utils.ftl" as viewutils />

<#if conf.auth>
  <#assign include = "false">
  <#if (conf.type = "groupedByDayEvents" && groupedByDayEvents?has_content)>
    <div class="vrtx-event-component vrtx-event-component-psd">
    <#assign include = "true">
  <#elseif (conf.type = "list-once" && events?has_content)>
    <div class="vrtx-event-component vrtx-event-component-res">
    <#assign include = "true">
  <#elseif conf.includeIfEmpty>
    <div class="vrtx-event-component">
    <#assign include = "true">
  </#if>

    <#if (conf.eventsTitle && include = "true")>
      <h2><a href="${conf.uri}">${eventsTitle}</a></h2>
    </#if>
    <#if (conf.type = "groupedByDayEvents" && groupedByDayEvents?has_content)>
      <#assign groupedByDayEventsSize = groupedByDayEvents?size />
      <#list groupedByDayEvents as event>
        <@displayGroupedByDayEvents event.propertySet event.date event.showTime event_index+1 groupedByDayEventsSize />
      </#list>
    <#elseif (conf.type = "list-once" && events?has_content)>
      <#assign eventsSize = events.entries?size />
      <#list events.entries as eventEntry>
        <@displayEvent eventEntry eventEntry_index+1 eventsSize />
      </#list>
    <#elseif (include = "true")>
      <p class="vrtx-empty-message">
      <#if conf.emptyMsg?exists>
        ${conf.emptyMsg}
      <#elseif conf.showOnlyOngoing?exists && conf.showOnlyOngoing>
        <@vrtx.msg code="eventListing.noOngoing" />
      <#else>
        <@vrtx.msg code="eventListing.noPlanned.allupcoming" />
      </#if>
      </p>
    </#if>
    <#if (conf.allEventsLink && include = "true")>
      <div class="vrtx-more">
        <span><a href="${conf.uri}"><#if conf.allEventsText?exists>${conf.allEventsText}<#else><@vrtx.msg code="event.go-to-events" default="Go to events" /></#if></a></span>
      </div>
    </#if>

  <#if (include = "true")>
    </div>
  </#if>
</#if>

<#macro displayGroupedByDayEvents event startdate showTime nr last>
  <#if nr == last>
    <div class="vrtx-event-component-item vrtx-event-component-item-${nr} last">
  <#else>
    <div class="vrtx-event-component-item vrtx-event-component-item-${nr}">
  </#if>
    <#local title = vrtx.propValue(event, 'title') />
    <#local location  = vrtx.propValue(event, 'location')! />
    <#local intro = vrtx.propValue(event, 'introduction')!'' />
    <#local introImg = vrtx.prop(event, 'picture')!  />
    <#local caption = vrtx.propValue(event, 'caption')!  />
    <#local uri = vrtx.getUri(event) />

    <#if conf.dateIcon>
      <div class="vrtx-daily-events-date">
        <#local todayDay = vrtx.calcDate(today, 'dd') />
        <#local todayMonth = vrtx.calcDate(today, 'MM') />
        <#local currentDay = vrtx.calcDate(startdate, 'dd') />
        <#local currentMonth = vrtx.calcDate(startdate, 'MM') />
        <#local todayLocalized = vrtx.getMsg("eventListing.calendar.today", "today") />

        <#if (vrtx.parseInt(currentDay) == vrtx.parseInt(todayDay)) && (vrtx.parseInt(currentMonth) == vrtx.parseInt(todayMonth)) >
          <span class="vrtx-daily-events-date-day vrtx-daily-events-date-today">${todayLocalized}</span>
        <#else>
          <span class="vrtx-daily-events-date-day">${currentDay}</span>
        </#if>
        <span class="vrtx-daily-events-date-month"><@vrtx.date value=startdate format='MMM.' /></span>
      </div>
    </#if>

    <div class="vrtx-event-component-main">
      <#if conf.showPicture && introImg?has_content>
        <div class="vrtx-event-component-picture">
          <#local introImgURI = vrtx.propValue(event, 'picture')! />
          <#if introImgURI?has_content>
            <#local thumbnail =  vrtx.relativeLinkConstructor(introImgURI, 'displayThumbnailService') />
          <#else>
            <#local thumbnail = "" />
          </#if>
          <#local introImgAlt = vrtx.propValue(event, 'pictureAlt')!'' />
          <a class="vrtx-image" href="${uri}">
            <img src="${thumbnail}" alt="<#if introImgAlt?has_content>${introImgAlt}</#if>" />
          </a>
        </div>
     </#if>

      <div class="vrtx-event-component-title">
        <a class="vrtx-event-component-title summary" href="${uri}">${title}</a>
      </div>

      <div class="vrtx-event-component-misc">
        <#if conf.dateIcon && showTime && conf.showDate>
          <span class="vrtx-event-component-start-time">
            <@vrtx.date value=startdate format='hours-minutes' /><#if conf.showLocation && location?has_content>,</#if>
          </span>
        <#elseif showTime && conf.showDate>
          <span class="vrtx-event-component-start-time">
            <@vrtx.date value=startdate format='long' /><#if conf.showLocation && location?has_content>,</#if>
          </span>
        </#if>

        <#if conf.showLocation && location?has_content>
          <span class="vrtx-event-component-location location">${location}</span>
        </#if>

        <#if conf.addToCalendar>
          <div class="vrtx-number-of-comments-add-event-container">
            <span class="vrtx-add-event">
              <a class="vrtx-ical" href="${uri}?vrtx=ical"><@vrtx.msg code="event.add-to-calendar" /></a>
              <a class="vrtx-ical-help" href="${vrtx.getMsg("event.add-to-calendar.help-url")}" title="${vrtx.getMsg("event.add-to-calendar.help")}"></a>
            </span>
          </div>
        </#if>
      </div>

      <#if conf.eventDescription>
        <div class="vrtx-event-component-introduction">
          ${intro?no_esc}
        </div>
      </#if>
    </div>
  </div>
</#macro>

<#macro displayEvent eventEntry nr last>
<#if nr == last>
  <div class="vrtx-event-component-item vrtx-event-component-item-${nr} last">
<#else>
  <div class="vrtx-event-component-item vrtx-event-component-item-${nr}">
</#if>

    <#-- The actual event we are displaying -->
    <#local event = eventEntry.propertySet />

    <#local title = vrtx.propValue(event, 'title')! />
    <#local location  = vrtx.propValue(event, 'location')!  />
    <#local startDate = vrtx.propValue(event, 'start-date', 'long')! />
    <#local startDateShort = vrtx.propValue(event, 'start-date', 'short')! />
    <#local endDate = vrtx.propValue(event, 'end-date', 'long')! />
    <#local endDateShort = vrtx.propValue(event, 'end-date', 'short')! />
    <#local endDateTime = vrtx.propValue(event, 'end-date', 'hours-minutes')! />
    <#local intro = vrtx.propValue(event, 'introduction')!  />
    <#local introImg = vrtx.prop(event, 'picture')!  />
    <#local caption = vrtx.propValue(event, 'caption')!  />

    <#if conf.showPicture && introImg?has_content>
      <div class="vrtx-event-component-picture">
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
      </div>
    </#if>

    <div class="vrtx-event-component-title">
      <a class="vrtx-event-component-title summary" href="${eventEntry.url}">${title}</a>
    </div>

    <div class="vrtx-event-component-misc">
      <#if conf.showDate>
        <#local startiso8601 = vrtx.propValue(event, "start-date", "iso-8601")! />
        <#local endiso8601 = vrtx.propValue(event, "end-date", "iso-8601")! />
        <#local isostarthour = "" />
        <#if startiso8601?has_content>
          <#local isostarthour = startiso8601?substring(11, 16) />
        </#if>
        <#local isoendhour = "" />
        <#if endiso8601?has_content>
          <#local isoendhour = endiso8601?substring(11, 16) />
        </#if>
        <#local isostartdate = "" />
        <#if startiso8601?has_content>
          <#local isostartdate = startiso8601?substring(0, 10) />
        </#if>
        <#local isoenddate = "" />
        <#if endiso8601?has_content>
          <#local isoenddate = endiso8601?substring(0, 10) />
        </#if>
        <span class="vrtx-event-component-start<#if conf.showEndTime>-and-end</#if>-time">
          <#if isostarthour != "00:00">${startDate}<#else>${startDateShort}</#if>
          <#if conf.showEndTime && endiso8601?has_content>
            <#if isoendhour != "00:00" &&  isostartdate != isoenddate> - ${endDate}
            <#elseif isostartdate != isoenddate> - ${endDateShort}
            <#elseif isoendhour != "00:00"> - ${endDateTime}
            </#if>
          </#if>
          <#if conf.showLocation && location?has_content>,</#if>
        </span>
      </#if>

      <#if conf.showLocation && location?has_content>
        <span class="vrtx-event-component-location">${location}</span>
      </#if>

      <#if conf.addToCalendar>
        <span class="vrtx-add-event">
          <a class="vrtx-ical" href="${eventEntry.url}?vrtx=ical">
            <@vrtx.msg code="event.add-to-calendar" /></a>
          <a class="vrtx-ical-help" href="${vrtx.getMsg("event.add-to-calendar.help-url")}" title="${vrtx.getMsg("event.add-to-calendar.help")}"></a>
        </span>
      </#if>
    </div>

    <#if conf.eventDescription>
      <div class="vrtx-event-component-introduction">
        ${intro?no_esc}
      </div>
    </#if>
  </div>
</#macro>
