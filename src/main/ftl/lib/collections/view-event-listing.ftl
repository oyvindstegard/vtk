<#import "/lib/vortikal.ftl" as vrtx />
<#import "/lib/view-utils.ftl" as viewutils />

<#macro displayEvents collection hideNumberOfComments=false displayMoreURLs=false >

  <#local displayType = vrtx.propValue(collection, 'display-type', '', 'el') />
  <#if !displayType?has_content && searchComponents?has_content>
    <#list searchComponents as searchComponent>
      <@displayStandard searchComponent hideNumberOfComments displayMoreURLs />
    </#list>
  <#elseif displayType = 'calendar'>
    <@displayCalendar hideNumberOfComments displayMoreURLs />
  </#if>
  
</#macro>>

<#macro displayStandard collectionListing hideNumberOfComments displayMoreURLs >
  
  <#local events = collectionListing.files />
  <#if events?size &gt; 0>

    <div id="${collectionListing.name}" class="vrtx-resources ${collectionListing.name}">
    <#if collectionListing.title?exists && collectionListing.offset == 0>
      <h2>${collectionListing.title?html}</h2> 
    </#if>
    <#list events as event>
      <@displayEvent collectionListing event hideNumberOfComments displayMoreURLs />
    </#list>
   </div>
  </#if>

</#macro>

<#macro displayCalendar hideNumberOfComments displayMoreURLs>
  
  <#if allUpcoming?has_content>
    <@displayStandard allUpcoming hideNumberOfComments displayMoreURLs />
  <#elseif allPrevious?has_content>
    <@displayStandard allPrevious hideNumberOfComments displayMoreURLs />
  <#elseif specificDateEvents?has_content>
    <h2>${specificDateEventsTitle}</h2>
    <@displayStandard specificDateEvents hideNumberOfComments displayMoreURLs=false />
  <#elseif groupedByDayEvents?has_content || furtherUpcoming?has_content>
  
    <#if groupedByDayEvents?has_content>
      <div id="vrtx-daily-events">
        <h2>${groupedEventsTitle?html}</h2>
      <#assign count = 1 />
      <#list groupedByDayEvents as groupedEvents>
        <div id="vrtx-daily-events-${count}">
          <div class="vrtx-daily-events-date">
            <span class="vrtx-daily-events-date-day"><@vrtx.date value=groupedEvents.day format='dd' /></span>
            <span class="vrtx-daily-events-date-month"><@vrtx.date value=groupedEvents.day format='MMM' /></span>
          </div>
          <div class="vrtx-daily-event">
          <#local eventListing = groupedEvents.events /> 
          <#list eventListing.files as event>
            <@displayEvent eventListing event hideNumberOfComments displayMoreURLs=false />
          </#list>
          </div>      
        </div>
        <#assign count = count +1 />
      </#list>
      </div>
    </#if>

    <#if furtherUpcoming?has_content && furtherUpcoming.files?size &gt; 0>
      <h2>${furtherUpcomingTitle?html}</h2>
      <@displayStandard furtherUpcoming hideNumberOfComments displayMoreURLs=false />
    </#if>

  </#if>
  
  <div id="vrtx-additional-content">
     <div class="vrtx-frontpage-box" id="vrtx-event-calendar">
       <script type="text/javascript">
         $(document).ready(function(){

           $("#datepicker").datepicker({
             dateFormat: 'yy-mm-dd',
             onSelect: function(dateText, inst) {
               location.href = location.href.split('?')[0] + "?date=" + dateText;
             },
             monthNames: ['Januar','Februar','Mars','April','Mai','Juni','Juli','August','September','Oktober','November','Desember'],
             dayNamesMin: ['Sø', 'Ma', 'Ti', 'On', 'To', 'Fr', 'Lø'],
             firstDay: 1,
             beforeShowDay: function(day) {
              var date_str = [
                 day.getFullYear(),
                 day.getMonth() + 1,
                 day.getDate()
               ].join('-');

               if ($.inArray(date_str, ${eventDates}) != -1) {
                 return [true, 'vrtx-selected-date', '<@vrtx.msg code="eventListing.calendar.dayHasPlannedEvents" />'];      
               } else {
                 return [false, 'vrtx-unselected-date', '<@vrtx.msg code="eventListing.calendar.dayHasNoPlannedEvents" />'];
               }
             },
             onChangeMonthYear: function(year, month, inst) {
              
             }
           });

         });
       </script>
       <h2>Bla i arrangementer</h2>
       <div type="text" id="datepicker"></div>
     </div>
  </div>
  
  <#if groupedByDayEvents?has_content || furtherUpcoming?has_content>
   <div id="vrtx-events-nav">
      <a href="${viewAllUpcomingURL}"><@vrtx.msg code="eventListing.allUpcoming" default="Upcoming events"/></a>
      <a href="${viewAllPreviousURL}"><@vrtx.msg code="eventListing.allPrevious" default="Previous events"/></a>
    </div>
  </#if>
</#macro>

<#macro displayEvent parent event hideNumberOfComments displayMoreURLs >

  <#local locale = springMacroRequestContext.getLocale() />
  
  <#local title = vrtx.propValue(event, 'title') />
  <#local introImg = vrtx.prop(event, 'picture')  />
  <#local intro = vrtx.prop(event, 'introduction')  />
  <#local location  = vrtx.prop(event, 'location')  />
  <#local caption = vrtx.propValue(event, 'caption')  />
  <#local endDate = vrtx.prop(event, 'end-date') />
  <#local hideEndDate = !endDate?has_content || !parent.hasDisplayPropDef(endDate.definition.name) />
  <#local hideLocation = !location?has_content || !parent.hasDisplayPropDef(location.definition.name) />
 
  <#-- Flattened caption for alt-tag in image -->
  <#local captionFlattened>
    <@vrtx.flattenHtml value=caption escape=true />
  </#local>
  <div class="vrtx-resource vevent">
    <#if introImg?has_content && parent.hasDisplayPropDef(introImg.definition.name)>
      <#local src = vrtx.propValue(event, 'picture', 'thumbnail') />
      <a class="vrtx-image" href="${parent.urls[event.URI]?html}">
        <#if caption != ''>
          <img src="${src?html}" alt="${captionFlattened}" />
        <#else>
          <img src="${src?html}" alt="${vrtx.getMsg("article.introductionImageAlt")}" />
        </#if>
      </a>
    </#if>
    <div class="vrtx-title">
      <a class="vrtx-title summary" href="${parent.urls[event.URI]?html}">${title?html}</a>
    </div>

    <div class="time-and-place">
      <@viewutils.displayTimeAndPlace event title hideEndDate hideLocation hideNumberOfComments />
    </div>

    <#if intro?has_content && parent.hasDisplayPropDef(intro.definition.name)>
      <div class="description introduction">${intro.value}</div>
    </#if>

    <#local hasBody = vrtx.propValue(event, 'hasBodyContent') == 'true' />
    <#if displayMoreURLs && hasBody>
      <div class="vrtx-read-more">
        <a href="${parent.urls[event.URI]?html}" class="more" title="${title?html}">
          <@vrtx.msg code="viewCollectionListing.readMore" />
        </a>
      </div>
    </#if>
  </div>

</#macro>