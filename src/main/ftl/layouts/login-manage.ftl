<#ftl strip_whitespace=true output_format="HTML" auto_esc=true>
<#import "/lib/vtk.ftl" as vrtx />
<#import "/lib/view-utils.ftl" as viewutils />

<#if options?has_content>
  <#assign type = "login-manage" />

  <#-- First option => visible dropdown toggler (and link if not principal desc.) -->
  <#list options?keys as opt>
    <#if opt_index == 1><#break /></#if>
    <#if opt = "principal-desc">
      <#assign title = principal.description />
      <#assign titleLink = "" />
   <#else>
      <#assign title = vrtx.getMsg("decoration.${type}.${opt}") />
      <#assign titleLink = options[opt] />
      <#assign titleLinkTip = vrtx.getMsg("decoration.${type}.tip") />
    </#if>
  </#list>
  
  <#-- Rest of options => dropdown list -->
  <#if (options?size > 1)>
    <!-- begin view dropdown js -->
    <script type="text/javascript" src="${jsUrl}"></script>
    <!-- end view dropdown js -->
  
    <@viewutils.displayDropdown type title titleLink false titleLinkTip>
      <ul>
      <#list options?keys as opt>
        <#if (opt_index > 0)>
          <#assign classes = "" />
          <#if (opt_index == 1)>
            <#assign classes = classes + "vrtx-dropdown-first" />
          </#if>
          <#if (opt_index == (options?size - 1))>
            <#if classes != ""><#assign classes = classes + " " /></#if>
            <#assign classes = classes + "vrtx-dropdown-last" />
          </#if>
          <li<#if classes != ""> class="${classes}"</#if>>
            <#assign url = options[opt] />
            <#if opt = "logout">
              <form action="${url}" method="post" class="vrtx-dropdown-form">
                <@vrtx.csrfPreventionToken url=url />
                <button type="submit" name="logoutAction">
                  <@vrtx.msg code="decoration.${type}.${opt}" />
                </button>
              </form>
              <a href="javascript:void(0);" class="vrtx-${type}-${opt} vrtx-dropdown-form-link">
                <@vrtx.msg code="decoration.${type}.${opt}" />
              </a>
            <#else>
              <a href="${url}<@adminIndexFile type />" class="vrtx-${type}-${opt}">
                <@vrtx.msg code="decoration.${type}.${opt}" />
              </a>
            </#if>
          </li>
        </#if>
      </#list>
      </ul>
    </@viewutils.displayDropdown>
    
  <#else>
 
    <div class="vrtx-${type}-component">
      <a href="<#if titleLink != ''>${titleLink}<@adminIndexFile type /><#else>javascript:void(0)</#if>" class="vrtx-${type}-link">
        ${title}
      </a>
    </div>
    
  </#if>
  
</#if>

<#macro adminIndexFile type><#compress>
  <#assign isIndexFile = resourceContext.requestContext.indexFile />
  <#if isIndexFile && type = "login-manage">#admin-index-file=on</#if>
</#compress></#macro>
