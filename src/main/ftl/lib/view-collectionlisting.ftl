<#--
  - File: view-collectionlisting.ftl
  - 
  - Description: 
  - 
  - Required model data:
  -  
  - Optional model data:
  -   
  -->

<#import "vortikal.ftl" as vrtx />
<#import "view-utils.ftl" as viewutils />


<#-- XXX: remove this when properties 'introduction' and 'description'
     are merged: -->
<#function getIntroduction resource>
  <#local introduction = vrtx.propValue(resource, "introduction") />
  <#if !introduction?has_content>
    <#local introduction = vrtx.propValue(resource, "description", "", "content") />
  </#if>
  <#return introduction />
</#function>

<#macro displayResources collectionListing>

  <#local resources=collectionListing.files />
  <#if resources?size &gt; 0>
    <div id="${collectionListing.name}" class="vrtx-resources ${collectionListing.name}">
    <#if collectionListing.title?exists && collectionListing.offset == 0>
      <h2>${collectionListing.title?html}</h2>
    </#if>
    
    <#list resources as r>
      <div class="vrtx-resource">

        <a class="vrtx-title" href="${collectionListing.urls[r.URI]?html}">${vrtx.propValue(r, "title", "", "")?html}</a>

        <#list collectionListing.displayPropDefs as displayPropDef>

          <#if displayPropDef.name = 'introduction'>
            <#assign val = getIntroduction(r) />
          <#elseif displayPropDef.type = 'IMAGE_REF'>
            <#assign val><img src="${vrtx.propValue(r, displayPropDef.name, "")}" /></#assign>
          <#elseif displayPropDef.name = 'lastModified'>
            <#assign val>
              <@vrtx.msg code="viewCollectionListing.lastModified"
                         args=[vrtx.propValue(r, displayPropDef.name, "long")] />
            </#assign>
          <#else>
            <#assign val = vrtx.propValue(r, displayPropDef.name, "long") /> <#-- Default to 'long' format -->
          </#if>

          <#if val?has_content>
            <div class="${displayPropDef.name}">
              ${val}
            </div>
          </#if>
        </#list>

      </div>
    </#list>
   </div>
  </#if>
  
</#macro>

<#macro displayArticles page collectionListings displayMoreURLs=false>

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
        <#local locale = springMacroRequestContext.getLocale() />
        <#list resources as r>
        
          <#local title = vrtx.propValue(r, 'title') />
          <#local introImg  = vrtx.prop(r, 'picture')  />
          <#local publishedDate  = vrtx.prop(r, 'published-date')  />
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
            <a class="vrtx-title" href="${articles.urls[r.URI]?html}">
            <#if introImg?has_content && articles.displayPropDefs?seq_contains(introImg.definition)>
              <#local src = vrtx.propValue(r, 'picture', 'thumbnail') />
              <#if caption != ''>
                <img src="${src?html}" alt="${captionFlattened}" />
              <#else>
                <img src="${src?html}" alt="${vrtx.getMsg("article.introductionImageAlt")}" />
              </#if>
            </#if>
            ${title?html}</a>
            
            <#if publishedDate?has_content && articles.displayPropDefs?seq_contains(publishedDate.definition)> 
              <div class="published-date">
                <@vrtx.msg code="viewCollectionListing.publishedDate" args=[publishedDate.getFormattedValue('long', locale)] />
              </div>
            </#if>

            <#if intro?has_content && articles.displayPropDefs?seq_contains(intro.definition)>
              <div class="description introduction">${intro.value}</div>
            </#if>

            <#local hasBody = vrtx.propValue(r, 'hasBodyContent') == 'true' />
            <#if displayMoreURLs && hasBody>
              <a href="${articles.urls[r.URI]?html}" class="more">
                <@vrtx.msg code="viewCollectionListing.readMore" />
              </a>
            </#if>
          </div>
          <#assign i = i + 1 />
        </#list>
        
      </#if>
    </#list>
    </div>

  </#if>

</#macro>

<#macro displayEvents collectionListing displayMoreURLs=false>
  <#local resources=collectionListing.files />
  <#if resources?size &gt; 0>
    <div id="${collectionListing.name}" class="vrtx-resources ${collectionListing.name}">
    <#if collectionListing.title?exists && collectionListing.offset == 0>
      <h2>${collectionListing.title?html}</h2> 
    </#if>
    <#local locale = springMacroRequestContext.getLocale() />
    <#list resources as r>
      <#local title = vrtx.propValue(r, 'title') />
      <#local introImg  = vrtx.prop(r, 'picture')  />
      <#local intro  = vrtx.prop(r, 'introduction')  />
      <#local location  = vrtx.prop(r, 'location')  />
      <#local caption = vrtx.propValue(r, 'caption')  />
      
      <#-- Flattened caption for alt-tag in image -->
     <#local captionFlattened>
        <@vrtx.flattenHtml value=caption escape=true />
      </#local>

      <div class="vrtx-resource vevent">
        
        <a class="vrtx-title summary" href="${collectionListing.urls[r.URI]?html}">
        <#if introImg?has_content && collectionListing.displayPropDefs?seq_contains(introImg.definition)>
          <#local src = vrtx.propValue(r, 'picture', 'thumbnail') />
          <#if caption != ''>
            <img src="${src?html}" alt="${captionFlattened}" />
           <#else>
             <img src="${src?html}" alt="${vrtx.getMsg("article.introductionImageAlt")}" />
           </#if>
        </#if>
        ${title?html}</a>

        <div class="time-and-place"> 
          <@viewutils.displayTimeAndPlace r title/>
        </div>

        <#if intro?has_content && collectionListing.displayPropDefs?seq_contains(intro.definition)>
        <div class="description introduction">${intro.value}</div>
        </#if>

        <#local hasBody = vrtx.propValue(r, 'hasBodyContent') == 'true' />

        <#if displayMoreURLs && hasBody>
          <a href="${collectionListing.urls[r.URI]?html}" class="more" title="${title?html}">
            <@vrtx.msg code="viewCollectionListing.readMore" />
          </a>
        </#if>

      </div>
    </#list>
   </div>
  </#if>

</#macro>
