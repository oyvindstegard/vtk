<#ftl strip_whitespace=true output_format="HTML" auto_esc=true>

<#import "/lib/vtk.ftl" as vrtx />
<#import "/lib/view-utils.ftl" as viewutils />
<#import "/lib/collections/view-event-listing.ftl" as events />
<#import "/lib/collections/view-project-listing.ftl" as projects />
<#import "/lib/collections/view-person-listing.ftl" as persons />

<#macro displayTagElements tagElements showOccurences=false split="" limit=0>
  <div class="vrtx-tags-service">
  
  <#local count = 1 />
  
  <#-- Split in thirds -->
  <#if split = "thirds">
    <#local tagElementsSize = tagElements?size />
    <#if (limit > 0 && tagElementsSize > limit)>
      <#local tagElementsSize = limit />
    </#if>
    <#local colOneCount = vrtx.getEvenlyColumnDistribution(tagElementsSize, 1, 3) />
    <#local colTwoCount = vrtx.getEvenlyColumnDistribution(tagElementsSize, 2, 3) />
    <#local colThreeCount = vrtx.getEvenlyColumnDistribution(tagElementsSize, 3, 3) />
    <ul class="vrtx-tag thirds-left">
    <#list tagElements as element>
    
      <#-- Tag element -->
      <li class="vrtx-tags-element-${count}">
        <a class="tags" href="${element.linkUrl}" rel="tags">${element.text}<#if showOccurences> (${element.occurences})</#if></a>
      </li>
      
      <#if (count = colOneCount && colTwoCount > 0)>
        </ul><ul class="vrtx-tag thirds-middle">
      </#if>
      <#if ((count = colOneCount + colTwoCount) && colThreeCount > 0)>
        </ul><ul class="vrtx-tag thirds-right">
      </#if>
      
      <#-- Limit -->
      <#if count = limit>
        <#break>
      </#if> 
      <#local count = count + 1 />
    </#list>
    </ul>
  <#-- Split in fourths -->
  <#elseif split = "fourths">
    <#local tagElementsSize = tagElements?size />
    <#if (limit > 0 && tagElementsSize > limit)>
      <#local tagElementsSize = limit />
    </#if>
    <#local colOneCount = vrtx.getEvenlyColumnDistribution(tagElementsSize, 1, 4) />
    <#local colTwoCount = vrtx.getEvenlyColumnDistribution(tagElementsSize, 2, 4) />
    <#local colThreeCount = vrtx.getEvenlyColumnDistribution(tagElementsSize, 3, 4) />
    <#local colFourCount = vrtx.getEvenlyColumnDistribution(tagElementsSize, 4, 4) />
    <ul class="vrtx-tag fourths-left">
    <#list tagElements as element>
    
      <#-- Tag element -->
      <li class="vrtx-tags-element-${count}">
        <a class="tags" href="${element.linkUrl}" rel="tags">${element.text}<#if showOccurences> (${element.occurences})</#if></a>
      </li>
      
      <#if (count = colOneCount && colTwoCount > 0)>
        </ul><ul class="vrtx-tag fourths-middle">
      </#if>
      <#if ((count = colOneCount + colTwoCount) && colThreeCount > 0)>
        </ul><ul class="vrtx-tag fourths-middle">
      </#if>
      <#if ((count = colOneCount + colTwoCount + colThreeCount) && colFourCount > 0)>
        </ul><ul class="vrtx-tag fourths-right">
      </#if>
      
      <#-- Limit -->
      <#if count = limit>
        <#break>
      </#if> 
      <#local count = count + 1 />
    </#list>
    </ul>
  <#else>
    <ul class="vrtx-tag">
    <#list tagElements as element>
    
      <#-- Tag element -->
      <li class="vrtx-tags-element-${count}">
        <a class="tags" href="${element.linkUrl}" rel="tags">${element.text}<#if showOccurences> (${element.occurences})</#if></a>
      </li>
      
      <#-- Limit -->
      <#if count = limit>
        <#break>
      </#if>
      <#local count = count + 1 />
    </#list>
    </ul>

  </#if>
  
  </div>
</#macro>

