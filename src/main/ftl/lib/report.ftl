<#ftl strip_whitespace=true output_format="XHTML" auto_esc=true>
<#--
 * alternativeViewCheckbox
 *
 * Display a checkbox for switching between an alternative view in a report (JS-based url change)
 * 
 * @param alternativeName - alternative name from report
 * @param isAlternativeView - if alternative name exists as a parameter in request
 *
-->
<#macro alternativeViewCheckbox alternativeName alternativeLabelText isAlternativeView=false>
  <div id="is-${alternativeName}-view" class="vrtx-checkbox vrtx-report-alternative-view-switch">
    <input name="${alternativeName}" id="is-${alternativeName}" type="checkbox" <#if isAlternativeView>checked="checked"</#if> />
    <label for="is-${alternativeName}"><@vrtx.msg code=alternativeLabelText /></label>
  </div>
</#macro>

<#macro generateFilters filters>
    <#if filters?exists && (filters?size > 0)>
      <div id="vrtx-report-filters">
        <#list report.filters?keys as filterKey>
          <#local filterOpts = filters[filterKey] />
          <#if (filterOpts?size > 0)>
            <#if (filterKey_index == (filters?size - 1))>
              <ul class="vrtx-report-filter vrtx-report-filter-last" id="vrtx-report-filter-${filterKey}">
            <#else>
              <ul class="vrtx-report-filter" id="vrtx-report-filter-${filterKey}">
            </#if>
              <#list filterOpts as filterOpt>
                <#local filterID = "vrtx-report-filter-" + filterKey + "-" + filterOpt.name />
                <#if filterOpt.active>
                  <li class="active-filter" id="${filterID}">
                    <span><@vrtx.msg code="report.${report.reportname}.filters.${filterKey}.${filterOpt.name}" /></span>
                <#else>
                  <li id="${filterID}">
                    <a href="${filterOpt.URL}"><@vrtx.msg code="report.${report.reportname}.filters.${filterKey}.${filterOpt.name}" /></a>
                </#if>
                  </li>
              </#list>
            </ul>
          </#if>
        </#list>
      </div>
    </#if>
 </#macro>
