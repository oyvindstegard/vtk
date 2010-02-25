<#import "/lib/vortikal.ftl" as vrtx />
<#import "/lib/view-utils.ftl" as viewutils />

<#macro displayArticles page collectionListings hideNumberOfComments=false displayMoreURLs=false >

  <#if collectionListings?size &gt; 0>
    <#assign i = 1 />
    
    <#local frontpageClass = "" />
    <#if page = 1>
      <#local frontpageClass = "vrtx-resources-frontpage" />
    </#if>
    
    <#--
      First of all, there is more than one searchcomponent, hence the list.
      Second, the searchcomponents aren't necessarily named "articleListing.searchComponent",
        but we wanna show the contents of them all in one common div.
      Thirdly, we don't know if there's any styling "out there" that uses this particular
        id. So we keep it...
    -->
    
    <div id="articleListing.searchComponent" class="vrtx-resources articleListing.searchComponent ${frontpageClass}">
    <#list collectionListings as articles>
      <#local resources=articles.files />
      <#if resources?size &gt; 0>
        <#list resources as r>
        
          <#local locale = springMacroRequestContext.getLocale() />
          <#if r.contentLocale?has_content>
            <#local locale = r.contentLocale />
          </#if>
          
          <#local title = vrtx.propValue(r, 'title') />
          <#local introImg  = vrtx.prop(r, 'picture')  />
          <#local publishedDate  = vrtx.prop(r, 'published-date')  />
          <#local publishDate = vrtx.propValue(r, 'publish-date') />
          <#local intro  = vrtx.prop(r, 'introduction')  />
          <#local caption = vrtx.propValue(r, 'caption')  />
          
          <#-- Flattened caption for alt-tag in image -->
          <#local captionFlattened>
            <@vrtx.flattenHtml value=caption escape=true />
          </#local>
          
          <#local articleType = "vrtx-default-article" />
          <#if articles.name == "articleListing.featuredArticles">
            <#local articleType = "vrtx-featured-article" />
          </#if>
          <div id="vrtx-result-${i}" class="vrtx-resource ${articleType}"> 
            <#local src = vrtx.propValue(r, 'picture', 'thumbnail') />
            <#if introImg?has_content && articles.hasDisplayPropDef(introImg.definition.name)>            
               <a class="vrtx-image" href="${articles.urls[r.URI]?html}">        
                 <#if caption != ''>
                    <img src="${src?html}" alt="${captionFlattened}" />
                  <#else>
                    <img src="${src?html}" alt="${vrtx.getMsg("article.introductionImageAlt")}" />
                  </#if>
               </a>
            </#if>
            <div class="vrtx-title">
            <a class="vrtx-title" href="${articles.urls[r.URI]?html}">${title?html}</a></div>
            
            <#if publishedDate?has_content && articles.hasDisplayPropDef(publishedDate.definition.name)>    
              <div class="published-date">
                <span class="published-date-prefix"><@vrtx.localizeMessage code="viewCollectionListing.publishedDate" default="" args=[] locale=locale /></span>${publishedDate.getFormattedValue('long', locale)}                
              </div>
            <#elseif publishDate?has_content && articles.hasDisplayPropDef("published-date")>
              <div class="published-date">
                <span class="published-date-prefix"><@vrtx.localizeMessage code="viewCollectionListing.publishedDate" default="" args=[] locale=locale /></span>${publishDate}                
              </div>
            </#if>
            
            <#if hideNumberOfComments?exists && !hideNumberOfComments >
               <#local numberOfComments = vrtx.prop(r, "numberOfComments") />
               <#if numberOfComments?has_content >  
                 <div class="vrtx-number-of-comments-add-event-container">
                   <@viewutils.displayNumberOfComments r locale />
                 </div>
               </#if>
            </#if>
            <#if intro?has_content && articles.hasDisplayPropDef(intro.definition.name)>
              <div class="description introduction">${intro.value}</div>
            </#if>
            
            <#local hasBody = vrtx.propValue(r, 'hasBodyContent') == 'true' />
            <#if displayMoreURLs && hasBody>
            <div class="vrtx-read-more">
              <a href="${articles.urls[r.URI]?html}" class="more">  
                <@vrtx.localizeMessage code="viewCollectionListing.readMore" default="" args=[] locale=locale />
              </a>
            </div>
            </#if>
            
          </div>
          <#assign i = i + 1 />
        </#list>
      </#if>
    </#list>
    </div>
  </#if>

</#macro>