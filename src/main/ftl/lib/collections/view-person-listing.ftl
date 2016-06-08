<#ftl strip_whitespace=true output_format="HTML" auto_esc=true>
<#import "../vtk.ftl" as vrtx />
<#-- XXX: should reuse similar FTL in person-list-util.ftl in vortex-project -->
<#macro displayPersons personListing title="">

  <#local persons = personListing.entries />

  <#if (persons?size > 0)>
    <div class="vrtx-person-search-hits">
      <#-- Removed until HTML5: summary="${vrtx.getMsg("person-listing.overview-of")} ${title}" -->
      <table class="vrtx-person-listing">
        <#if numberOfRecords?exists>
          <caption>
            ${vrtx.getMsg("person-listing.persons")} ${numberOfRecords["elementsOnPreviousPages"]} -
            ${numberOfRecords["elementsIncludingThisPage"]} ${vrtx.getMsg("person-listing.of")} 
            ${personListing.totalHits?string}
          </caption>
        </#if>
        <thead>
          <tr>
            <th scope="col" class="vrtx-person-listing-name">${vrtx.getMsg("person-listing.name")}</th>
            <th scope="col" class="vrtx-person-listing-phone">${vrtx.getMsg("person-listing.phone")}</th>
            <th scope="col" class="vrtx-person-listing-email">${vrtx.getMsg("person-listing.email")}</th>
            <th scope="col" class="vrtx-person-listing-tags">${vrtx.getMsg("person-listing.tags")}</th>
          </tr>
        </thead>
        <tbody>
      <#local personNr = 1 />
      <#list persons as personEntry>
        <#local person = personEntry.propertySet />
        <#local firstName = vrtx.propValue(person, 'firstName')! />
        <#local surname = vrtx.propValue(person, 'surname')! />
        <#local title = vrtx.propValue(person, 'title')! />
        <#local picture = vrtx.propValue(person, 'picture')!  />
        <#local position = vrtx.propValue(person, 'position')!  />
        <#local phonenumbers = vrtx.propValue(person, 'phone')!  />
        <#local alternativephonenumber = vrtx.propValue(person, 'alternativeCellPhone')!  />
        <#local mobilenumbers = vrtx.propValue(person, 'mobile')!  />
        <#local emails = vrtx.propValue(person, 'email')!  />
        <#local tags = vrtx.propValue(person, 'tags')! />
      
        <#local src = vrtx.propValue(person, 'picture', 'thumbnail')! />
      
        <#local introImgURI = vrtx.propValue(person, 'picture')! />
        <#if introImgURI?has_content>
    	  <#local thumbnail =  vrtx.relativeLinkConstructor(introImgURI, 'displayThumbnailService') />
        <#else>
          <#local thumbnail = "" />
   	</#if>
      
        <#local imageAlt = vrtx.getMsg("person-listing.image-alt") >
        <#local imageAlt = imageAlt + " " + firstName + " " + surname />
	<tr class="vrtx-person-${personNr}">
          <td class="vrtx-person-listing-name">
            <#if src?has_content>
              <a class="vrtx-image" href="${personEntry.url}"><img src="${thumbnail}" alt="${imageAlt}" /></a>
            </#if>
            <#if surname?has_content >
              <a href="${personEntry.url}">${surname}<#if firstName?has_content && surname?has_content>, </#if>${firstName}</a>
            <#else>
              <a href="${personEntry.url}">${title}</a>
            </#if>
            <span>${position}</span>
          </td>
          <td class="vrtx-person-listing-phone">
            <#if phonenumbers?has_content>
              <#list phonenumbers?split(",") as phone>
                <span>${phone}</span>
              </#list>
            </#if>
            <#if mobilenumbers?has_content>
              <#list mobilenumbers?split(",") as mobile>
                <span>${mobile}</span>
              </#list>
            </#if>
            <#if alternativephonenumber?has_content>
              <span>${alternativephonenumber}</span>
            </#if>
          </td>
          <td class="vrtx-person-listing-email">
            <#if emails?has_content>
              <#list emails?split(",") as email>
                <#if (email?string?length > 25) >
                  <#if email?string?contains('@') >
                    <#assign eS = email?string?split('@') />
                    <a href="mailto:${email}"><span>${eS[0]}</span><span>@${eS[1]}</span></a>
                  <#else>
                    <a href="mailto:${email}"><span>${email?string?substring(0, 25)}</span><span>${email?string?substring(25,email?string?length)}</span></a>
                  </#if>
                <#else>
                  <a href="mailto:${email}">${email}</a>
                </#if>
              </#list>
            </#if>
          </td>
          <td class="vrtx-person-listing-tags">
            <#local tagsList = tags?split(",")>
            <#local tagsNr = 0 />
            <#if tagsList?has_content>
              <#list tagsList as tag>
                <#local tagUrl = "?vrtx=tags&tag=" + tag?trim + "&resource-type=" + person.getResourceType() />
                <#local sortingParams = personListing.getRequestSortOrderParams() />
                <#if sortingParams?has_content>
                  <#local tagUrl = tagUrl + "&" + sortingParams />
                </#if>
                <#local tagsNr = tagsNr+1 />
                <a href="${tagUrl}">${tag?trim}</a><#if tagsList?size != tagsNr>,</#if>
              </#list>
            </#if>
          </td>
        </tr>
        <#local personNr = personNr+1 />
      </#list>
        </tbody>
      </table>
    </div>
  </#if>
</#macro>
