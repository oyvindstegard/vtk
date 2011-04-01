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
<#import "/lib/vortikal.ftl" as vrtx />
<#import "/lib/autocomplete.ftl" as autocomplete />

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
      <div class="expandedForm">
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
      <#if aclInfo.aclEditURLs[privilegeName]?exists>
        (&nbsp;<a href="${aclInfo.aclEditURLs[privilegeName]?html}"><@vrtx.msg code="permissions.privilege.edit" default="edit" /></a>&nbsp;)
      </#if>
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
  <table>
    <#list privilegeList as p>
      <tr>
      <#assign formName = 'permissionsForm_' + p.name />
      <#assign privilegeName = p.name />
      <#assign privilegeHeading = p.heading />
      <#if .vars[formName]?exists>
        <td colspan="2" class="expandedForm">
        <@editACLFormNew
           formName = formName
           privilegeName = privilegeName 
           privilegeHeading = privilegeHeading />
        </td>
      <#else>
        <td class="key">${privilegeHeading}</td>
        <td>
          <@listPrincipals privilegeName = privilegeName />
          <#if aclInfo.aclEditURLs[privilegeName]?exists>
            (&nbsp;<a href="${aclInfo.aclEditURLs[privilegeName]?html}"><@vrtx.msg code="permissions.privilege.edit" default="edit" /></a>&nbsp;)
          </#if>
        </td>
      </#if>
      </tr>
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

  <#if (pseudoPrincipals?size > 0 || users?size > 0 || groups?size > 0)>
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
      <#local lkey = "permissions.shortcut.group" + group.name />
      <#compress><@vrtx.msg code=lkey default="${group.name}" /></#compress><#t/>
      <#if group_index &lt; groups?size - 1>,<#t/></#if>
    </#list>
  <#else>
    <@vrtx.msg code="permissions.not.assigned" default="Not assigned" /> <#t/>
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
  <form class="aclEdit" action="${spring.status.value?html}" method="post">
    <h3>${privilegeHeading}</h3>
    <@spring.bind formName + ".shortcuts" />
    <@listShortcuts privilegeName privilegeHeading spring.status.value />
    <ul class="principalList" id="principalList">
      <@editACLFormGroupsOrUsers "group" />
      <@editACLFormGroupsOrUsers "user" />
    </ul>
    <div id="submitButtons" class="submitButtons">
      <input type="submit" name="saveAction" value="<@vrtx.msg code="permissions.save" default="Save"/>">
      <input type="submit" name="cancelAction" value="<@vrtx.msg code="permissions.cancel" default="Cancel"/>">
    </div>
  </form>
</#macro>

<#--
 * editACLFormGroupsOrUsers
 *
 * Displaying groups or users sub-lists in the 'Edit ACL' form
 *
 * @param type - "group" or "user"
 *
-->

<#macro editACLFormGroupsOrUsers type>
  <#assign capitalizedType = "${type?capitalize}" />
  <li class="${type}s">
    <fieldset>
      <legend><@vrtx.msg code="permissions.${type}s" default="${capitalizedType}s"/></legend>
      
      <#-- Bind removeUrls -->
      <@spring.bind formName + ".remove${capitalizedType}URLs" />
      <#assign removeURLs=spring.status.value />
      
      <#-- Bind and list principals -->
      <@spring.bind formName + ".${type}s" /> 
      <ul class="${type}s">
        <#list spring.status.value as groupOrUser>
          <li>
            <#compress>
              <#if type == "user">
                <@vrtx.displayUserPrincipal principal=groupOrUser />
              <#else>
                ${groupOrUser.name}
              </#if>
              
              <#-- Remove link -->
              &nbsp;(&nbsp;<a href="${removeURLs[groupOrUser.name]?html}"><#t/>
              <#t/><@vrtx.msg code="permissions.remove" default="remove"/></a>&nbsp;)
              
            </#compress>
          </li>
        </#list>
      </ul>
      
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
              <#assign value=spring.status.value[0] />
            </#if>
          <#else>
            <#assign value=spring.status.value />
          </#if>
        </#if>
      </#if>
      
      <#-- Add -->
      <span class="add${capitalizedType}">      
        <input type="text" id="${spring.status.expression}" name="${spring.status.expression}" value="${value?html}" />
        <#if type == "user">
          <@spring.bind formName + ".ac_userNames" />
          <#assign value=""/>
          <#if errorsExist>
            <#assign value = spring.status.value />
          </#if>        
          <input type="hidden" id="ac_userNames" name="ac_userNames" value="${value?html}" />
        </#if>
        <input class="add${capitalizedType}Button" type="submit" name="add${capitalizedType}Action"
               value="<@vrtx.msg code="permissions.add${capitalizedType}" default="Add ${capitalizedType}"/>"/>   
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
  <ul class="shortcuts" id="${privilegeHeading}">
    <#list shortcuts as shortcut>
      <li>
        <label for="${privilegeName}"> 
          <#if shortcut[1] == "checked">
            <input type="checkbox" name="updatedShortcuts" checked="${shortcut[1]}" value="${shortcut[0]}" />
          <#else>
            <input type="checkbox" name="updatedShortcuts" value="${shortcut[0]}" />             
          </#if>
          <@vrtx.msg code="permissions.shortcut.${shortcut[0]}" />
        </label>
      </li>
    </#list>
  </ul>
</#macro>
