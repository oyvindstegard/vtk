<#ftl strip_whitespace=true>
<#import "/lib/vtk.ftl" as vrtx />
<#import "/lib/view-utils.ftl" as viewutils />

<#macro addScripts collection>
  <#if cssURLs?exists>
    <@addScriptURLs "css" "common" cssURLs />
    <@addScriptURLs "css" listingType cssURLs />
  </#if>
  <#if jsURLs?exists>
    <@addScriptURLs "js" "common" jsURLs />
    <@addScriptURLs "js" listingType jsURLs />
  </#if>
</#macro>

<#macro addScriptURLs scriptType listingType urls>
  
  <#if urls[listingType]?exists>
    <#list urls[listingType] as commonUrl>
      <#if scriptType == "css">
        <link rel="stylesheet" href="${commonUrl}" type="text/css" />
      <#elseif scriptType == "js">
        <script type="text/javascript" src="${commonUrl}"></script>
      </#if>
    </#list>
  </#if>

</#macro>

<#macro displayCollection collectionListing>

  <#local resourceEntries=collectionListing.entries />
  <#if (resourceEntries?size > 0)>
     <script type="text/javascript"><!--
       $(window).load(function() {
         var cut = $("#right-main").length ? ".last-four" : ".last-five";
	     $('ul.vrtx-image-listing').find(".vrtx-image-entry:not(" + cut + ")")
	       .css("marginRight", "18px !important;").end();
	   });
     // -->
     </script>
     
     <#if collectionListing.title?exists && collectionListing.offset == 0>
      <h2>${collectionListing.title?html}</h2>
     </#if>
    
     <div class="vrtx-image-listing-container">
       <ul class="vrtx-image-listing">
       <#assign count = 1 />

       <#list resourceEntries as entry>

         <#assign r = entry.propertySet />

         <#if count % 4 == 0 && count % 5 == 0>
           <li class="vrtx-image-entry last last-four last-five">
         <#elseif count % 4 == 0>
           <li class="vrtx-image-entry last last-four">
         <#elseif count % 5 == 0>
           <li class="vrtx-image-entry last-five">
         <#else>
           <li class="vrtx-image-entry">
         </#if>
         
         <div class="vrtx-image-container">
           <#if vrtx.isOfType("audio", r.resourceType) >
             <a href="${entry.url?html}">
               <img src="/vrtx/__vrtx/static-resources/themes/default/icons/audio-icon.png" alt="" />
             </a>
           <#elseif vrtx.isOfType("video", r.resourceType)>
               <#local posterImgURI = vrtx.propValue(r, 'poster-image') />
	           <#if posterImgURI?exists && posterImgURI != "">
	    	       <#local thumbnail =  vrtx.relativeLinkConstructor(posterImgURI, 'displayThumbnailService') />
	    	   <#else>
	    		<#local thumbnail =  vrtx.relativeLinkConstructor(r.URI, 'displayThumbnailService') />
	   	   </#if>
            	<a href="${entry.url?html}">
            	  <img src="${thumbnail?html}" alt="" />
             	</a>
            </#if>
         </div>
         
         <div class="vrtx-image-info">
           <div class="vrtx-image-title">
             <a class="vrtx-title" href="${entry.url?html}">${vrtx.propValue(r, "title", "", "")?html}</a>
		       </div>
		   
           <#list collectionListing.displayPropDefs as displayPropDef>
             <#assign val = "" />
             <#if displayPropDef.name = 'introduction'>
               <#assign val = vrtx.getIntroduction(r) />
             <#elseif displayPropDef.type = 'IMAGE_REF'>
             <#elseif displayPropDef.name = 'lastModified'>
               <#assign val = vrtx.propValue(r, displayPropDef.name, 'short') />
             <#elseif displayPropDef.name = 'duration'  >
                <#if r.getProperty(displayPropDef)?exists >
                  <div class="${displayPropDef.name}">
                      <@vrtx.displayTime r.getProperty(displayPropDef).intValue />
                  </div>
                </#if>
             <#else>
                <#assign val = vrtx.propValue(r, displayPropDef.name) />
             </#if>
             <#if val?has_content>
               <div class="${displayPropDef.name}">
                 ${val}
               </div>
             </#if>
           </#list>
         </div>
         </li>
         <#assign count = count +1 />
       </#list>
       </ul>
     </div>
  </#if>
</#macro>