<#macro displayAlphabeticalTagElements rangesTagElements showOccurences=false>
  <div class="vrtx-tags-service">
  
  <#local count = 1 />

  <div id="vrtx-tags-alphabetical-tabs">
    <ul style="display: none">
      <#list rangesTagElements?keys as rangeKey>
        <li>
          <a href="#vrtx-tags-alphabetical-${rangeKey}" name="vrtx-tags-alphabetical-${rangeKey}">${rangeKey?upper_case?replace("-", " - ")}</a>
        </li>
      </#list>
    </ul>
    <#list rangesTagElements?keys as rangeKey>
      <div id="vrtx-tags-alphabetical-${rangeKey}">
        <#local range = rangesTagElements[rangeKey]>
        <#list range?keys as letterKey>
          <h2>${letterKey?upper_case}</h2>
          <ul class="vrtx-tag">
            <#local tagElements = range[letterKey]>
            <#list tagElements as element>
              <#-- Tag element -->
              <li class="vrtx-tags-element-${count}">
                <a class="tags" href="${element.linkUrl}" rel="tags">${element.text}<#if showOccurences> (${element.occurences})</#if></a>
              </li>
              <#local count = count + 1 />
            </#list>
          </ul>
        </#list>
      </div>
    </#list>
  </div>
  
  </div>
</#macro>

<#macro displayTagListing listing>
  
  <div class="tagged-resources vrtx-resources">
    <#if resourceType??>
      <#if resourceType = 'person'>
        <@persons.displayPersons listing />
      <#elseif resourceType = 'structured-project'>
        <@projects.displayProjects listing />
      <#elseif resourceType = 'event' || resourceType = 'structured-event'>
        <@events.displayEvents
          collection=collection hideNumberOfComments=hideNumberOfComments displayMoreURLs=true considerDisplayType=false />
      <#else>
        <@displayCommonTagListing listing />
      </#if>
    <#else>
      <@displayCommonTagListing listing />
    </#if>
  </div>
  
  <div class="vrtx-paging-feed-wrapper">
  	<#if pageThroughUrls?? >
		<@viewutils.displayPageThroughUrls pageThroughUrls page />
	</#if>
    
    <#if alternativeRepresentations??>
      <#list alternativeRepresentations as alt>
        <#if alt.contentType = 'application/atom+xml'>
          <div class="vrtx-feed-link">
            <a id="vrtx-feed-link" href="${alt.url}"><@vrtx.msg code="viewCollectionListing.feed.fromThis" /></a>
          </div>
          <#break />
        </#if>
      </#list>
     </#if>
  </div>
  
</#macro>

<#macro displayCommonTagListing listing>
  
  <#local resources = listing.entries />
  <#local i = 1 />

  <#list resources as resourceEntry>
    <#local resource = resourceEntry.propertySet />
    <#local resourceTitle = vrtx.prop(resource, "title", "").getFormattedValue() />
    <#local introImageProp = vrtx.prop(resource, "picture", "")!"" />
    <#local introImageAlt = vrtx.propValue(resource, "pictureAlt")! />
    <div class="vrtx-resource" id="vrtx-result-${i}">
      <#if introImageProp != "">
      <a href="${resourceEntry.url}" class="vrtx-image">
        <#local src = vrtx.propValue(resource, 'picture', 'thumbnail')! />
        <img src="${src}" alt="${introImageAlt}" />
      </a>
      </#if>
      <div class="vrtx-title">
        <a href="${resourceEntry.url}" class="vrtx-title"> ${resourceTitle}</a>
      </div>

      <#local publishDate = vrtx.propValue(resource, 'publish-date')! />
      <#if publishDate?has_content>
        <div class="publish-date">
          <@vrtx.msg code="viewCollectionListing.publishedDate"
             default="" args=[] locale=locale />${publishDate}
        </div>
      </#if>

      <#local introduction = vrtx.getIntroduction(resource)! />
      <#if introduction?has_content && !listing.hasDisplayPropDef("hide-introduction")>
        <div class="introduction">
          ${introduction}
          <#local hasBody = (vrtx.propValue(resource, 'hasBodyContent')!'false') == 'true' />
          <#if hasBody>
            <div class="vrtx-read-more">
              <a href="${resourceEntry.url}" class="more">
                <@vrtx.msg code="viewCollectionListing.readMore" default="" args=[] locale=locale />
              </a>
            </div>
          </#if>
        </div>
      </#if>
    </div>
    <#local i = i + 1 />
  </#list>
  
</#macro>
