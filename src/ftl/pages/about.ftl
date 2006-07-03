<#ftl strip_whitespace=true>

<#--
  - File: about.ftl
  - 
  - Description: A HTML page that displays resource info
  - 
  - Required model data:
  -  
  - Optional model data:
  -
  -->
<#import "/spring.ftl" as spring />
<#import "/lib/vortikal.ftl" as vrtx />
<#import "/lib/propertyList.ftl" as propList />

<#if !aboutItems?exists>
  <#stop "This template only works with 'aboutItems' model map supplied." />
</#if>

<#macro propertyItemIfExists propertyName>
  <#if aboutItems[propertyName]?exists>
    <@propList.editOrDisplayPropertyItem aboutItems[propertyName] />
  </#if>
</#macro>

<#macro propertyEditURLIfExists propertyName>
  <#if aboutItems[propertyName]?exists>
    <@propList.propertyEditURL aboutItems[propertyName] />
  </#if>
</#macro>


<#function shouldDisplayForm propertyName>
  <#if aboutItems[propertyName]?exists && form?exists
       && form.definition?exists
       && form.definition = aboutItems[propertyName].definition>
    <#return true />
  </#if>
  <#return false />
</#function>

<#macro displayForm propertyName>
  <@propList.propertyForm aboutItems[propertyName] />
</#macro>


<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN" "http://www.w3.org/TR/html4/loose.dtd">
<html>
<head>
  <title>About</title>
</head>
<body>

<#assign resource = resourceContext.currentResource />
<#assign defaultHeader = vrtx.getMsg("resource.metadata.about", "About this resource") />

<#--
<#pre>
  <#list aboutItems?keys as key>
    ${key} = ${aboutItems[key]}
  </#list>
