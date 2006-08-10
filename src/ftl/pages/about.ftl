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

<#function shouldDisplayForm propertyName>
  <#if aboutItems[propertyName]?exists && form?exists
       && form.definition?exists
       && form.definition = aboutItems[propertyName].definition>
    <#return true />
  </#if>
  <#return false />
</#function>

<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN" "http://www.w3.org/TR/html4/loose.dtd">
<html>
<head>
  <title>About</title>
</head>
<body>

<#assign resource = resourceContext.currentResource />
<#assign defaultHeader = vrtx.getMsg("resource.metadata.about", "About this resource") />

  <div class="resourceInfo">
  <h2>
    <@vrtx.msg
       code="resource.metadata.about.${resource.resourceType}"
       default="${defaultHeader}"/>
  </h2>



  <table class="resourceInfo">

      <!-- Last modified -->
      <#assign modifiedByStr = resource.modifiedBy.name />
      <#if resource.modifiedBy.URL?exists>
        <#assign modifiedByStr>
          <a href="${resource.modifiedBy.URL?html}">${resource.modifiedBy.name}</a>
        </#assign>
      </#if>
      <#assign modifiedStr>
        <@vrtx.rawMsg code = "property.lastModifiedBy"
                   args = [ "${resource.lastModified?datetime?string.long}", "${modifiedByStr}" ]
                   default = "${resource.lastModified?date} by ${modifiedByStr}" />
      </#assign>

      <@propList.defaultPropertyDisplay
             name = vrtx.getMsg("property.lastModified", "Last modified")
             value = modifiedStr />

      <!-- Created -->
      <#assign createdByStr = resource.createdBy.name />
      <#if resource.createdBy.URL?exists>
        <#assign createdByStr>
          <a href="${resource.createdBy.URL?html}">${resource.createdBy.name}</a>
        </#assign>
      </#if>
      <#assign createdByStr>
        <@vrtx.rawMsg code = "property.createdBy"
                   args = [ "${resource.creationTime?datetime?string.long}", "${createdByStr}" ]
                   default = "${resource.creationTime?date} by ${createdByStr}" />
      </#assign>
      <@propList.defaultPropertyDisplay
             name = vrtx.getMsg("property.creationTime", "Created")
             value = createdByStr />


      <!-- Owner -->
      <@propList.editOrDisplayProperty modelName='aboutItems' propertyName = 'owner' />

      <!-- ResourceType -->
      <@propList.defaultPropertyDisplay
             name = vrtx.getMsg("property.resourceType", "Resource type")
             value = vrtx.getMsg("property.resourceType." + resource.resourceType, 
                                 resource.resourceType) />

      <!-- Web address -->
      <#assign url><a href="${resourceDetail.viewURL?html}">${resourceDetail.viewURL}</a></#assign>
      <@propList.defaultPropertyDisplay
             name = vrtx.getMsg("resource.viewURL", "Web address")
             value = url />

      <!-- WebDAV address -->
      <#assign url><a href="${resourceDetail.webdavURL?html}">${resourceDetail.webdavURL}</a></#assign>
      <@propList.defaultPropertyDisplay
             name = vrtx.getMsg("resource.webdavURL", "WebDAV address")
             value = url />

      <!-- Content language -->
      <@propList.editOrDisplayProperty modelName='aboutItems' propertyName = 'contentLocale' />

    <#if !resource.collection>
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
	 <@vrtx.msg code="property.contentLength.unavailable" default="Not available" />
       </#if>
      </#assign>
      <@propList.defaultPropertyDisplay
             name = vrtx.getMsg("property.contentLength", "Size")
             value = size />
    </#if>
  </table>


  <h3 class="resourceInfoHeader">
    <@vrtx.msg
       code="resource.metadata.about.content"
       default="Information describing the content"/>
  </h3>

  <#-- @propList.propertyList
       modelName = "aboutItems"
       itemNames =  [ 'title', 'skipInPath', 'content:keywords',
                      'content:description', 'content:verifiedDate',
                      'content:authorName', 'content:authorEmail', 
                      'content:authorURL' ] / -->

  <table class="resourceInfo">
    <!-- title -->
    <@propList.editOrDisplayProperty modelName='aboutItems' propertyName = 'title' />

    <!-- skipInPath -->
    <@propList.editOrDisplayProperty modelName='aboutItems' propertyName = 'skipInPath' />

    <!-- content:keywords -->
    <@propList.editOrDisplayProperty modelName='aboutItems' propertyName = 'content:keywords' inputSize=40 />

    <!-- content:description -->
    <@propList.editOrDisplayProperty modelName='aboutItems' propertyName = 'content:description' inputSize=100 />

    <!-- content:verifiedDate -->
    <@propList.editOrDisplayProperty modelName='aboutItems' propertyName = 'content:verifiedDate' />

    <!-- content:authorName -->
    <@propList.editOrDisplayProperty modelName='aboutItems' propertyName = 'content:authorName' inputSize=40 />

    <!-- content:authorEmail -->
    <@propList.editOrDisplayProperty modelName='aboutItems' propertyName = 'content:authorEmail' inputSize=40 />

    <!-- content:authorURL -->
    <@propList.editOrDisplayProperty modelName='aboutItems' propertyName = 'content:authorURL' inputSize=40 />

  </table>


  <#if !resource.collection>
  <h3 class="resourceInfoHeader">
    <@vrtx.msg
       code="resource.metadata.about.technical"
       default="Technical details"/>
  </h3>
  <table class="resourceInfo">

    <!-- Content type -->
    <@propList.editOrDisplayProperty modelName='aboutItems' propertyName = 'contentType' />

    <!-- Character encoding -->
    <#if resource.characterEncoding?exists>
    <#if shouldDisplayForm('userSpecifiedCharacterEncoding')>
      <@propList.propertyForm
         item=aboutItems['userSpecifiedCharacterEncoding']
         formValue=resource.characterEncoding />
    <#else>
      <#assign encoding>
        <#if resource.userSpecifiedCharacterEncoding?has_content>
          ${resource.userSpecifiedCharacterEncoding}
        <#else>
          <@vrtx.msg code = "property.characterEncoding.guessed"
                     args = [ "${resource.characterEncoding}" ]
                     default = "Guessed to be ${resource.characterEncoding}" />
        </#if>
      </#assign>
      <#assign editURL>
        <@propList.propertyEditURL modelName='aboutItems'
                                   propertyName='userSpecifiedCharacterEncoding' />
      </#assign>
      <@propList.defaultPropertyDisplay
             name = vrtx.getMsg("property.characterEncoding", "Character encoding")
             value = encoding
             editURL = editURL />

    </#if>
    </#if>

    <!-- Plaintext Edit on managed xml -->
    <@propList.editOrDisplayProperty modelName='aboutItems' propertyName = 'custom:plaintext-edit' />

  </table>
  </#if>
  </div>

</body>
</html>
