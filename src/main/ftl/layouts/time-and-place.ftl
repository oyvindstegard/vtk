<#ftl strip_whitespace=true output_format="HTML" auto_esc=true>
<#--
  - File: time-and-place.ftl
  - 
  - Description: Event time and place
  - 
  - Required model data:
  -   resource
  -
  -->
<#import "/lib/vtk.ftl" as vrtx />
<#import "/lib/view-utils.ftl" as viewutils />

<#assign resource = resourceContext.currentResource />

<#assign start = vrtx.propValue(resource, "start-date") />
<#assign end = vrtx.propValue(resource, "end-date") />
<#assign location = vrtx.propValue(resource, "location") />
<#assign title = vrtx.propValue(resource, "title") />

<#if start != "" || end != "" || location != "">
  <div class="vevent">
    <#t /><@viewutils.displayTimeAndPlace resource title false false true />
  </div>
</#if>
