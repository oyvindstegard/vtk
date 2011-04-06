<#ftl strip_whitespace=true>

<#--
  - File: view-collection-listing.ftl
  - 
  - Description: A HTML page that displays a collection listing.
  - 
  - Required model data:
  -   resourceContext
  -   collectionListing:
  -     collections
  -     files
  -     urls
  -     sortURLs
  -     sortProperty
  -  
  - Optional model data:
  -
  -->


<#import "/lib/vortikal.ftl" as vrtx />
<#import "/lib/dump.ftl" as dumper>
<#import "/lib/view-utils.ftl" as viewutils />
<#import "/layouts/subfolder-menu.ftl" as subfolder />

<#import "/lib/collections/view-collection-listing.ftl" as coll />
<#import "/lib/collections/view-article-listing.ftl" as articles />
<#import "/lib/collections/view-event-listing.ftl" as events />
<#import "/lib/collections/view-project-listing.ftl" as projects />
<#import "/lib/collections/view-master-listing.ftl" as master />
<#import "/lib/collections/view-research-group-listing.ftl" as groups />
<#import "/lib/collections/view-person-listing.ftl" as persons />
<#import "/lib/collections/view-image-listing.ftl" as images />
<#import "/lib/collections/view-blog-listing.ftl" as blogs />
<#import "/lib/collections/view-audio-video-listing.ftl" as audioVideo />

<#assign resource = collection />
<#assign title = vrtx.propValue(resource, "title") />
<#if overriddenTitle?has_content>
  <#assign title = overriddenTitle />
</#if>

<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<html xmlns="http://www.w3.org/1999/xhtml">
<head>

  <#if collection.resourceType = 'image-listing'>
    <@images.addScripts collection />
  <#else>
    <#if cssURLs?exists>
      <#list cssURLs as cssURL>
        <link rel="stylesheet" href="${cssURL}" />
      </#list>
    </#if>
    <#if printCssURLs?exists>
      <#list printCssURLs as cssURL>
        <link rel="stylesheet" href="${cssURL}" media="print" />
      </#list>
    </#if>
    <#if jsURLs?exists>
      <#list jsURLs as jsURL>
        <script type="text/javascript" src="${jsURL}"></script>
      </#list>
    </#if>
  </#if>

  <#if alternativeRepresentations?exists && !(hideAlternativeRepresentation?exists && hideAlternativeRepresentation)>
    <#list alternativeRepresentations as alt>
      <link rel="alternate" type="${alt.contentType?html}" title="${alt.title?html}" href="${alt.url?html}" />
    </#list>
  </#if>

  <title>${title?html}
    <#if page?has_content && !overriddenTitle?has_content>
      <#if "${page}" != "1"> - <@vrtx.msg code="viewCollectionListing.page" /> ${page}</#if>
    </#if>
  </title>
  
  <#if page?has_content>
    <#if "${page}" != "1"><meta name="robots" content="noindex, follow"/> </#if>
  </#if>
  
</head>
<body id="vrtx-${resource.resourceType}">
  
  <#assign page = page?default(1) />

  <#assign isBlogListing = resource.resourceType = 'blog-listing' />
  <#assign eventListingDisplayType = vrtx.propValue(resource, 'display-type', '', 'el') />
  <#assign isEventCalendarListing = (eventListingDisplayType?has_content && eventListingDisplayType = 'calendar') />
  
  <#if isEventCalendarListing>
    <div id="vrtx-calendar-listing">
  </#if>
  
  <#-- Regular "additional content" placed in right-column -->
  <#assign additionalContent = vrtx.propValue(resource, "additionalContents") />
  <#if collection.resourceType != 'image-listing'
       && collection.resourceType != 'person-listing' && !isEventCalendarListing && !isBlogListing>
    <div id="vrtx-content">
      <#if additionalContent?has_content>
        <div id="vrtx-main-content">
      </#if>
  </#if>
  
<#if !isEventCalendarListing>
    <h1>${title?html}
      <@projects.completed />
      <@master.completed />
      <#if page?has_content>
        <#if "${page}" != "1"> - <@vrtx.msg code="viewCollectionListing.page" /> ${page}</#if>
      </#if>
    </h1>
    <#if page == 1>
      <#-- Introduction and image -->
      <#assign introduction = vrtx.getIntroduction(resource) />
      <#assign introductionImage = vrtx.propValue(resource, "picture") />
      <#if !viewOngoingProjectsLink?exists && !isBlogListing &&
           (introduction?has_content || introductionImage != "")>
        <div class="vrtx-introduction">
          <#-- Image -->
      	  <@viewutils.displayImage resource />
          <#-- Introduction -->
          <#if introduction?has_content>
            ${introduction}
          </#if>
        </div>
      </#if>
      <#-- List collections: -->
      <#if subFolderMenu?exists> 
      	<div id="vrtx-collections" class="vrtx-collections">
  	    	<@subfolder.displaySubFolderMenu subFolderMenu />
  	    </div>
	  </#if> 
  </#if> 
