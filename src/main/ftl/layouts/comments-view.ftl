<#ftl strip_whitespace=true>
<#--
  - File: comments-view.ftl
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
<#import "/lib/vortikal.ftl" as vrtx />
<#import "/fckeditor/fckeditor-textarea.ftl" as fck />

<#if !comments?exists || !config?exists>
  <#stop "Unable to render model: model data missing: required entries
          are 'comments' and 'config'">
</#if>


<#if !commentsEnabled && comments?size = 0>
  <#-- display nothing -->
<#else>
<div class="vrtx-comments" id="comments">
  <#if comments?exists>
    <div class="comments-header">
    <#assign header>
      <@vrtx.msg code="commenting.header"
                 default="Comments (" + comments?size + ")"
                 args=[comments?size] />
    </#assign>
    <#if baseCommentURL?exists>
      <a class="header-href" href="${(baseCommentURL + '#comments')?html}">${header}</a>
    <#else>
      ${header}
    </#if><sup>BETA</sup>


    <#assign message><@vrtx.msg code="commenting.deleteall" default="delete all" /></#assign>
      <#assign confirmation>
        <@vrtx.msg code="commenting.deleteall.confirmation" 
                   default="Are you sure you want to delete all ${comments?size} comments?" 
                   args=[comments?size] />
      </#assign>

    <#if deleteAllCommentsURL?exists>(&nbsp;<a onclick="return confirm('${confirmation}')" href="${deleteAllCommentsURL?html}">${message}</a>&nbsp;)</#if>
    <#if feedURL?exists>
      <a href="${feedURL?html}">RSS</a>
    </#if>
  </#if>
 </div>

  <#assign comments = comments?sort_by("time") />

  <#assign rowclass="even" />

    <#list comments as comment>
      <div class="vrtx-comment ${rowclass}" id="comment-${comment.ID?c}">
        <#if config.titlesEnabled>
          <div class="comment-title">
            <#if baseCommentURL?exists>
              <a href="${(baseCommentURL + '#comment-' + comment.ID?c)?html}">${comment.title?html}</a>
              <#else>
                ${comment.title?html}
              </#if>
          </div>
        </#if>
      <div class="comment-body">
      <#if config.htmlContentEnabled>
        <#-- Display content as raw html: -->
        ${comment.content}
      <#else>
        <#-- Display content as escaped html: -->
        ${comment.content?html}
      </#if>
      </div>
      <div class="comment-info">
        ${comment.author?html} -
        <@vrtx.date value=comment.time format='long' />
        <#if deleteCommentURLs[comment.ID?c]?exists>
          <#assign message><@vrtx.msg code="commenting.delete" default="delete" /></#assign>
          <#assign confirmation>
            <@vrtx.msg code="commenting.delete.confirmation" 
                       default="Are you sure you want to delete this comment?" />
          </#assign>
          (&nbsp;<a onclick="return confirm('${confirmation}');" href="${deleteCommentURLs[comment.ID?c]?html}">${message}</a>&nbsp;)
        </#if>
      </div>
    </div>
    <#assign rowclass><#if rowclass="even">odd<#else>even</#if></#assign>
  </#list>

  <div class="add-comment" id="comment-form">
    <div class="add-comment-header"><@vrtx.msg code="commenting.form.add-comment" default="Add comment" /><sup>BETA</sup></div>


    <#if !commentsEnabled>
      <p><@vrtx.msg code="commenting.disabled"
                    default="Commenting is disabled on this resource." /></p>

    <#elseif !postCommentURL?exists && !principal?exists && loginURL?exists>
      <#assign completeLoginURL>${loginURL?html}&amp;anchor=comment-form</#assign>
      <#assign defaultMsg>
        To comment on this resource you have to <a href="${completeLoginURL}">log in</a>
      </#assign>
      <p><@vrtx.rawMsg code="commenting.not-logged-in" default=defaultMsg args=[completeLoginURL] /></p>

    <#elseif principal?exists && !postCommentURL?exists>
      <p><@vrtx.msg code="commenting.denied"
                    default="You do not have sufficient privileges to add comments on this resource." /></p>

    <#elseif postCommentURL?exists>
      <div id="comment-syntax-desc" class="comment-syntax-desc">
        <div class="syntax-head"><@vrtx.msg code="commenting.form.syntax-description" default="Allowed HTML syntax" />:</div>
        <p>
        <#list config.validHtmlElements as element>
          &lt;${element.name?html}<#compress>
          <#if (element.attributes)?exists>
            <#list element.attributes as attr>
              &nbsp;${attr?html}=".."
            </#list>
          </#if>
          </#compress>&gt;<#if element_has_next>, </#if>
        </#list>
        </p>
      <#if commenting.richEditorEnabled>
        <script type="text/javascript">
          function editor() {
             document.getElementById("comment-syntax-desc").style.display = "none";
             document.getElementById("comments-text-div").style.margin = "0";
             loadEditor();
          }
          document.write("<p><a class=\"javascript-editor\" href=\"javascript:editor();\"><@vrtx.msg code="commenting.form.rich-editor" default="Use HTML-editor" /></a></p>");
        </script>
      </#if>      
      </div>
      <form action="${postCommentURL?string?html}#comment-form" method="post">
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
          <textarea id="comments-text" name="text" rows="6" cols="80">${value?html}</textarea>
        <#if errors?exists && errors.getFieldError('text')?exists>
          <div class="error">
            <@vrtx.msg code=errors.getFieldError('text').getCode()
                       default=errors.getFieldError('text').getDefaultMessage() 
                       args=errors.getFieldError('text').getArguments()  />
          </div>
        </#if>
        </div>
        <div class="submit">
        <input type="submit" id="submit-comment-button" name="save" value="<@vrtx.msg code='commenting.form.submit' default='Submit' />" /> (<@vrtx.msg code="commenting.as"
                    default="as" /> <span class="user">${principal?html}</span>)</div>
      </form>
      <#if commenting.richEditorEnabled>
        <@fck.editorInTextarea textarea="comments-text"
                               validElements=config.validHtmlElements
                               runOnLoad=false fckeditorBase=fckeditorBase />
      </#if>
    </#if>

  </div>
</div>
</#if>
