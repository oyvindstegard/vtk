<#ftl strip_whitespace=true>

<#--
  - File: view-collectionlisting.ftl
  - 
  - Description: A HTML page that displays a collection listing.
  - 
  - Required model data:
  -   resourceContext
  -   collectionListing:
  -     collections
  -     files
  -     urls
  -     sortURLs
  -     sortProperty
  -  
  - Optional model data:
  -
  -->


<#import "/lib/vortikal.ftl" as vrtx />
<#import "/lib/view-collectionlisting.ftl" as coll />
<#import "/lib/dump.ftl" as dumper>
<#import "/lib/view-utils.ftl" as viewutils />

<#assign resource = collection />

<#assign title = vrtx.propValue(resource, "title", "flattened") />
<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<html xmlns="http://www.w3.org/1999/xhtml">
<head>
  <#if cssURLs?exists>
    <#list cssURLs as cssURL>
      <link rel="stylesheet" href="${cssURL}" />
    </#list>
  </#if>
  <#list alternativeRepresentations as alt>
    <link rel="alternate" type="${alt.contentType?html}" title="${alt.title?html}" href="${alt.url?html}" />
  </#list>
  
  <title>${title?html}
    <#if page?has_content>
      <#if "${page}" != "1"> - <@vrtx.msg code="viewCollectionListing.page" /> ${page}</#if>
    </#if>
  </title>
  
</head>
<body>
  
  <#assign page = page?default(1) />

  <h1>${title}
    <#if page?has_content>
      <#if "${page}" != "1"> - <@vrtx.msg code="viewCollectionListing.page" /> ${page}</#if>
    </#if>
  </h1> 

     <#if page == 1>
     <#-- Image -->
     <@viewutils.displayImage resource />

     <#-- Introduction -->
     <#-- @viewutils.displayIntroduction resource / -->
     <#assign introduction = coll.getIntroduction(resource) />
     <#if introduction?has_content>
       <div class="vrtx-introduction">
         ${introduction}
       </div>
     </#if>
     </#if>

     <#-- List collections: -->
     <#if page == 1>
     
     <#if subCollections?size &gt; 0>
       <#if subCollections?size &gt; 15>
          <#assign splitList = ((subCollections?size/4)+0.75)?int />
          <#assign interval = splitList />
       <#elseif subCollections?size &gt; 8>
          <#assign splitList = ((subCollections?size/3)+0.5)?int />
          <#assign interval = splitList />
       <#elseif subCollections?size &gt; 3>
          <#assign splitList = ((subCollections?size/2)+0.5)?int />
          <#assign interval = splitList />
       <#else>
         <#assign splitList = -1 />
       </#if>


       <div id="vrtx-collections" class="vrtx-collections">
         <h2><@vrtx.msg code="viewCollectionListing.subareas" default="Subareas"/></h2>
         <table>
           <tr>
             <td> 
               <ul>
                 <#list subCollections as c>
                   <#if c_index = splitList>
                 </ul></td>
                 <td><ul>
                     <#assign splitList = splitList + interval />
                   </#if>
                   <li><a href="${c.getURI()?html}">${vrtx.propValue(c, "title")?html}</a></li>
                 </#list>                                                                                          
           </ul></td></tr>
         </table>
       </div>

     </#if>
     </#if>


     <#-- List resources: -->

     <#if collection.resourceType = 'article-listing'>
       <@coll.displayArticles page=page collectionListings=searchComponents hideNumberOfComments=hideNumberOfComments displayMoreURLs=true />
     <#else>
       <#list searchComponents as searchComponent>
         <#if collection.resourceType = 'event-listing'>
           <@coll.displayEvents collectionListing=searchComponent hideNumberOfComments=hideNumberOfComments displayMoreURLs=true />
         <#else>
           <@coll.displayResources collectionListing=searchComponent />
         </#if>
       </#list>
     </#if>

     <#-- Previous/next URLs: -->

     <#if prevURL?exists>
       <a class="vrtx-previous" href="${prevURL?html}"><@vrtx.msg code="viewCollectionListing.previous" /></a>
     </#if>
     <#if nextURL?exists>
       <a class="vrtx-next" href="${nextURL?html}"><@vrtx.msg code="viewCollectionListing.next" /></a>
     </#if>

    <#-- XXX: display first link with content type = atom: -->
    <#list alternativeRepresentations as alt>
      <#if alt.contentType = 'application/atom+xml'>
        <div class="vrtx-feed-link">
          <a id="vrtx-feed-link" href="${alt.url?html}"><@vrtx.msg code="viewCollectionListing.feed.fromThis" /></a>
        </div>
        <#break />
      </#if>
    </#list>
  </body>
</html>