</#if>

     <#-- XXX: Person listing "additional content" placed under introduction -->
     <#assign additionalContentPersonListing = vrtx.propValue(resource, "additionalContent", "", "pl") />
     <#if additionalContentPersonListing?has_content>
       <div class="vrtx-additional-content">
         <@vrtx.invokeComponentRefs additionalContentPersonListing />
       </div>
     </#if>

     <#-- List resources: -->
  	 <!--stopindex-->
     <#if collection.resourceType = 'event-listing'>
       <@events.displayEvents collection=collection hideNumberOfComments=hideNumberOfComments displayMoreURLs=true />
     <#elseif searchComponents?has_content>
       <#if collection.resourceType = 'article-listing'>
         <@articles.displayArticles page=page collectionListings=searchComponents hideNumberOfComments=hideNumberOfComments displayMoreURLs=true />
       <#else>
         <#list searchComponents as searchComponent>
           <#if collection.resourceType = 'person-listing'>
             <@persons.displayPersons searchComponent title />
           <#elseif collection.resourceType = 'project-listing'>
           		<#assign listingType = vrtx.propValue(collection, 'display-type', '', 'prl') />
           	  	<#if listingType = "alphabetical" >
           	  		<@projects.displayProjectsAlphabetical searchComponent />
           	  	<#else>
             		<@projects.displayProjects searchComponent />
           		</#if>
           	<#elseif collection.resourceType = 'master-listing'>
           		<#assign listingType = vrtx.propValue(collection, 'display-type', '', 'master') />
           	  	<#if listingType = "alphabetical" || overrideListingType?exists>    
           	  		<@master.displayMastersAlphabetical searchComponent />
           	  	<#else>
             		<@master.displayTable searchComponent collection />
           		</#if>
            <#elseif collection.resourceType = 'research-group-listing'>
           		<#assign listingType = vrtx.propValue(collection, 'display-type', '', 'rg') />
           	  	<#if listingType = "alphabetical" >
           	  		<@groups.displayResearchGroupsAlphabetical searchComponent />
           	  	<#else>
             		<@groups.displayResearchGroups searchComponent />
           		</#if>
           <#elseif collection.resourceType = 'image-listing'>
             <@images.displayImages searchComponent collection />
           <#elseif collection.resourceType = 'blog-listing'>
              <@blogs.displayBlogs searchComponent collection />
           <#elseif collection.resourceType = 'audio-video-listing' >
           	  <@audioVideo.displayCollection collectionListing=searchComponent />
           <#else>
              <@coll.displayCollection collectionListing=searchComponent />
           </#if>
         </#list>
       </#if>
     </#if>
     <@projects.projectListingViewServiceURL />
     <@master.masterListingViewServiceURL />
	 <div class="vrtx-paging-feed-wrapper">
		<#-- Previous/next URLs: -->
		<#if pageThroughUrls?exists >
			<@viewutils.displayPageThroughUrls pageThroughUrls page />
		</#if>
        <#-- XXX: display first link with content type = atom: -->
        <#if alternativeRepresentations?exists && !(hideAlternativeRepresentation?exists && hideAlternativeRepresentation)>
	        <#list alternativeRepresentations as alt>
	          <#if alt.contentType = 'application/atom+xml'>
	            <div class="vrtx-feed-link">
	              <a id="vrtx-feed-link" href="${alt.url?html}"><@vrtx.msg code="viewCollectionListing.feed.fromThis" /></a>
	            </div>
	            <#break />
	          </#if>
	        </#list>
	    </#if>
     </div>
     <#if collection.resourceType != 'image-listing'
          && collection.resourceType != 'person-listing' && !isEventCalendarListing && !isBlogListing>
         <#if additionalContent?has_content>
           </div><#-- end vrtx-main-content -->
           <div id="vrtx-additional-content">
             <div id="vrtx-related-content"> 
               <@vrtx.invokeComponentRefs additionalContent />
             </div>
           </div>
         </#if>
       </div><#-- end vrtx-content -->
     </#if>
     <#if isEventCalendarListing>
       </div>
     </#if>
    <!--startindex-->
  </body>
</html>

