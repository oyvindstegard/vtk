<#ftl strip_whitespace=true output_format="XHTML" auto_esc=true>
<#import "/lib/vtk.ftl" as vrtx />

<#if !fckeditorBase?exists>
  <#stop "fckeditorBase attribute must exist in model" />
</#if>


<#--
 * editorInTextArea
 * 
 * Writes <script> references to CKEditor JS
 *
 -->
<#macro declareEditor>
  <#if !__editorDeclared?exists>
   	<script type="text/javascript" src="${jsBaseURL}/../jquery/include-jquery.js"></script>
  	<script type="text/javascript" src="${jsBaseURL}/editor.js"></script>
  	<script type="text/javascript" src="${fckeditorBase.url}/ckeditor.js"></script>
 	 <script type="text/javascript" src="${fckeditorBase.url}/adapters/jquery.js"></script>
    <#assign __editorDeclared = true />
  </#if>
</#macro>


<#--
 * editorInTextArea
 *
 * Display a minimal CKeditor in a div.
 *
 * @param textarea - the id of the textarea to replace with CKeditor
 * @param fckeditorBase - the CKeditor config (required to contain a 'url' entry)
 * @param runOnLoad - whether to run the editor immediately or wait
 *        until the JavaScript function loadEditor() is
 *        invoked. Defaults to 'false'.
 * @validElements - a list of [name, attribute-list] maps that
 *        describe the valid HTML elements
 * @toolbarElements - a list of strings that describe the set of
 *        editor toolbar elements to use
 *
-->

<#macro editorInTextarea
        textarea
        toolbar='Complete'
        fontFormats='p;h1;h2;h3;h4;h5;h6;pre'
        fullpage=false
        collapseToolbar=false
        runOnLoad=true
        enableFileBrowsers=true
        fckSkin='editor/skins/silver/'>

    <#if !__editorDeclared?exists>
      <@declareEditor />
    </#if>

    <script type="text/javascript"><!--
      $("#comment-syntax-desc").hide();
      $("#comments-text-div").css("margin", "0").on("click", "#comments-text",function () {
        vrtxEditor.richtextEditorFacade.init({
          name: "comments-text", 
          cssFileList: cssFileList, 
          height: 150,
          maxHeight: 400,
          minHeight: 40,
          toolbar: vrtxEditor.richtextEditorFacade.toolbars.commentsToolbar,
          resizable: true
        });
      });
    // -->
    </script>
</#macro>
