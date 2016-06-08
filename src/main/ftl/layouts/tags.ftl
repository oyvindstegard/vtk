<#ftl strip_whitespace=true output_format="HTML" auto_esc=true>
<#--
  - File: tags.ftl
  - 
  - Description: Simple rendering of a tags as a list. Feel free to improve.
  - 
  - Required model data:
  -     tagElements - List<vtk.web.view.decorating.components.TagCloudComponent.TagElement>
  - 
  -->

<#if tagElements?exists && tagElements?size &gt; 0>
  <div id="vrtx-tags" class="vrtx-tags-sets-${numberOfColumns}">
    <#assign i=1 >
    <ul class="vrtx-tags-${i}">
      <#assign counter=0 >
      <#assign i = i +1>
      <#list tagElements as element>
	    <#if counter == numberOfTagsInEachColumn>
		  </ul>
		  <ul class="vrtx-tags-${i}">
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
	      <a class="tags" href="${element.linkUrl}" rel="tags">${element.text}</a>
	      <#if showOccurence>
	        (${element.occurences})
	      </#if>
	    </li>
	    <#assign counter=counter + 1 >
      </#list>
    </ul>
  </div>
</#if>