</pre>
 -->

  <div class="resourceInfoHeader">
    <h2>
      <@vrtx.msg
        code="resource.metadata.about.${resource.resourceType}"
        default="${defaultHeader}"/>
    </h2>
  </div>
  <p>Lorem ipsum dolere sit amet...</p>


  <h3 class="resourceInfoHeader">
    <@vrtx.msg
       code="resource.metadata.about.basic"
       default="Basic information"/>
  </h3>
  <table class="resourceInfo">
    <tr>
      <!-- Last modified -->
      <td class="key">
        <@vrtx.msg code="resource.lastModified" default="Last modified"/>:
      </td>
      <td>
        <#assign modifiedByStr = resource.modifiedBy.name />
        <#if resource.modifiedBy.URL?exists>
          <#assign modifiedByStr>
            <a href="${resource.modifiedBy.URL?html}">${resource.modifiedBy.name}</a>
          </#assign>
        </#if>
        <@vrtx.msg code = "resource.lastModifiedBy"
                   args = [ "${resource.lastModified?date}", "${modifiedByStr}" ]
                   default = "${resource.lastModified?date} by ${modifiedByStr}" />
      </td>
      <td>
        
      </td>
    <tr>
      <!-- Created -->
      <td class="key">
        <@vrtx.msg code="resource.creationTime" default="Created"/>:
      </td>
      <td>
        <#assign createdByStr = resource.createdBy.name />
        <#if resource.createdBy.URL?exists>
          <#assign createdByStr>
            <a href="${resource.createdBy.URL?html}">${resource.createdBy.name}</a>
          </#assign>
        </#if>
        <@vrtx.msg code = "resource.createdBy"
                   args = [ "${resource.creationTime?date}", "${createdByStr}" ]
                   default = "${resource.creationTime?date} by ${createdByStr}" />
      </td>
      <td>
        
      </td>
    </tr>
    </tr>
      <!-- Owner -->
      <@propertyItemIfExists propertyName = 'owner' />
    <tr>
      <!-- ResourceType -->
      <@propList.defaultPropertyDisplay
             key = vrtx.getMsg("resource.resourceType", "Resource type")
             value = vrtx.getMsg("resource.resourceType." + resource.resourceType, 
                                 resource.resourceType) />
    </tr>

    <tr>
      <!-- Web address -->
      <#assign url><a href="${resourceDetail.viewURL?html}">${resourceDetail.viewURL}</a></#assign>
      <@propList.defaultPropertyDisplay
             key = vrtx.getMsg("resource.viewURL", "Web address")
             value = url />
    </tr>

    <tr>
      <!-- WebDAV address -->
      <#assign url><a href="${resourceDetail.webdavURL?html}">${resourceDetail.webdavURL}</a></#assign>
      <@propList.defaultPropertyDisplay
             key = vrtx.getMsg("resource.webdavURL", "WebDAV address")
             value = url />
    </tr>

      <!-- Content language -->
      <@propertyItemIfExists propertyName = 'contentLocale' />

    <#if !resource.collection>
    <tr>
      <!-- Size -->
      <#assign size>
       <#if resourceContext.currentResource.contentLength?exists>
          <#if resourceContext.currentResource.contentLength <= 1000>
            ${resourceContext.currentResource.contentLength} B
          <#elseif resourceContext.currentResource.contentLength <= 1000000>
            ${(resourceContext.currentResource.contentLength / 1000)?string("0.#")} KB
          <#elseif resourceContext.currentResource.contentLength <= 1000000000>
            ${(resourceContext.currentResource.contentLength / 1000000)?string("0.#")} MB
          <#elseif resourceContext.currentResource.contentLength <= 1000000000000>
            ${(resourceContext.currentResource.contentLength / 1000000000)?string("0.#")} GB
          <#else>
            ${resourceContext.currentResource.contentLength} B
          </#if>
       <#else>
	 <@vrtx.msg code="resource.contentLength.unavailable" default="Not available" />
       </#if>
      </#assign>
      <@propList.defaultPropertyDisplay
             key = vrtx.getMsg("resource.contentLength", "Size")
             value = size />
    </tr>
    </#if>
  </table>


  <h3 class="resourceInfoHeader">
    <@vrtx.msg
       code="resource.metadata.about.content"
       default="Information describing the content"/>
  </h3>
  <table class="resourceInfo">
      <!-- Title -->
      <@propertyItemIfExists propertyName = 'collection.title' />
      <!-- Short Title -->
      <@propertyItemIfExists propertyName = 'shortTitle' />
      <!-- Keywords -->
      <@propertyItemIfExists propertyName = 'content:keywords' />
      <!-- Description -->
      <@propertyItemIfExists propertyName = 'content:description' />
      <!-- Verified date -->
      <@propertyItemIfExists propertyName = 'content:verifiedDate' />
      <!-- Author -->
      <@propertyItemIfExists propertyName = 'content:authorName' />
      <!-- Author e-mail -->
      <@propertyItemIfExists propertyName = 'content:authorEmail' />
      <!-- Author URL -->
      <@propertyItemIfExists propertyName = 'content:authorURL' />
    
  </table>

  <#if !resource.collection>
  <h3 class="resourceInfoHeader">
    <@vrtx.msg
       code="resource.metadata.about.technical"
       default="Technical details"/>
  </h3>
  <table class="resourceInfo">

      <!-- Content type -->
      <@propertyItemIfExists propertyName = 'contentType' />

    <tr>
      <!-- Character encoding -->
    <#if resource.characterEncoding?exists>
    <#if shouldDisplayForm('userSpecifiedCharacterEncoding')>
      <@displayForm propertyName = 'userSpecifiedCharacterEncoding' />
    <#else>
      <#assign encoding>
        <#if resource.userSpecifiedCharacterEncoding?has_content>
          ${resource.userSpecifiedCharacterEncoding}
        <#else>
          <@vrtx.msg code = "resource.characterEncoding.guessed"
                     args = [ "${resource.characterEncoding}" ]
                     default = "Guessed to be ${resource.characterEncoding}" />
        </#if>
      </#assign>
      <#assign editURL>
        <@propertyEditURLIfExists propertyName = 'userSpecifiedCharacterEncoding' />
      </#assign>
      <@propList.defaultPropertyDisplay
             key = vrtx.getMsg("resource.characterEncoding", "Character encoding")
             value = encoding
             editURL = editURL />

    </#if>
    </#if>
    </tr>
  </table>
  </#if>


</body>
</html>
