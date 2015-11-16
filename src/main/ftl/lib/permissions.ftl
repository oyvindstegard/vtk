<#ftl strip_whitespace=true>
<#--
  - File: permissions.ftl
  - 
  - Description: Permissions listing view component.
  - 
  - Required model data:
  -   resourceContext
  -  
  - Optional model data:
  -
  -->

<#import "/spring.ftl" as spring />
<#import "dump.ftl" as dumper />
<#import "/lib/vtk.ftl" as vrtx />

<#if !resourceContext?exists>
  <#stop "Unable to render model: required submodel
  'resourceContext' missing">
</#if>


<#--
 * editOrDisplayPrivilege
 *
 * Display privilege as a single group with heading.
 * 
 * @param privilegeName - name of privilege
 * @param privilegeHeading - name of group
 *
-->

<#macro editOrDisplayPrivilege privilegeName privilegeHeading>
  <#assign formName = 'permissionsForm_' + privilegeName />

  <#if .vars[formName]?exists>
    <div>
      <div class="permissions-${privilegeName}-wrapper expandedForm expandedForm-${privilegeName} expandedFormIsReplaced">
        <@editACLFormNew
           formName = formName
           privilegeName = privilegeName
           privilegeHeading = privilegeHeading />
      </div>
    </div>
  <#else>
    <h3 class="${privilegeName}">${privilegeHeading}</h3>
    <div class="${privilegeName}">
      <@listPrincipals privilegeName=privilegeName />
    </div>
  </#if>
</#macro>


<#--
 * editOrDisplayPrivileges
 *
 * Display a list of privileges as one group with heading (table).
 * 
 * @param privilegeList - sequence of hashes with name of privilege, privilege heading
 * @param heading - name of group
 *
-->

<#macro editOrDisplayPrivileges privilegeList heading>
  <h3 class="privelegeList">${heading}</h3>
  <table class="privilegeTable">
    <#assign count = 1 />
    <#list privilegeList as p>
      <#if count % 2 == 0>
        <tr class="even ${p.name}">
      <#else>
        <#if count == 1>
          <tr class="odd first ${p.name}">
        <#else>
          <tr class="odd ${p.name}">
        </#if>
      </#if>
      <#assign formName = 'permissionsForm_' + p.name />
      <#assign privilegeName = p.name />
      <#assign privilegeHeading = p.heading />
      <#if .vars[formName]?exists>
        <td colspan="2">
        <@editACLFormNew
           formName = formName
           privilegeName = privilegeName 
           privilegeHeading = privilegeHeading />
        </td>
      <#else>
        <th scope="row" class="key">${privilegeHeading}</th>
        <td>
          <@listPrincipals privilegeName = privilegeName />
        </td>
      </#if>
      </tr>
      <#assign count = count+1 />
    </#list>
  </table>
</#macro>

<#--
 * listPrincipals
 *
 * Lists users and groups that have permissions on the current
 * resource.
 *
 * @param privilegeName - name of privilege
 *
-->

<#macro listPrincipals privilegeName>
  <#assign pseudoPrincipals = aclInfo.privilegedPseudoPrincipals[privilegeName] />
  <#assign users = aclInfo.privilegedUsers[privilegeName] />
  <#assign groups = aclInfo.privilegedGroups[privilegeName] />
  <#assign shortcut = aclInfo.shortcuts[privilegeName] />

  <#if (pseudoPrincipals?size > 0 || users?size > 0 || groups?size > 0) && shortcut == "">
    <#list pseudoPrincipals as pseudoPrincipal>
      <#compress>
        <@vrtx.msg code="pseudoPrincipal.${pseudoPrincipal.name}" default="${pseudoPrincipal.name}" /><#t/>
      </#compress>
      <#if pseudoPrincipal_index &lt; pseudoPrincipals?size - 1  || users?size &gt; 0  || groups?size &gt; 0>, <#t/></#if>
    </#list>
    <#list users as user>
      <#compress><@vrtx.displayUserPrincipal principal=user /></#compress><#t/>
      <#if user_index &lt; users?size - 1 || groups?size &gt; 0>,<#t/></#if>
    </#list>
    <#list groups as group>
      <#compress><@vrtx.displayGroupPrincipal principal=group /></#compress><#t/>
      <#if group_index &lt; groups?size - 1>,<#t/></#if>
    </#list>
    <#if aclInfo.aclEditURLs[privilegeName]?exists>
      &nbsp;<a class="vrtx-button-small full-ajax" href="${aclInfo.aclEditURLs[privilegeName]?html}"><@vrtx.msg code="permissions.privilege.edit" default="edit" /></a>&nbsp;&nbsp;
    </#if>
    <@displayAboutPropShortcut privilegeName "read-write-unpublished" "editorial-contacts" true true "- " />
  <#else>
    <#if shortcut != "">
      <@vrtx.msg code="permissions.shortcut.${shortcut}" default="${shortcut}" /> <#t/>
    <#else>
      <@vrtx.msg code="permissions.not.assigned" default="Not assigned" /> <#t/>
    </#if>
    <#if aclInfo.aclEditURLs[privilegeName]?exists>
      &nbsp;<a class="vrtx-button-small full-ajax" href="${aclInfo.aclEditURLs[privilegeName]?html}"><@vrtx.msg code="permissions.privilege.edit" default="edit" /></a>&nbsp;&nbsp;
    </#if>
  </#if>
