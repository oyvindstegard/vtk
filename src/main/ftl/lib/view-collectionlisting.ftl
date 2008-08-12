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


<#-- XXX: remove this when properties 'introduction' and 'description'
     are merged: -->
<#function getIntroduction resource>
  <#local introduction = vrtx.propValue(resource, "introduction") />
  <#if !introduction?has_content>
    <#local introduction = vrtx.propValue(resource, "description", "", "content") />
  </#if>
  <#return introduction />
</#function>



<#macro displayResources model>

  <#local collectionListing = .vars[model] />
  <#local resources=collectionListing.files />
  <#if resources?size &gt; 0>
    <div class="vrtx-resources ${model}">
    <#if collectionListing.title?exists>
      <h2>${collectionListing.title?html}</h2>
    </#if>

    <#list resources as r>
      <div class="vrtx-resource">

        <a class="vrtx-title" href="${collectionListing.urls[r.URI]?html}">${vrtx.propValue(r, "title", "", "")}</a>

        <#list collectionListing.displayPropDefs as displayPropDef>

          <#if displayPropDef.name = 'introduction'>
            <#assign val = getIntroduction(r) />
          <#elseif displayPropDef.type = 'IMAGE_REF'>
            <#assign val><img src="${vrtx.propValue(r, displayPropDef.name, "")}" /></#assign>
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
  
  <#if collectionListing.prevURL?exists>
    <a href="${collectionListing.prevURL?html}">previous</a>&nbsp;
  </#if>
  <#if collectionListing.nextURL?exists>
    <a href="${collectionListing.nextURL?html}">next</a>
  </#if>
</#macro>


<#macro displayArticles model displayMoreURLs=false>

  <#local collectionListing = .vars[model] />
  <#local resources=collectionListing.files />
  <#if resources?size &gt; 0>
    <div class="vrtx-resources ${model}">
    <#if collectionListing.title?exists>
      <h2>${collectionListing.title?html}</h2>
    </#if>

    <#local locale = springMacroRequestContext.getLocale() />
    <#list resources as r>
      <#local title = vrtx.propValue(r, 'title') />
      <#local introImg  = vrtx.prop(r, 'picture')  />
      <#local publishedDate  = vrtx.prop(r, 'published-date')  />
      <#local intro  = vrtx.prop(r, 'introduction')  />

      <div class="vrtx-resource">
        <a class="vrtx-title" href="${collectionListing.urls[r.URI]?html}">
        <#if introImg?has_content && collectionListing.displayPropDefs?seq_contains(introImg.definition)>
        <img src="${introImg.value?html}" alt="${vrtx.getMsg("article.introductionImageAlt")}" />
        </#if>
        ${title}</a> 

        <#if publishedDate?has_content && collectionListing.displayPropDefs?seq_contains(publishedDate.definition)> 
        <div class="published-date">${publishedDate.getFormattedValue('long', locale)}</div>
	</#if>

        <#if intro?has_content && collectionListing.displayPropDefs?seq_contains(intro.definition)>
        <div class="description introduction">${intro.value}</div>
        </#if>

        <#-- list collectionListing.displayPropDefs as displayPropDef>

          <#if displayPropDef.name = 'introduction'>
            <#assign val = getIntroduction(r) />
          <#elseif displayPropDef.type = 'IMAGE_REF'>
            <#assign val><img src="${vrtx.propValue(r, displayPropDef.name, "")}" /></#assign>
          <#else>
            <#assign val = vrtx.propValue(r, displayPropDef.name, "long") /> 
          </#if>

          <#if val?has_content>
            <div class="vrtx-prop ${displayPropDef.name}">
              ${val}
            </div>
          </#if>
        </#list -->

        <#if displayMoreURLs>
          <a href="${collectionListing.urls[r.URI]?html}" class="more">
            <@vrtx.msg code="viewCollectionListing.readMore" />
          </a>
        </#if>
      </div>
    </#list>
   </div>
  </#if>

  <#if collectionListing.prevURL?exists>
    <a class="vrtx-previous" href="${collectionListing.prevURL?html}">previous</a>
  </#if>
  <#if collectionListing.nextURL?exists>
    <a class="vrtx-next" href="${collectionListing.nextURL?html}">next</a>
  </#if>
</#macro>



<#macro displayEvents model displayMoreURLs=false>
  <#local collectionListing = .vars[model] />
  <#local resources=collectionListing.files />
  <#if resources?size &gt; 0>
    <div class="vrtx-resources ${model}">
    <#if collectionListing.title?exists>
      <h2>${collectionListing.title?html}</h2>
    </#if>
    <#local locale = springMacroRequestContext.getLocale() />
    <#list resources as r>
      <#local title = vrtx.propValue(r, 'title') />
      <#local introImg  = vrtx.prop(r, 'picture')  />
      <#local intro  = vrtx.prop(r, 'introduction')  />
      <#local startDate  = vrtx.prop(r, 'start-date')  />
      <#local endDate  = vrtx.prop(r, 'end-date')  />
      <#local location  = vrtx.prop(r, 'location')  />
      <div class="vrtx-resource vevent">
        <a class="vrtx-title summary" href="${collectionListing.urls[r.URI]?html}">
        <#if introImg?has_content && collectionListing.displayPropDefs?seq_contains(introImg.definition)>
        <img src="${introImg.value?html}" alt="${vrtx.getMsg("article.introductionImageAlt")}" />
        </#if>
        ${title}</a>

        <div class="time-and-place"> 
        <abbr class="dtstart" title="${startDate.getFormattedValue('iso-8601', locale)}">${startDate.getFormattedValue('short', locale)}</abbr>
        <#if endDate?has_content && collectionListing.displayPropDefs?seq_contains(endDate.definition)>
        <span class="delimiter"> - </span>
        <abbr class="dtend" title="${endDate.getFormattedValue('iso-8601', locale)}">${endDate.getFormattedValue('short', locale)}</abbr>
        </#if>
        <#if location?has_content && collectionListing.displayPropDefs?seq_contains(location.definition)>
        <span class="location">${location.value}</span>
        </#if>
        </div>

        <#if intro?has_content && collectionListing.displayPropDefs?seq_contains(intro.definition)>
        <div class="description introduction">${intro.value}</div>
        </#if>

        <#if displayMoreURLs>
          <a href="${collectionListing.urls[r.URI]?html}" class="more" title="${title?html}">
            <@vrtx.msg code="viewCollectionListing.readMore" />
          </a>
        </#if>

      </div>
    </#list>
   </div>
  </#if>

  <#if collectionListing.prevURL?exists>
    <a class="vrtx-previous" href="${collectionListing.prevURL?html}">previous</a>
  </#if>
  <#if collectionListing.nextURL?exists>
    <a class="vrtx-next" href="${collectionListing.nextURL?html}">next</a>
  </#if>

</#macro>

