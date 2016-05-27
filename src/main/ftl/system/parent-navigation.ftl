<#ftl strip_whitespace=true output_format="XHTML" auto_esc=true>
<#import "/lib/vtk.ftl" as vrtx />
<#if tabMenuLeft?exists>
  <#if (tabMenuLeft.url)?exists>
    <ul class="list-menu" id="tabMenuLeft">
      <li class="navigateToParentService">
        <a id="navigateToParentService" href="${tabMenuLeft.url}">
          <@vrtx.msg code="collectionListing.navigateToParent" default="Up"/>
        </a>
      </li>
    </ul>
  </#if>
</#if>