</#macro>


<#--
 * editACLForm
 *
 * Macro for displaying the 'Edit ACL' form
 *
 * @param formName - the name of the form
 * @param privilegeName - the privilege to edit
 *
-->
  <#-- 
    Should we use this to access form without having to do @spring.bind all the time?
    assign form=.vars[formName] /
  -->

<#macro editACLFormNew formName privilegeName privilegeHeading>
  <#assign privilege = aclInfo.privileges[privilegeName] />
  <#assign pseudoPrincipals = aclInfo.privilegedPseudoPrincipals[privilegeName] />

  <@spring.bind formName + ".submitURL" />
  <#assign submitUrl = spring.status.value />

  <@spring.bind formName + ".losingPrivileges" />
  <#assign losingPrivileges = spring.status.value />

  <h3>${privilegeHeading}</h3>
  
  <form class="aclEdit" action="${submitUrl?html}" method="post">
    <@spring.bind formName + ".shortcuts" />
    <@listShortcuts privilegeName privilegeHeading spring.status.value />
    <ul class="principalList" id="principalList">
      <@editACLFormGroupsOrUsers "group" privilegeName submitUrl />
      <@editACLFormGroupsOrUsers "user" privilegeName submitUrl />
      <li class="still-admin" style="display:none;">${(!losingPrivileges)?string}</li>
    </ul>
    <@displayAboutPropShortcut privilegeName "read-write-unpublished" "editorial-contacts" true true "<p>" "</p>" />
    <div id="submitButtons" class="submitButtons">
      <input class="vrtx-focus-button" type="submit" name="saveAction" value="<@vrtx.msg code="permissions.save" default="Save"/>" />
      <input class="vrtx-button" type="submit" name="cancelAction" value="<@vrtx.msg code="permissions.cancel" default="Cancel"/>" />
    </div>
  </form>
</#macro>

<#--
 * displayAboutPropShortcut
 *
 * Macro for displaying a link for editing or viewing about prop
 *
 * @param privilegeName - the current privilege
 * @param matchPrivilegeName - which privilege to match
 * @param propName - the name of the prop
 * @param onlyCollection - if only can edit the prop on collection
 * @param capFirst - if should capitalize first letter in text
 * @param pre - markup before
 * @param post - markup after
 *
-->

<#macro displayAboutPropShortcut privilegeName matchPrivilegeName propName onlyCollection=false capFirst=false pre="" post="">
  <#if privilegeName = matchPrivilegeName>
    <#local resource = resourceContext.currentResource />
    <#local propVal = vrtx.propValue(resource, propName) />
    <#local editLinkText = vrtx.getMsg("permissions.privilege.${privilegeName}.${propName}.edit") />
    <#local viewLinkText = vrtx.getMsg("permissions.privilege.${privilegeName}.${propName}.view") />
    
    <#if (!onlyCollection || resource.isCollection()) && aclInfo.aclEditURLs[privilegeName]??>
      ${pre}<a href="?name=${propName}&vrtx=admin&mode=about"><#if !capFirst>${editLinkText}<#else>${editLinkText?cap_first}</#if></a>${post}
    <#elseif propVal?has_content>
      ${pre}<#if !capFirst>${viewLinkText}: ${propVal}<#else>${viewLinkText?cap_first}: ${propVal}</#if>${post}
    </#if>
  </#if>
</#macro>

<#--
 * editACLFormGroupsOrUsers
 *
 * Displaying groups or users sub-lists in the 'Edit ACL' form
 *
 * @param type - "group" or "user"
 *
