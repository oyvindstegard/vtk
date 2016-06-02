<#ftl strip_whitespace=true output_format="HTML" auto_esc=true>
<#--
  - File: comments-component.ftl
  - 
  - Description: displays a list of comments on the page.
  - 
  - Required model data:
  -   comments
  -   commentsEnabled
  -   config
  -
  - Optional model data:
  -   form
  -   principal
  - 
  -->
<#import "/lib/vtk.ftl" as vrtx />
<#import "/ckeditor/ckeditor-textarea.ftl" as ck />

<#if !comments?exists || !config?exists>
  <#stop "Unable to render model: model data missing: required entries
          are 'comments' and 'config'">
</#if>

<#if commentsEnabled>
  <@ck.declareEditor />
</#if>

<#if (commentsEnabled || comments?size > 0)>

<div class="vrtx-comments" id="comments">
  <#if comments?exists>
    <div class="comments-header">

    <div id="comments-header-left">
      <#assign header>
        <@vrtx.msg code="commenting.header"
                   default="Comments (" + comments?size + ")"
                   args=[comments?size] />
      </#assign>
      <#if baseCommentURL?exists>
        <a class="header-href" href="${(baseCommentURL + '#comments')}">${header}</a>
      <#else>
        ${header}
      </#if>
  
      <#assign message><@vrtx.msg code="commenting.deleteall" default="delete all" /></#assign>
      <#assign confirmation>
        <@vrtx.msg code="commenting.deleteall.confirmation" 
                   default="Are you sure you want to delete all ${comments?size} comments?" 
                   args=[comments?size] />
      </#assign>
  
      <#if (deleteAllCommentsURL?exists && comments?size > 0)>
        <form class="vrtx-comments-delete-all" action="${deleteAllCommentsURL}" method="post">
          <@vrtx.csrfPreventionToken url=deleteAllCommentsURL />
          <button type="submit" id="vrtx-comments-delete-all" class="button" name="delete-all-comments-button"
                  onclick="return confirm('${confirmation}');"><span>${message}</span></button>
        </form>
      </#if>
    </div>
    
    <#if feedURL?exists>
      <div id="comments-header-feedHref">
         <a href="${feedURL}"><@vrtx.msg code="commenting.subscribe" default="subscribe" /></a>
      </div>
    </#if>
  </#if>
 </div>

  <#assign comments = comments?sort_by("time") />

  <#assign rowclass="even" />

    <#list comments as comment>
      <div class="vrtx-comment ${rowclass}" id="comment-${comment.ID}">
        <#if config.titlesEnabled>
          <div class="comment-title">
            <#if baseCommentURL?exists>
              <a href="${(baseCommentURL + '#comment-' + comment.ID)}">${comment.title}</a>
              <#else>
                ${comment.title}
              </#if>
          </div>
        </#if>
      <div class="comment-body">
      <#if config.htmlContentEnabled>
        <#-- Display content as raw html: -->
        ${comment.content?no_esc}
      <#else>
        <#-- Display content as escaped html: -->
        ${comment.content}
      </#if>
      </div>
      <div class="comment-info">
        <#if comment.author.URL?exists>
         <span class="comment-author"><a href="${comment.author.URL}">${comment.author.description}</a></span><span class="comment-author-line"> -</span>
        <#else>
         <span class="comment-author"><@vrtx.breakSpecificChar nchars=21 char='@'>${comment.author.description}</@vrtx.breakSpecificChar></span><span class="comment-author-line"> -</span>
        </#if>
         <span class="comment-date"><@vrtx.date value=comment.time format='long' /></span>
        <#if deleteCommentURLs[comment.ID]?exists>
          <#assign message><@vrtx.msg code="commenting.delete" default="delete" /></#assign>
          <#assign confirmation>
            <@vrtx.msg code="commenting.delete.confirmation" 
                       default="Are you sure you want to delete this comment?" />
          </#assign>
          <form class="vrtx-comments-delete" action="${deleteCommentURLs[comment.ID]}" method="post">
            <@vrtx.csrfPreventionToken url=deleteCommentURLs[comment.ID] />
            <button class="comment-delete-button button" type="submit" onclick="return confirm('${confirmation}');"><span>${message}</span></button>
          </form>
        </#if>
      </div>
      <span class="vrtx-comment-background-hook-1"></span>
    </div>
    <#assign rowclass><#if rowclass="even">odd<#else>even</#if></#assign>
  </#list>
  
  <div class="add-comment" id="comment-form">
    <div class="add-comment-header"><@vrtx.msg code="commenting.form.add-comment" default="Add comment" /></div>
    <#if !commentsEnabled>
       <p><@vrtx.msg code="commenting.disabled"
                  default="Commenting is disabled on this resource." /></p>
    <#elseif !principal?exists>
      <#assign completeLoginURL>${loginURL}&amp;anchor=comment-form</#assign>
      <#assign defaultMsg><a class="button" href="${completeLoginURL}"><span>Log in</span></a> to comment</#assign>
      <div id="add-comment-login"><@vrtx.rawMsg code="commenting.not-logged-in" default=defaultMsg?markup_string args=[completeLoginURL?markup_string] /></div>
      <div id="add-comment-webid"><@vrtx.rawMsg code="commenting.not-logged-in.webid" default="" /></div>
    <#elseif repositoryReadOnly>
       <p><@vrtx.msg code="commenting.read.only"
                    default="You cannot add comments because the system is currently in read only mode." /></p>
      
    <#elseif commentsAllowed && commentsLocked>
      <p><@vrtx.msg code="commenting.locked"
                    default="You can not comment this resource at the moment because it is locked by another user." /></p>

    <#elseif !commentsAllowed>
      <p><@vrtx.msg code="commenting.denied"
                    default="You are not allowed to comment on this resource." /></p>

    <#elseif commentsAllowed && postCommentURL?exists>
      <script type="text/javascript"><!--
          // CKEditor CSS
          var cssFileList = [<#if fckEditorAreaCSSURL?exists>
                               <#list fckEditorAreaCSSURL as cssURL>
                                 "${cssURL}" <#if cssURL_has_next>,</#if>
                               </#list>
                             </#if>]; 
      //-->
      </script>
    
      <div id="comment-syntax-desc" class="comment-syntax-desc">
        <div class="syntax-head"><@vrtx.msg code="commenting.form.syntax-description" default="Allowed HTML syntax" />:</div>
        <p>
        <#list config.validHtmlElements as element>
          &lt;${element.name}<#compress>
          <#if (element.attributes)?exists>
            <#list element.attributes as attr>
              &nbsp;${attr}=".."
            </#list>
          </#if>
          </#compress>&gt;<#if element_has_next>, </#if>
        </#list>
        </p>
      </div>
      <form class="vrtx-comments-post" action="${postCommentURL?string}#comment-form" method="post">
        <@vrtx.csrfPreventionToken url=postCommentURL />
        <#if config.titlesEnabled>
        <div class="comments-title">
          <#assign value><#if form?exists && form.title?exists>${form.title}</#if></#assign>
          <label for="comments-title">
            <@vrtx.msg code="commenting.form.title" default="Title" /></label>
          <input id="comments-title" type="text" name="title" size="30" value="${value}" />
          <#if errors?exists && errors.getFieldError('title')?exists>
            <span class="error">
              <@vrtx.msg code=errors.getFieldError('title').getCode()
                         default=errors.getFieldError('title').getDefaultMessage() />
            </span>
          </#if>
        </div>
        </#if>
        <div class="comments-text" id="comments-text-div">
          <#assign value><#if form?exists && form.text?exists>${form.text}</#if></#assign>
          <textarea id="comments-text" name="text" rows="6" cols="80">${value}</textarea>
        <#if errors?exists && errors.getFieldError('text')?exists>
          <div class="error">
            <@vrtx.msg code=errors.getFieldError('text').getCode()
                       default=errors.getFieldError('text').getDefaultMessage() 
                       args=errors.getFieldError('text').getArguments()  />
          </div>
        </#if>
        </div>
        <div class="submit">
        <#assign principalStr>
          <#compress>
            <span class="user">${principal.description}</span>
          </#compress>
        </#assign>
        <input type="submit" id="submit-comment-button" name="save"
          value="<@vrtx.msg code='commenting.form.submit' default='Submit' />" />
          (<@vrtx.rawMsg code="commenting.post.comment-as" default="as ${principal.description}" args=[principalStr?markup_string] />)
        </div> 
      </form>
      <@ck.editorInTextarea textarea="comments-text" toolbar="AddComment" runOnLoad=false  />
    </#if>
  </div>
</div>

</#if>
