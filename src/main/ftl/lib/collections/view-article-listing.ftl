<#ftl strip_whitespace=true output_format="HTML" auto_esc=true>
<#import "/lib/vtk.ftl" as vrtx />
<#import "/lib/view-utils.ftl" as viewutils />

<#macro displayArticles page collectionListings listingView hideNumberOfComments=false displayMoreURLs=false >

  <#if (collectionListings?size > 0)>

    <#assign i = 1 />

    <#local frontpageClass = "" />
    <#if page = 1>
      <#local frontpageClass = "vrtx-resources-frontpage" />
    </#if>
    <#if listingView == "2columns">
      <div id="articleListing.searchComponent" class="vrtx-resources vrtx-two-columns articleListing.searchComponent ${frontpageClass}">
    <#elseif listingView == "2columns+prio">
      <div id="articleListing.searchComponent" class="vrtx-resources vrtx-two-columns vrtx-resource-prioritize-first articleListing.searchComponent ${frontpageClass}">
    <#else>
      <div id="articleListing.searchComponent" class="vrtx-resources articleListing.searchComponent ${frontpageClass}">
    </#if>
    <#list collectionListings as articles>
      <#local entries=articles.entries />
      <#if (entries?size > 0)>
        <#list entries as entry>

          <#-- The actual resource we are displaying -->
          <#local entryPropSet = entry.propertySet />

          <#local locale = vrtx.getLocale(entryPropSet)! />
          <#local title = vrtx.propValue(entryPropSet, 'title')! />
          <#local introImg  = vrtx.prop(entryPropSet, 'picture')!  />
          <#local intro  = vrtx.prop(entryPropSet, 'introduction')!  />
          <#local caption = vrtx.propValue(entryPropSet, 'caption')!  />
          <#local publishDateProp = vrtx.prop(entryPropSet, 'publish-date')! />
          <#local hidePublishDate = vrtx.prop(entryPropSet, 'hide-published-date')! />

          <#local articleType = "vrtx-default-article" />
          <#if articles.name == "articleListing.featuredArticles">
            <#local articleType = "vrtx-featured-article" />
          </#if>
          <div id="vrtx-result-${i}" class="vrtx-resource ${articleType}<#if listingView == "2columns"> ${articleType}-<#if i % 2 == 0>right<#else>left</#if></#if><#if listingView == "2columns+prio"> ${articleType}-<#if ((i+1) % 2 == 0)>right<#else>left</#if></#if>">
          <#local introImgURI = vrtx.propValue(entryPropSet, 'picture')! />
           <#if introImgURI?has_content>
    	     <#local thumbnail =  vrtx.relativeLinkConstructor(introImgURI, 'displayThumbnailService') />
    	   <#else>
    	     <#local thumbnail = "" />
   	   </#if>
           <#if introImg?has_content && !articles.hasDisplayPropDef("hide-introduction-image")>
             <#local introImgAlt = vrtx.propValue(entryPropSet, 'pictureAlt')! />
             <a class="vrtx-image" href="${entry.url}">
               <img src="${thumbnail}" alt="<#if introImgAlt?has_content>${introImgAlt}</#if>" />
             </a>
           </#if>

            <div class="vrtx-title">
            <a class="vrtx-title" href="${entry.url}">${title}</a></div>
            <#if publishDateProp?has_content && !articles.hasDisplayPropDef("hide-published-date")>
              <div class="published-date">
                <span class="published-date-prefix">
                  <@vrtx.localizeMessage code="viewCollectionListing.publishedDate" default="" args=[] locale=locale />
                </span>
                <@vrtx.date value=publishDateProp.dateValue format='long' locale=locale />
              </div>
            </#if>

            <#if hideNumberOfComments?exists && !hideNumberOfComments >
               <#local numberOfComments = vrtx.prop(entryPropSet, "numberOfComments")! />
               <#if numberOfComments?has_content >
                 <div class="vrtx-number-of-comments-add-event-container">
                   <@viewutils.displayNumberOfComments entryPropSet locale />
                 </div>
               </#if>
            </#if>

            <#if intro?has_content && !articles.hasDisplayPropDef("hide-introduction")>
              <div class="description introduction"><@vrtx.linkResolveFilter intro.value entry.url requestURL /> </div>
            </#if>

            <#local hasBody = (vrtx.propValue(entryPropSet, 'hasBodyContent')!'') == 'true' />
            <#if displayMoreURLs && hasBody>
            <div class="vrtx-read-more">
              <a href="${entry.url}" class="more">
                <@vrtx.localizeMessage code="viewCollectionListing.readMore" default="" args=[] locale=locale />
              </a>
            </div>
            </#if>

          </div>
          
          <#if i == 1 && listingView == "2columns+prio">
            <div id="vrtx-resources-unprioritized">
          </#if>
          
          <#assign i = i + 1 />
        </#list>
      </#if>
    </#list>
    <#if listingView == "2columns+prio">
      </div>
    </#if>
    </div>
  </#if>

</#macro>
