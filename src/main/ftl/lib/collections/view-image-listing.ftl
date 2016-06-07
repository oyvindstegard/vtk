<#ftl strip_whitespace=true output_format="HTML" auto_esc=true>
<#import "../vtk.ftl" as vrtx />
<#import "/lib/gallery.ftl" as gallery />

<#macro addScripts collection>
  <#local listingType = vrtx.propValue(collection, 'display-type', '', 'imgl')! />
  <#if displayTypeParam?has_content><#local listingType = displayTypeParam /></#if>
  
  <#--
    Default listing chosen, no particular listing type given.
    Set it to "list" to retrieve proper scripts
  -->
  <#if !listingType?has_content><#local listingType = "list" /></#if>

  <#if listingType?is_markup_output><#local listingType = listingType?markup_string /></#if>
  
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

<#macro displayImages imageListing collection>
  <#local listingType = vrtx.propValue(collection, 'display-type', '', 'imgl')! />
  <#if displayTypeParam?has_content><#local listingType = displayTypeParam /></#if>
  <#if listingType?is_markup_output><#local listingType = listingType?markup_string /></#if>

  <#if listingType == 'gallery'>
    <@displayGallery imageListing collection />
  <#elseif listingType == 'table'>
    <@displayTable imageListing collection />
  <#else>
    <@displayDefault imageListing collection />
  </#if>
</#macro>

<#macro displayDefault imageListing collection>
  <#local images = imageListing.entries />
  <#if (images?size > 0)>
    <div class="vrtx-image-listing-container">
      <ul class="vrtx-image-listing">
      <#assign count = 1 />
      <#list images as imageEntry>
        <#local image = imageEntry.propertySet />
        <#local title = vrtx.propValue(image, 'title') />
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
              <a href="${imageEntry.url}?vrtx=view-as-webpage">
                <#-- ${vrtx.relativeLinkConstructor(imageEntry.url, 'displayThumbnailService')} is no good! -->
                <img src="${imageEntry.url.protocolRelativeURL()}?vrtx=thumbnail" title="${title}" alt="" />
              </a>
            </div>

            <div class="vrtx-image-info">
              <div class="vrtx-image-title">
                <#if (title?markup_string?length > 20) >
                  <a href="${imageEntry.url}?vrtx=view-as-webpage">${title?markup_string?substring(0, 20)} ...</a>
                <#else>
                  <a href="${imageEntry.url}?vrtx=view-as-webpage">${title}</a>
                </#if>
              </div>
              
              <#local creationTime = vrtx.propValue(image, 'creationTime', 'short', '') />
              <div class="vrtx-image-creation-time">
                ${creationTime}
              </div>

              <#local description = vrtx.propValue(image, 'image-description')! />
              <div class="vrtx-image-description">
                <#if description?has_content>
                  <@vrtx.flattenHtml value=description escape=false />
                </#if>
              </div>

            </div>
        </li>
        <#assign count = count +1 />
      </#list>
      </ul>
    </div>
  </#if>
</#macro>

<#macro displayGallery imageListing collection>
  <#local images = imageListing.entries />
  <#if (images?size > 0)>
    <div class="vrtx-image-listing-include" id="vrtx-image-listing-include-${unique}">
      <#local activeImage = "" />
      <#if RequestParameters['actimg']?exists>
        <#local activeImage = RequestParameters['actimg'] />
      </#if>

      <@gallery.galleryJSInit 0 "4-3" />

      <ul class="vrtx-image-listing-include-thumbs-pure-css">
        <@gallery.galleryListImages images activeImage imageListing />
      </ul>
   </div>
 </#if>
</#macro>

<#macro displayTable imageListing collection>
  <#local images = imageListing.entries />
  <#if (images?size > 0)>
    <div class="vrtx-image-table">
      <table class="rowstyle-alt colstyle-alt no-arrow" border="1">
        <thead>
          <tr>
            <th scope="col" id="vrtx-table-image">${vrtx.getMsg("property.resourceType.image")}</th>
            <th scope="col" id="vrtx-table-title" class="sortable-text">${vrtx.getMsg("property.title")}</th>
            <th scope="col" id="vrtx-table-description" class="sortable-text">${vrtx.getMsg("imageListing.description")}</th>
            <th scope="col" id="vrtx-table-dimensions-width" class="sortable-numeric">${vrtx.getMsg("imageListing.width")}</th>
            <th scope="col" id="vrtx-table-dimensions-height" class="sortable-numeric">${vrtx.getMsg("imageListing.height")}</th>
            <th scope="col" id="vrtx-table-size" class="sortable-numeric">${vrtx.getMsg("property.contentLength")}</th>
            <th scope="col" id="vrtx-table-photo" class="sortable-text">${vrtx.getMsg("article.photoprefix")}</th>
            <th scope="col" id="vrtx-table-creation-time" class="sortable-sortEnglishLonghandDateFormat">${vrtx.getMsg("proptype.name.creationTime")}</th>
          </tr>
        </thead>
        <tbody>
        <#list images as imageEntry>
          <#local image = imageEntry.propertySet />
          <#assign url = imageEntry.url />
          <tr>
            <#local title = vrtx.propValue(image, 'title') />
            <#-- ${vrtx.relativeLinkConstructor(url, 'displayThumbnailService')} is no good! -->
            <td class="vrtx-table-image"><a href="${url}?vrtx=view-as-webpage"><img src="${url.protocolRelativeURL()}?vrtx=thumbnail" alt="" /></a></td>
            <td class="vrtx-table-title"><a href="${url}?vrtx=view-as-webpage">${title}</a></td>
            <#local description = vrtx.propValue(image, 'image-description') />
            <td class="vrtx-table-description">
              <#if description?has_content>
                <@vrtx.flattenHtml value=description escape=false />
              </#if>
            </td>
            <#local width = vrtx.propValue(image, 'pixelWidth') />
            <#local height = vrtx.propValue(image, 'pixelHeight') />
            <td class="vrtx-table-dimensions-width">${width} px</td>
            <td class="vrtx-table-dimensions-height">${height} px</td>
            <#local contentLength = vrtx.propValue(image, 'contentLength') />
            <td class="vrtx-table-size"><@vrtx.calculateResourceSizeToKB contentLength?number /></td>
            <#local photographer = vrtx.propValue(image, "photographer") />
            <td class="vrtx-table-photo">${photographer}</td>
            <#local creationTime = vrtx.propValue(image, 'creationTime', 'short', '') />
            <td class="vrtx-table-creation-time">${creationTime}</td>
          </tr>
        </#list>
        </tbody>
      </table>
    </div>
  </#if>
</#macro>
