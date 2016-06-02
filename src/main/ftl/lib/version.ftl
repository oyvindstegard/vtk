<#ftl strip_whitespace=true output_format="HTML" auto_esc=true>
<#--
  - File: version.ftl
  - 
  - Description: Macro for displaying version information
  - 
  - Required model data:
  -   version
  -  
  - Optional model data:
  -   
  -
  -->

<#macro displayVersion version>
    <#if version?exists>
    <div class="versionInfo">
      <@vrtx.msg "version.shortDisplaystring",
      "version", [version.getFrameworkTitle(), version.getVersion()]/>
      <!-- (<@vrtx.msg "version.displaystring",
      "version", [version.getFrameworkTitle(), version.getVersion(),
      version.getBuildDate()?datetime?string.long, version.getBuildHost()] /> ) -->
    </div>
    </#if>
</#macro>
