<#import "/lib/vortikal.ftl" as vrtx />

<#if !fckeditorBase?exists>
  <#stop "fckeditorBase attribute must exist in model" />
</#if>


<#--
 * editorInTextArea
 * 
 * Writes a <script> reference to the main fckeditor.js file
 *
 -->
<#macro declareEditor>
  <#if !__editorDeclared?exists>
   	<script language="Javascript" type="text/javascript" src="${jquery}"></script>
  	<script language="Javascript" type="text/javascript" src="${jsBaseURL?html}/admin-fck-setup.js"></script>
  	<script language="Javascript" type="text/javascript" src="${fckeditorBase.url?html}/ckeditor.js"></script>
 	 <script type="text/javascript" src="${fckeditorBase.url?html}/adapters/jquery.js"></script>
    <#assign __editorDeclared = true />
  </#if>
</#macro>


<#--
 * editorInTextArea
 *
 * Display a minimal FCKeditor in a div.
 *
 * @param textarea - the id of the textarea to replace with FCKeditor
 * @param fckeditorBase - the FCKeditor config (required to contain a 'url' entry)
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
        format_tags='p;h2;h3;h4;h5;h6;pre'
        fullpage=false
        collapseToolbar=false
        runOnLoad=true
        enableFileBrowsers=true
        fckSkin='editor/skins/silver/'>

    <#if !__editorDeclared?exists>
      <@declareEditor />
    </#if>

    <script type="text/javascript">
		commentsCkEditor();	  
    </script>
</#macro>
