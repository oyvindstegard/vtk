<#ftl strip_whitespace=true>

<#--
  - File: kupu-edit.ftl
  - 
  - Description: HTML page that displays an iframe for editing the
  - contents of a XHTML resource with Kupu
  - 
  - Required model data:
  -  
  - Optional model data:
  -
  -->
<#if !kupuEditForm?exists>
  <#stop "Unable to render model: required submodel
  'kupuEditForm' missing">
</#if>
<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<html xmlns="http://www.w3.org/1999/xhtml" xmlns:i18n="http://xml.zope.org/namespaces/i18n" i18n:domain="kupu">
  <head>
    <base href="${kupuBase.url?html}/common/" />
    <title>Kupu Editor</title>
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8"/>

    <!-- Include standard CSS: -->
    <link href="kupustyles.css" rel="stylesheet" type="text/css"/>
    <link href="kupudrawerstyles.css" rel="stylesheet" type="text/css"/>

    <!-- Override standard CSS: -->
    <link href="${kupuLocalStylesheet?html}" rel="stylesheet" type="text/css"/>
    <link href="${kupuLocalDrawerStylesheet?html}" rel="stylesheet" type="text/css"/>
    
    <!-- In case we need to override without deploying new Vortex version.  -->
    <!-- This stylesheet should normally be empty.                          -->
    <link href="http://www.uio.no/profil/kupu/kupustyles-override.css" rel="stylesheet" type="text/css"/>
    
    
    <!-- needed to override popup-funtions from 'kupubasetools.js' -->
	<script type="text/javascript" src="${cssBaseURL}/browser-sniffer.js"> </script>
	
    <script type="text/javascript" src="sarissa.js"> </script>
    <script type="text/javascript" src="sarissa_ieemu_xpath.js"> </script>
    <script type="text/javascript" src="kupuhelpers.js"> </script>
    <!-- <script type="text/javascript" src="kupueditor.js"> </script> -->
    <script type="text/javascript" src="${kupuLocalBase}/kupueditor-vortikal.js"> </script> <!-- overridden file (minor changes to alert message) -->
    <!-- <script type="text/javascript" src="kupubasetools.js"> </script> -->
    <script type="text/javascript" src="${kupuLocalBase}/kupubasetools-vortikal.js"> </script> <!-- overridden two functions: added variable 'baseURL', for IE, to LinkTool() and ImageTool() -->
    <script type="text/javascript" src="kupuloggers.js"> </script>
    <script type="text/javascript" src="kupunoi18n.js"> </script>
    <!--script type="text/javascript" src="../../i18n.js/i18n.js"> </script-->
    <script type="text/javascript" src="kupucleanupexpressions.js"> </script>
    <script type="text/javascript" src="kupucontentfilters.js"> </script>  <!-- TROR ikke denne er i bruk lenger? (begge funksjonene overstyres i kupucontentfilters-vortikal.js -->
    <script type="text/javascript" src="${kupuLocalBase}/kupucontentfilters-vortikal.js"> </script>
    <script type="text/javascript" src="kuputoolcollapser.js"> </script>
    <script type="text/javascript" src="kupucontextmenu.js"> </script>
    
    <!-- Local modifications to Kupu: -->
    <script type="text/javascript" src="${kupuLocalBase}/kupueditor-local.js"> </script>
    <!--script type="text/javascript" src="kupuinit.js"> </script-->
    <script type="text/javascript" src="${kupuLocalBase}/kupuinit-vortikal.js"> </script>
    <script type="text/javascript" src="kupustart.js"> </script>
    <!-- <script type="text/javascript" src="kupusaveonpart.js"> </script> -->
    <script type="text/javascript" src="${kupuLocalBase}/kupusaveonpart-vortikal.js"> </script>  <!-- NB: Denne er blitt modifisert og endringene skal meldes inn til Kupu-prosjektet -->
    <script type="text/javascript" src="kupusourceedit.js"> </script>
    <script type="text/javascript" src="kupuspellchecker.js"> </script>
    <script type="text/javascript" src="kupudrawers.js"> </script>
    <script type="text/javascript" src="${kupuLocalBase}/dynamic-css-to-resize-iframe.js"> </script>


  </head>
  <body onload="kupu = startKupu(); kupuLocalHook(kupu);">
    <div style="display: none;">
      <xml id="kupuconfig" class="kupuconfig">
        <kupuconfig>
          <dst>${kupuDest.destURL?html}</dst>
          <use_css>0</use_css>
          <reload_after_save>0</reload_after_save>
          <strict_output>0</strict_output>
          <content_type>text/html</content_type>
          <compatible_singletons>1</compatible_singletons>
          <table_classes>
            <class>ridge</class>
            <class>solid</class>
            <class>invert</class>
          </table_classes>
          <cleanup_expressions>
            <set>
              <name>Convert single quotes to curly ones</name>
              <expression>
                <reg>
            (\W)'
          </reg>
                <replacement>
            \1&#x2018;
          </replacement>
              </expression>
              <expression>
                <reg>
            '
          </reg>
                <replacement>
            &#x2019;
          </replacement>
              </expression>
            </set>
            <set>
              <name>Reduce whitespace</name>
              <expression>
                <reg>
            [\n\r\t]
          </reg>
                <replacement>
            \x20
          </replacement>
              </expression>
              <expression>
                <reg>
            [ ]{2}
          </reg>
                <replacement>
            \x20
          </replacement>
              </expression>
            </set>
          </cleanup_expressions>
          <image_xsl_uri>${kupuDrawerService.url?html}</image_xsl_uri>
          <link_xsl_uri>${kupuDrawerService.url?html}</link_xsl_uri>
          <image_libraries_uri><#if (kupuImageLibraries.URL)?exists>${kupuImageLibraries.URL?html}</#if></image_libraries_uri>
          <link_libraries_uri><#if (kupuLinkLibraries.URL)?exists>${kupuLinkLibraries.URL?html}</#if></link_libraries_uri>
          <search_images_uri> </search_images_uri>
          <search_links_uri> </search_links_uri>

          <!--
          <image_xsl_uri>kupudrawers/drawer.xsl</image_xsl_uri>
          <link_xsl_uri>kupudrawers/drawer.xsl</link_xsl_uri>
          <image_libraries_uri>kupudrawers/imagelibrary.xml</image_libraries_uri>
          <link_libraries_uri>kupudrawers/linklibrary.xml</link_libraries_uri>
          <search_images_uri> </search_images_uri>
          <search_links_uri> </search_links_uri>
          -->
        </kupuconfig>
      </xml>
    </div>
    <div class="kupu-fulleditor">
      <div class="kupu-tb" id="toolbar">
        <span id="kupu-tb-buttons" class="kupu-tb-buttons">
          <span class="kupu-tb-buttongroup kupu-logo" style="float: right" id="kupu-logo">
            <button type="button" class="kupu-logo" title="Kupu 1.3.1" i18n:attributes="title" accesskey="k" onclick="window.open('http://kupu.oscom.org');">&#xA0;</button>
          </span>
          <span class="kupu-tb-buttongroup" style="float: right" id="kupu-zoom">
            <button type="button" class="kupu-zoom" id="kupu-zoom-button" i18n:attributes="title" title="zoom: alt-x" accesskey="x">&#xA0;</button>
          </span>
          <select id="kupu-tb-styles">
            <option value="P" i18n:translate="">
        Normal
      </option>
            <option value="H1">
              <span i18n:translate="">Heading 1</span>
            </option>
            <option value="H2">
              <span i18n:translate="">Heading 2</span>
            </option>
            <option value="H3">
              <span i18n:translate="">Heading 3</span>
            </option>
            <option value="H4">
              <span i18n:translate="">Heading 4</span>
            </option>
            <option value="H5">
              <span i18n:translate="">Heading 5</span>
            </option>
            <option value="H6">
              <span i18n:translate="">Heading 6</span>
            </option>
            <option value="PRE" i18n:translate="">
        Formatted
      </option>
          </select>
          <span class="kupu-tb-buttongroup">
            <button type="button" class="kupu-save" id="kupu-save-button" title="save: alt-s" i18n:attributes="title" accesskey="s">&#xA0;</button>
          </span>
          <span class="kupu-tb-buttongroup" id="kupu-bg-basicmarkup">
            <button type="button" class="kupu-bold" id="kupu-bold-button" title="bold: alt-b" i18n:attributes="title" accesskey="b">&#xA0;</button>
            <button type="button" class="kupu-italic" id="kupu-italic-button" title="italic: alt-i" i18n:attributes="title" accesskey="i">&#xA0;</button>
            <button type="button" class="kupu-underline" id="kupu-underline-button" title="underline: alt-u" i18n:attributes="title" accesskey="u">&#xA0;</button>
          </span>
          <span class="kupu-tb-buttongroup" id="kupu-bg-subsuper">
            <button type="button" class="kupu-subscript" id="kupu-subscript-button" title="subscript: alt--" i18n:attributes="title" accesskey="-">&#xA0;</button>
            <button type="button" class="kupu-superscript" id="kupu-superscript-button" title="superscript: alt-+" i18n:attributes="title" accesskey="+">&#xA0;</button>
          </span>
          <span class="kupu-tb-buttongroup">
            <button type="button" class="kupu-forecolor" id="kupu-forecolor-button" title="text color: alt-f" i18n:attributes="title" accesskey="f">&#xA0;</button>
            <button type="button" class="kupu-hilitecolor" id="kupu-hilitecolor-button" title="background color: alt-h" i18n:attributes="title" accesskey="h">&#xA0;</button>
          </span>
          <span class="kupu-tb-buttongroup" id="kupu-bg-justify">
            <button type="button" class="kupu-justifyleft" id="kupu-justifyleft-button" title="left justify: alt-l" i18n:attributes="title" accesskey="l">&#xA0;</button>
            <button type="button" class="kupu-justifycenter" id="kupu-justifycenter-button" title="center justify: alt-c" i18n:attributes="title" accesskey="c">&#xA0;</button>
            <button type="button" class="kupu-justifyright" id="kupu-justifyright-button" title="right justify: alt-r" i18n:attributes="title" accesskey="r">&#xA0;</button>
          </span>
          <span class="kupu-tb-buttongroup" id="kupu-bg-list">
            <button type="button" class="kupu-insertorderedlist" title="numbered list: alt-#" id="kupu-list-ol-addbutton" i18n:attributes="title" accesskey="#">&#xA0;</button>
            <button type="button" class="kupu-insertunorderedlist" title="unordered list: alt-*" id="kupu-list-ul-addbutton" i18n:attributes="title" accesskey="*">&#xA0;</button>
          </span>
          <span class="kupu-tb-buttongroup" id="kupu-bg-definitionlist">
            <button type="button" class="kupu-insertdefinitionlist" title="definition list: alt-=" id="kupu-list-dl-addbutton" i18n:attributes="title" accesskey="=">&#xA0;</button>
          </span>
          <span class="kupu-tb-buttongroup" id="kupu-bg-indent">
            <button type="button" class="kupu-outdent" id="kupu-outdent-button" title="outdent: alt-&lt;" i18n:attributes="title" accesskey="&lt;">&#xA0;</button>
            <button type="button" class="kupu-indent" id="kupu-indent-button" title="indent: alt-&gt;" i18n:attributes="title" accesskey="&gt;">&#xA0;</button>
          </span>
          <span class="kupu-tb-buttongroup">
            <button type="button" class="kupu-image" id="kupu-imagelibdrawer-button" title="image" i18n:attributes="title">&#xA0;</button>
            <button type="button" class="kupu-inthyperlink" id="kupu-linklibdrawer-button" title="internal link" i18n:attributes="title">&#xA0;</button>
            <button type="button" class="kupu-exthyperlink" id="kupu-linkdrawer-button" title="external link" i18n:attributes="title">&#xA0;</button>
            <button type="button" class="kupu-table" id="kupu-tabledrawer-button" title="table" i18n:attributes="title">&#xA0;</button>
          </span>
          <span class="kupu-tb-buttongroup" id="kupu-bg-remove">
            <button type="button" class="kupu-removeimage invisible" id="kupu-removeimage-button" title="Remove image" i18n:attributes="title">&#xA0;</button>
            <button type="button" class="kupu-removelink invisible" id="kupu-removelink-button" title="Remove link" i18n:attributes="title">&#xA0;</button>
          </span>
          <span class="kupu-tb-buttongroup" id="kupu-bg-undo">
            <button type="button" class="kupu-undo" id="kupu-undo-button" title="undo: alt-z" i18n:attributes="title" accesskey="z">&#xA0;</button>
            <button type="button" class="kupu-redo" id="kupu-redo-button" title="redo: alt-y" i18n:attributes="title" accesskey="y">&#xA0;</button>
          </span>
          <span class="kupu-tb-buttongroup kupu-spellchecker-span" id="kupu-spellchecker">
            <button type="button" class="kupu-spellchecker" id="kupu-spellchecker-button" title="check spelling" i18n:attributes="title">&#xA0;</button>
          </span>
          <span class="kupu-tb-buttongroup kupu-source-span" id="kupu-source">
            <button type="button" class="kupu-source" id="kupu-source-button" title="edit HTML code" i18n:attributes="title">&#xA0;</button>
          </span>
        </span>
        <select id="kupu-ulstyles" class="kupu-ulstyles">
          <option value="disc" i18n:translate="list-disc">&#x25CF;</option>
          <option value="square" i18n:translate="list-square">&#x25A0;</option>
          <option value="circle" i18n:translate="list-circle">&#x25CB;</option>
          <option value="none" i18n:translate="list-nobullet">no bullet</option>
        </select>
        <select id="kupu-olstyles" class="kupu-olstyles">
          <option value="decimal" i18n:translate="list-decimal">1</option>
          <option value="upper-roman" i18n:translate="list-upperroman">I</option>
          <option value="lower-roman" i18n:translate="list-lowerroman">i</option>
          <option value="upper-alpha" i18n:translate="list-upperalpha">A</option>
          <option value="lower-alpha" i18n:translate="list-loweralpha">a</option>
        </select>
        <div style="display:block;" class="kupu-librarydrawer-parent">

    </div>
        <div id="kupu-linkdrawer" class="kupu-drawer kupu-linkdrawer">
          <h1 i18n:translate="">External Link</h1>
          <div id="kupu-linkdrawer-addlink" class="kupu-panels kupu-linkdrawer-addlink">
            <table cellspacing="0">
              <tr>
                <td>
                  <div class="kupu-toolbox-label">
                    <span i18n:translate="">
            Link the highlighted text to this URL:
          </span>
                  </div>
                  <input class="kupu-toolbox-st kupu-linkdrawer-input" type="text" onkeypress="return HandleDrawerEnter(event, 'linkdrawer-preview');"/>
                </td>
                <td class="kupu-preview-button">
                  <button class="kupu-dialog-button" type="button" id="linkdrawer-preview" onclick="drawertool.current_drawer.preview()" i18n:translate="">Preview</button>
                </td>
              </tr>
              <tr>
                <td colspan="2" align="center">
                  <iframe frameborder="1" scrolling="auto" class="kupu-linkdrawer-preview" src="kupublank.html">
                  </iframe>
                </td>
              </tr>
            </table>
            <div class="kupu-dialogbuttons">
              <button class="kupu-dialog-button" type="button" onclick="drawertool.current_drawer.save()" i18n:translate="">Ok</button>
              <button class="kupu-dialog-button" type="button" onclick="drawertool.closeDrawer()" i18n:translate="">Cancel</button>
            </div>
          </div>
        </div>
        <div id="kupu-tabledrawer" class="kupu-drawer kupu-tabledrawer">
          <h1 i18n:translate="tabledrawer_title">Table</h1>
          <div class="kupu-panels">
            <table width="300">
              <tr class="kupu-panelsrow">
                <td class="kupu-panel">
                  <div class="kupu-tabledrawer-addtable">
                    <table>
                      <tr>
                        <th i18n:translate="tabledrawer_class_label" class="kupu-toolbox-label">Table Class</th>
                        <td>
                          <select class="kupu-tabledrawer-addclasschooser">
                            <option i18n:translate="" value="ridge">Ridge</option>
                            <option i18n:translate="" value="solid">Solid</option>
                            <option i18n:translate="" value="invert">Invert</option>
                          </select>
                        </td>
                      </tr>
                      <tr>
                        <th i18n:translate="tabledrawer_rows_label" class="kupu-toolbox-label">Rows</th>
                        <td>
                          <input type="text" class="kupu-tabledrawer-newrows" onkeypress="return HandleDrawerEnter(event);"/>
                        </td>
                      </tr>
                      <tr>
                        <th i18n:translate="tabledrawer_columns_label" class="kupu-toolbox-label">Columns</th>
                        <td>
                          <input type="text" class="kupu-tabledrawer-newcols" onkeypress="return HandleDrawerEnter(event);"/>
                        </td>
                      </tr>
                      <tr>
                        <th class="kupu-toolbox-label"> </th>
                        <td>
                          <label>
                            <input class="kupu-tabledrawer-makeheader" type="checkbox" onkeypress="return HandleDrawerEnter(event);"/>
                            <span i18n:translate="tabledrawer_headings_label">Create Headings</span>
                          </label>
                        </td>
                      </tr>
                      <tr>
                        <th class="kupu-toolbox-label"> </th>
                        <td>
                          <button class="kupu-dialog-button" type="button" i18n:translate="tabledrawer_add_table_button" onclick="drawertool.current_drawer.createTable()">Add Table</button>
                          <button class="kupu-dialog-button" type="button" i18n:translate="tabledrawer_fix_tables_button" onclick="drawertool.current_drawer.fixAllTables()">Fix All Tables</button>
                        </td>
                      </tr>
                    </table>
                  </div>
                  <div class="kupu-tabledrawer-edittable">
                    <table>
                      <tr>
                        <th class="kupu-toolbox-label" i18n:translate="tabledrawer_class_label">Table Class</th>
                        <td>
                          <select class="kupu-tabledrawer-editclasschooser" onchange="drawertool.current_drawer.setTableClass(this.options[this.selectedIndex].value)">
                            <option i18n:translate="" value="ridge">Ridge</option>
                            <option i18n:translate="" value="solid">Solid</option>
                            <option i18n:translate="" value="invert">Invert</option>
                          </select>
                        </td>
                      </tr>
                      <tr>
                        <th class="kupu-toolbox-label" i18n:translate="tabledrawer_alignment_label">Current column alignment</th>
                        <td>
                          <select id="kupu-tabledrawer-alignchooser" class="kupu-tabledrawer-alignchooser" onchange="drawertool.current_drawer.tool.setColumnAlign(this.options[this.selectedIndex].value)">
                            <option i18n:translate="tabledrawer_left_option" value="left">Left</option>
                            <option i18n:translate="tabledrawer_center_option" value="center">Center</option>
                            <option i18n:translate="tabledrawer_right_option" value="right">Right</option>
                          </select>
                        </td>
                      </tr>
                      <tr>
                        <th class="kupu-toolbox-label" i18n:translate="tabledrawer_column_label">Column</th>
                        <td>
                          <button class="kupu-dialog-button" type="button" i18n:translate="tabledrawer_add_button" onclick="drawertool.current_drawer.addTableColumn()">Add</button>
                          <button class="kupu-dialog-button" type="button" i18n:translate="tabledrawer_remove_button" onclick="drawertool.current_drawer.delTableColumn()">Remove</button>
                        </td>
                      </tr>
                      <tr>
                        <th class="kupu-toolbox-label" i18n:translate="tabledrawer_row_label">Row</th>
                        <td>
                          <button class="kupu-dialog-button" type="button" i18n:translate="tabledrawer_add_button" onclick="drawertool.current_drawer.addTableRow()">Add</button>
                          <button class="kupu-dialog-button" type="button" i18n:translate="tabledrawer_remove_button" onclick="drawertool.current_drawer.delTableRow()">Remove</button>
                        </td>
                      </tr>
                      <tr>
                        <th class="kupu-toolbox-label" i18n:translate="tabledrawer_fix_table_label">Fix Table</th>
                        <td>
                          <button class="kupu-dialog-button" type="button" i18n:translate="tabledrawer_fix_button" onclick="drawertool.current_drawer.fixTable()">Fix</button>
                        </td>
                      </tr>
                    </table>
                  </div>
                </td>
              </tr>
            </table>
            <div class="kupu-dialogbuttons">
              <button class="kupu-dialog-button" type="button" onfocus="window.status='focus';" onmousedown="window.status ='onmousedown';" i18n:translate="tabledrawer_close_button" onclick="drawertool.closeDrawer(this)">Close</button>
            </div>
          </div>
        </div>
      </div>
      <div class="kupu-toolboxes" id="kupu-toolboxes">
        <div class="kupu-toolbox" id="kupu-toolbox-properties">
          <h1 class="kupu-toolbox-heading" i18n:translate="">Properties</h1>
          <div>
            <div class="kupu-toolbox-label" i18n:translate="">Tittel p&aring; dokumentet:</div>
            <input class="wide" id="kupu-properties-title"/>
            <span class="vortikal-kupu-link"><a target="new_window" href="http://www.usit.uio.no/it/vortex/hjelp/admin/kupu/">Hjelp til redigering</a> med <a target="new_window" href="http://kupu.oscom.org/">Kupu</a>.</span>
            <div class="kupu-toolbox-label" style="display:none" i18n:translate="">Description:</div>
            <textarea class="wide" id="kupu-properties-description"> </textarea>
            <!-- <div style="clear: both; height: 1px" /> -->
          </div>
        </div>
        <div class="kupu-toolbox" id="kupu-toolbox-links">
          <h1 class="kupu-toolbox-heading" i18n:translate="">Links</h1>
          <div id="kupu-toolbox-addlink">
            <div class="kupu-toolbox-label">
              <span i18n:translate="">
            Link the highlighted text to this URL:
          </span>
            </div>
            <input id="kupu-link-input" class="wide" type="text"/>
            <div class="kupu-toolbox-buttons">
              <button type="button" id="kupu-link-button" class="kupu-toolbox-action" i18n:translate="">Make Link</button>
            </div>
          </div>
        </div>
        <div class="kupu-toolbox" id="kupu-toolbox-images">
          <h1 class="kupu-toolbox-heading" i18n:translate="">Images</h1>
          <div>
            <div class="kupu-toolbox-label">
              <span i18n:translate="">Image class:</span>
            </div>
            <select class="wide" id="kupu-image-float-select">
              <option value="image-inline" i18n:translate="">Inline</option>
              <option value="image-left" i18n:translate="">Left</option>
              <option value="image-right" i18n:translate="">Right</option>
            </select>
            <div class="kupu-toolbox-label">
              <span i18n:translate="">Insert image at the following URL:</span>
            </div>
            <input id="kupu-image-input" value="kupuimages/kupu_icon.gif" class="wide" type="text"/>
            <div class="kupu-toolbox-buttons">
              <button type="button" id="kupu-image-addbutton" class="kupu-toolbox-action" i18n:translate="">Insert Image</button>
            </div>
          </div>
        </div>
        <div class="kupu-toolbox" id="kupu-toolbox-tables">
          <h1 class="kupu-toolbox-heading" i18n:translate="">Tables</h1>
          <div>
            <div class="kupu-toolbox-label">
              <span i18n:translate="">Table Class:</span>
              <select class="wide" id="kupu-table-classchooser"> </select>
            </div>
            <div id="kupu-toolbox-addtable" class="kupu-toolbox-addtable">
              <div class="kupu-toolbox-label" i18n:translate="">Rows:</div>
              <input class="wide" type="text" id="kupu-table-newrows"/>
              <div class="kupu-toolbox-label" i18n:translate="">Columns:</div>
              <input class="wide" type="text" id="kupu-table-newcols"/>
              <div class="kupu-toolbox-label">
                <span i18n:translate="">Headings:</span>
                <input name="kupu-table-makeheader" id="kupu-table-makeheader" type="checkbox"/>
                <label for="kupu-table-makeheader" i18n:translate="">Create</label>
              </div>
              <div class="kupu-toolbox-buttons">
                <button type="button" id="kupu-table-fixall-button" i18n:translate="">Fix Table</button>
                <button type="button" id="kupu-table-addtable-button" i18n:translate="">Add Table</button>
              </div>
            </div>
            <div id="kupu-toolbox-edittable" class="kupu-toolbox-edittable">
              <div class="kupu-toolbox-label">
                <span i18n:translate="">Col Align:</span>
                <select class="wide" id="kupu-table-alignchooser">
                  <option value="left" i18n:translate="">Left</option>
                  <option value="center" i18n:translate="">Center</option>
                  <option value="right" i18n:translate="">Right</option>
                </select>
              </div>
              <div class="kupu-toolbox-buttons">
                <br/>
                <button type="button" id="kupu-table-addcolumn-button" i18n:translate="">Add Column</button>
                <button type="button" id="kupu-table-delcolumn-button" i18n:translate="">Remove Column</button>
                <br/>
                <button type="button" id="kupu-table-addrow-button" i18n:translate="">Add Row</button>
                <button type="button" id="kupu-table-delrow-button" i18n:translate="">Remove Row</button>
                <button type="button" id="kupu-table-fix-button" i18n:translate="">Fix</button>
              </div>
            </div>
          </div>
        </div>
        <div class="kupu-toolbox" id="kupu-toolbox-cleanupexpressions">
          <h1 class="kupu-toolbox-heading" i18n:translate="">Cleanup expressions</h1>
          <div>
            <div class="kupu-toolbox-label">
              <span i18n:translate="">
            Select a cleanup action:
          </span>
            </div>
            <select id="kupucleanupexpressionselect" class="kupu-toolbox-st">
        </select>
            <div style="text-align: center">
              <button type="button" id="kupucleanupexpressionbutton" class="kupu-toolbox-action">Perform action</button>
            </div>
          </div>
        </div>
        <div class="kupu-toolbox" id="kupu-toolbox-debug">
          <h1 class="kupu-toolbox-heading" i18n:translate="">Debug Log</h1>
          <div id="kupu-toolbox-debuglog" class="kupu-toolbox-label">
      </div>
        </div>
      </div>
      <table id="kupu-colorchooser" cellpadding="0" cellspacing="0" style="position: fixed; border-style: solid; border-color: black; border-width: 1px;">
    </table>
      <div class="kupu-editorframe">
        <form>
          <iframe name="kupu-editor" id="kupu-editor" class="kupu-editor-iframe" frameborder="0" src="${kupuSrc.srcURL?html}" scrolling="yes">
          </iframe>
          <textarea class="kupu-editor-textarea" id="kupu-editor-textarea"> </textarea>
        </form>
      </div>
    </div>
  </body>
</html>

