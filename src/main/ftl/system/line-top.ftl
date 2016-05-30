<#ftl strip_whitespace=true output_format="XHTML" auto_esc=true>
<#import "/lib/vtk.ftl" as vrtx />

<#assign upscopedHost = "" />
<#-- <#if !displayUpscoping?exists || (displayUpscoping?exists && (displayUpscoping = "false" || displayUpscoping?trim = ""))>
  <#assign upscopedHost = "not-upscoped-host" />
</#if> -->

<#assign language = vrtx.getMsg("eventListing.calendar.lang", "en") />

<div id="line-top"<#if upscopedHost != ""> class="${upscopedHost}"</#if>>
  <#if upscopedHost == "">
    <div id="uiologo" <#if language == "en">class="english-uiologo"</#if>>
      <#if language != "en">
        <a href="http://www.uio.no/">Universitetet i Oslo</a>
      <#else>
        <a href="http://www.uio.no/english/">University of Oslo</a>
      </#if>
    </div> 
  </#if>
</div>
