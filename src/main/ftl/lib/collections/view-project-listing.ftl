<#ftl strip_whitespace=true output_format="HTML" auto_esc=true>
<#import "../vtk.ftl" as vrtx />

<#macro displayProjectsAlphabetical projectListing>
  <#list alpthabeticalOrdredResult?keys as key >
	<ul class="vrtx-alphabetical-project-listing">
	  <li>${key}
	    <ul>
		  <#list alpthabeticalOrdredResult[key] as project>
			  <#local title = vrtx.propValue(project.propertySet, 'title') />
			  <li><a href="${project.url}">${title}</a></li>
		  </#list>
		</ul>
	  </li>
	</ul>
  </#list>
</#macro>

<#macro projectListingViewServiceURL >
  <#if viewAllProjectsLink?exists || viewOngoingProjectsLink?exists>
	<div id="vrtx-listing-completed-ongoing">
	  <#if viewAllProjectsLink?exists && displayAlternateLink?exists>
	  	<a href="${viewAllProjectsLink}">${vrtx.getMsg("projects.viewCompletedProjects")}</a>
	  </#if>
	  <#if viewOngoingProjectsLink?exists>
	    <a href="${viewOngoingProjectsLink}">${vrtx.getMsg("projects.viewOngoingProjects")}</a>
	  </#if>
    </div>
  </#if>
</#macro>

<#macro displayProjects projectListing>

  <#local projects = projectListing.entries />

  <#if (projects?size > 0) >
    <div id="${projectListing.name}" class="vrtx-resources vrtx-projects ${projectListing.name}">
    <#if projectListing.title?exists && projectListing.offset == 0>
      <h2>${projectListing.title}</h2>
    </#if>
    <#local locale = springMacroRequestContext.getLocale() />

    <#list projects as projectEntry>

      <#local project = projectEntry.propertySet />
      <#local title = vrtx.propValue(project, 'title')! />
      <#local introImg = vrtx.prop(project, 'picture')!  />
      <#local intro = vrtx.prop(project, 'introduction')!  />
      <#local caption = vrtx.propValue(project, 'caption')!  />

      <div class="vrtx-resource vrtx-project">
        <#if introImg?has_content>
          <#local introImgURI = vrtx.propValue(project, 'picture')! />
          <#if introImgURI?has_content>
          <#local thumbnail =  vrtx.relativeLinkConstructor(introImgURI, 'displayThumbnailService') />
    	  <#else>
    	    <#local thumbnail = "" />
   	  </#if>
   	  <#local introImgAlt = vrtx.propValue(project, 'pictureAlt')! />
          <a class="vrtx-image" href="${projectEntry.url}">
            <img src="${thumbnail}" alt="<#if introImgAlt?has_content>${introImgAlt}</#if>" />
          </a>
          </#if>
          <div class="vrtx-title">
            <a class="vrtx-title summary" href="${projectEntry.url}">${title}</a>
	  </div>
          <#if intro?has_content && projectListing.hasDisplayPropDef("hide-introduction")>
            <div class="description introduction"><@vrtx.linkResolveFilter intro.value projectEntry.url requestURL /></div>
          </#if>
          <div class="vrtx-read-more">
            <a href="${projectEntry.url}" class="more">
              <@vrtx.localizeMessage code="viewCollectionListing.readMore" default="" args=[] locale=locale />
            </a>
          </div>
      </div>
    </#list>
   </div>
  </#if>
</#macro>

<#macro completed >
	<#if viewOngoingProjectsLink?exists>
		<span>(${vrtx.getMsg("projects.completed")})</span>
	</#if>
</#macro>
