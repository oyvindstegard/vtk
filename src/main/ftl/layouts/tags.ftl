<#ftl strip_whitespace=true>
<#--
  - File: tags.ftl
  - 
  - Description: Simple rendering of a tags as a list. Feel free to improve.
  - 
  - Required model data:
  -     tagElements - List<org.vortikal.web.view.decorating.components.TagCloudComponent.TagElement>
  - 
  -->

<#if tagElements?exists && tagElements?size &gt; 0>
  <div id="vrtx-tags">
    <#assign i=1 >
    <ul class="vrtx-tags-${i?html}">
      <#assign counter=0 >
      <#assign i = i +1>
      <#list tagElements as element>    
	    <#if counter == numberOfTagsInEachColumn>
		  </ul>
		  <ul class="vrtx-tags-${i?html}">
		    <#if completeColumn == 0 >
		      <#assign numberOfTagsInEachColumn = numberOfTagsInEachColumn - 1>
		    </#if>
		    <#assign counter=0 >
		    <#assign i = i +1>
		    <#if completeColumn &gt; -1 >
		      <#assign completeColumn = completeColumn - 1>
		    </#if>
	    </#if>
	    <li class="vrtx-tags-element">
	      <a class="tags" href="${element.linkUrl?html}" rel="tags">${element.text?html}</a>
	      <#if showOccurence>
	        (${element.occurences?html})
	      </#if>
	    </li>
	    <#assign counter=counter + 1 >
      </#list>
    </ul>
  </div>
</#if>