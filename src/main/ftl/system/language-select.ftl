<#ftl strip_whitespace=true output_format="XHTML" auto_esc=true>
<#--
  - File: select-language.ftl
  - 
  - Description: component for locale switching
  - 
  - Needed but not required model data:
  -  switchLocaleActions
  -
  -->
<#import "/lib/vtk.ftl" as vrtx />

<#if switchLocaleActions?exists>
  <#compress>
    <div id="locale-selection">
      <span id="locale-selection-header"><@vrtx.msg code="localeSelection.selectLocale" default="Language settings"/>:</span>
      <ul>
        <#list switchLocaleActions.localeServiceNames as locale>
          <#assign active = switchLocaleActions.localeServiceActive[locale] />
          <li class="locale ${locale} ${active}">
            <#if active = "active">
              <span><@vrtx.msg code="locales.${locale}" default="${locale}"/></span>
            <#else>
              <a href="${switchLocaleActions.localeServiceURLs[locale]}"><@vrtx.msg code="locales.${locale}" default="${locale}"/></a>
            </#if>
          </li>
        </#list>
      </ul>
    </div>
  </#compress>
</#if>