-->

<#macro editACLFormGroupsOrUsers type privilegeName submitUrl>
  <#assign capitalizedType = "${type?capitalize}" />
  
  <li class="${type}s">
    <fieldset>
      <legend><@vrtx.msg code="permissions.${type}s" default="${capitalizedType}s"/></legend>
      
      <#-- Bind and list principals -->
      <@spring.bind formName + ".${type}s" />
      
      <div class="${type}s-wrapper">
      <#if (spring.status.value?size > 0)>
        <ul class="${type}s">
          <#list spring.status.value as groupOrUser>
            <li>
              <#compress>
                <#if type == "user">
                  <@vrtx.displayUserPrincipal principal=groupOrUser />
                <#else>
                  <@vrtx.displayGroupPrincipal principal=groupOrUser />
                </#if>
              
                <#-- Remove -->
                &nbsp;<input class="removePermission" name="remove${capitalizedType}.${groupOrUser.name?html}" type="submit" value="<@vrtx.msg code='permissions.remove' default='remove' />" />
              </#compress>
            </li>
          </#list>
        </ul>
      </#if>
      </div>
      
      <#-- Bind names -->
      <@spring.bind formName + ".${type}Names" />
      
      <#-- Display errors -->
      <#assign value=""/>
      <#assign errorsExist = false>
      <#if spring.status.errorMessages?size &gt; 0>
        <div class="errorContainer">
          <ul class="errors">
            <#list spring.status.errorMessages as error>
              <li>${error}</li>
            </#list>
          </ul>
        </div>
        <#if spring.status.value?exists>
          <#if spring.status.value?is_sequence>
            <#assign valueCSV = "" />
            <#if (spring.status.value?size > 1)>
              <#list spring.status.value as theValue>
                <#assign valueCSV = valueCSV + theValue + "," />
              </#list>
              <#assign value=valueCSV />
            <#else>
              <#if spring.status.value[0]?exists>
                <#assign value=spring.status.value[0] />
              </#if>
            </#if>
          <#else>
            <#assign value=spring.status.value />
          </#if>
        </#if>
      </#if>
      
      <#-- Add -->
      <span class="add${capitalizedType}">
        <input class="vrtx-textfield" type="text" id="${spring.status.expression}" name="${spring.status.expression}" value="${value?html}" />

        <#if type == "user">
          <@spring.bind formName + ".ac_userNames" />
          <#assign value=""/>
          <#if errorsExist>
            <#assign value = spring.status.value />
          </#if>
          <input type="hidden" id="ac_userNames" name="ac_userNames" value="${value?html}" />
        </#if>
        <input class="vrtx-button add${capitalizedType}Button" type="submit" name="add${capitalizedType}Action"
               value="<@vrtx.msg code="permissions.add${capitalizedType}" default="Add ${capitalizedType}"/>" />
      </span>
    </fieldset>
  </li>
</#macro>

<#--
 * listShortcuts
 *
 * List shortcuts (configurated pr. host)
 *
 * @param privilegeName - name of privilege
 * @param privilegeHeading - privilegeHeading
 *
-->

<#macro listShortcuts privilegeName privilegeHeading shortcuts>
  <#if (shortcuts?size > 0)>
    <ul class="shortcuts" id="${privilegeHeading}">
      <#assign radioCheckedShortcuts = false />
      <#list shortcuts as shortcut>
        <li>
          <label for="${shortcut[0]}-${privilegeName}">
            <#if shortcut[1] == "checked">
              <input id="${shortcut[0]}-${privilegeName}" type="radio" name="updatedShortcut" checked="${shortcut[1]}" value="${shortcut[0]}" />
              <#assign radioCheckedShortcuts = true />
            <#else>
              <input id="${shortcut[0]}-${privilegeName}" type="radio" name="updatedShortcut" value="${shortcut[0]}" />
            </#if>
            <@vrtx.msg code="permissions.shortcut.${shortcut[0]}" />
          </label>
        </li>
      </#list>
      <li>
        <label for="custom">
          <#if !radioCheckedShortcuts>
            <input id="custom" type="radio" name="updatedShortcut" checked="checked" value="" />
          <#else>
            <input id="custom" type="radio" name="updatedShortcut" value="" />
          </#if>
          <@vrtx.msg code="permissions.shortcut.custom" />
        </label>
      </li>
    </ul>
  </#if>
</#macro>
