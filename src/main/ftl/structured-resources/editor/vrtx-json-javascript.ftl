<#ftl strip_whitespace=true>
<#-- JSON elements interaction in new documenttypes (add, remove and move) -->

<#macro script>
  <#assign locale = springMacroRequestContext.getLocale() />
  <script type="text/javascript" src="${webResources?html}/jquery/plugins/jquery.scrollTo-1.4.2-min.js"></script>

  <script type="text/javascript"><!--
   
    var TEMPLATES = [];
    var LIST_OF_JSON_ELEMENTS = [];

    $(document).ready(function() {

      // Retrieve HTML templates
      var templatesRetrieved = $.Deferred();
      TEMPLATES = vrtxAdmin.retrieveHTMLTemplates("templates",
                                                  ["string", "html", "radio", "dropdown", "date", "browse", "add-remove-move"],
                                                   templatesRetrieved);

      // Build JSON elements
      var jsonElementsBuilt = $.Deferred();
      <#assign i = 0 />
      <#list form.elements as elementBox>
        <#assign j = 0 />
        <#list elementBox.formElements as elem>
          <#if elem.description.type == "json" && elem.description.isMultiple()>
            LIST_OF_JSON_ELEMENTS[${i}] = {};
            LIST_OF_JSON_ELEMENTS[${i}].name = "${elem.name}";
            LIST_OF_JSON_ELEMENTS[${i}].type = "${elem.description.type}";

            LIST_OF_JSON_ELEMENTS[${i}].a = [];
            <#list elem.description.attributes as jsonAttr>
              LIST_OF_JSON_ELEMENTS[${i}].a[${j}] = {};
              LIST_OF_JSON_ELEMENTS[${i}].a[${j}].name = "${jsonAttr.name}";
              LIST_OF_JSON_ELEMENTS[${i}].a[${j}].type = "${jsonAttr.type}";
              <#if jsonAttr.edithints?exists>
                <#if jsonAttr.edithints['dropdown']?exists>
                  LIST_OF_JSON_ELEMENTS[${i}].a[${j}].dropdown = true;
                </#if>
              </#if>
              <#if jsonAttr.getValuemap(locale)?exists >
                <#assign valuemap = jsonAttr.getValuemap(locale) />
                <#assign k = 0 />
                var valuemap = [];
                <#list valuemap?keys as key>
                  <#assign optionKey = key />
                  <#if optionKey = '""' >
                    <#assign optionKey = "''" />
                  </#if>
                  valuemap[${k}] = "${optionKey}$${valuemap[key]}";
                  <#assign k = k + 1 />
                </#list>
                LIST_OF_JSON_ELEMENTS[${i}].a[${j}].valuemap = valuemap;
              </#if>
              LIST_OF_JSON_ELEMENTS[${i}].a[${j}].title = "${form.resource.getLocalizedMsg(jsonAttr.name, locale, null)}";
              <#assign j = j + 1 />
            </#list>
            <#assign i = i + 1 />
          </#if>
        </#list>
      </#list>

      jsonElementsBuilt.resolve();

      $.when(templatesRetrieved, jsonElementsBuilt).done(function() {
        for (var i = 0, len = LIST_OF_JSON_ELEMENTS.length; i < len; i++) {
          var json = { clazz: "add", buttonText: '${vrtx.getMsg("editor.add")}' }
          $("#" + LIST_OF_JSON_ELEMENTS[i].name)
            .append($.mustache(TEMPLATES["add-remove-move"], json))
            .find(".vrtx-add-button input").data({'number': i});
        }
      });
      
      // TODO: avoid this being hardcoded here
      var items = $("#editor.vrtx-syllabus #items");
      wrapJSONItemsLeftRight(items, ".author, .title, .year, .publisher, .isbn, .comment", ".linktext, .link, .bibsys, .fulltext, .articles");
      accordionHeaderListener(items, ".author input, .title input");
      // ^ TODO: avoid this being hardcoded here
      
      // Because accordion needs one content wrapper
      for(var grouped = $(".vrtx-json-accordion .vrtx-json-element"), i = grouped.length; i--;) { 
        var group = $(grouped[i]);
        group.find("> *").wrapAll("<div />");
        updateHeader(group);
      }
      
      $(".vrtx-json-accordion .fieldset").accordion({ 
                                            header: "> div > .header",
                                            autoHeight: false,
                                            collapsible: true,
                                            active: false
                                          });
                                          
       

     $("#app-content").on("click", ".vrtx-json .vrtx-add-button input", function(e) {
        var accordionWrapper = $(this).closest(".vrtx-json-accordion");
        var hasAccordion = accordionWrapper.length;
        
        addNewJsonElement(this, hasAccordion);
        
        if(hasAccordion ) {
          var accordionContent = accordionWrapper.find(".fieldset");

          var group = accordionContent.find(".vrtx-json-element:last");
          group.find("> *").wrapAll("<div />");
          group.prepend('<div class="header">' + (vrtxAdmin.lang !== "en" ? "Inget innhold" : "No content") + '</div>');
          
          // TODO: avoid this being hardcoded here
          wrapJSONItemsLeftRight(group, ".author, .title, .year, .publisher, .isbn, .comment", ".linktext, .link, .bibsys, .fulltext, .articles");
          accordionHeaderListener(items, ".author input, .title input");
          // ^ TODO: avoid this being hardcoded here
          
          accordionRefresh(accordionContent);
        }
        e.stopPropagation();
        e.preventDefault();
      });
      
    });
    
    function wrapJSONItemsLeftRight(items, leftItems, rightItems) {
      if(items.length) {
        items.find(leftItems).wrapAll("<div class='left' />");
        items.find(rightItems).wrapAll("<div class='right' />");
      }
    }
    
    function accordionHeaderListener(items, fieldSelector) {
      var elm = items.find(fieldSelector);
      if(elm.length) {
        elm.addClass("header-listener");
        elm.off("keyup").on("keyup", function() {
          updateHeader($(this));
        });
      }
    }
    
    function updateHeader(elem) {
      var str = "";
      var jsonElm = elem.closest(".vrtx-json-element");
      var fields = jsonElm.find(".header-listener");
      if(fields.length) {
        for(var i = 0, len = fields.length, useDelimiter = (len > 1); i < len; i++) {
          if(useDelimiter && i < (len - 1)) {
            str += $(fields[i]).val() + ", ";
          } else {
            str += $(fields[i]).val();
          }
        }
        if(str === ", " || str === "") {
          str = (vrtxAdmin.lang !== "en") ? "Inget innhold" : "No content";
        }
        var header = jsonElm.find("> .header");
        if(!header.length) {
          jsonElm.prepend('<div class="header">' + str + '</div>');
        } else {
          header.html('<span class="ui-icon ui-icon-triangle-1-s"></span>' + str);
        }
      }
    }
    
    function accordionRefresh(elem) {
      elem.accordion('destroy').accordion({ 
                                  header: "> div > .header",
                                  autoHeight: false,
                                  collapsible: true,
                                  active: false
                                });
    }

    function addNewJsonElement(button, hasAccordion) {
      var j = LIST_OF_JSON_ELEMENTS[parseInt($(button).data('number'))];
      var counter = $(button).closest(".fieldset").find(".vrtx-json-element").length;

      var htmlTemplate = "";
      var arrayOfIds = [];

      // Add correct HTML for vrtx-type

      for (i in j.a) {
        var inputFieldName = j.name + "." + j.a[i].name + "." + counter;
        arrayOfIds[i] = new String(j.name + "." + j.a[i].name + ".").replace(/\./g, "\\.");
        switch (j.a[i].type) {
          case "string":
            if (j.a[i].dropdown && j.a[i].valuemap) {
              htmlTemplate += addDropdown(j.a[i], inputFieldName);
              break;
            } else {
              htmlTemplate += addStringField(j.a[i], inputFieldName);
              break
            }
          case "html":
            htmlTemplate += addHtmlField(j.a[i], inputFieldName);
            break
          case "simple_html":
            htmlTemplate += addHtmlField(j.a[i], inputFieldName);
            break
          case "boolean":
            htmlTemplate += addBooleanField(j.a[i], inputFieldName);
            break
          case "image_ref":
            htmlTemplate += addImageRef(j.a[i], inputFieldName);
            break
          case "resource_ref":
            htmlTemplate += addResourceRef(j.a[i], inputFieldName);
            break
          case "datetime":
            htmlTemplate += addDateField(j.a[i], inputFieldName);
            break
          case "media":
            htmlTemplate += addMediaRef(j.a[i], inputFieldName);
            break
          default:
            htmlTemplate += "";
            break
        }
      }
      
      // Move up, move down, remove
      
      var jsonParent = $(button).closest(".vrtx-json");
      var isImmovable = jsonParent && jsonParent.hasClass("vrtx-multiple-immovable");

      if(!isImmovable) {
        var moveDownButton = $.mustache(TEMPLATES["add-remove-move"], { clazz: 'move-down', buttonText: '&darr; ${vrtx.getMsg("editor.move-down")}' });
        var moveUpButton = $.mustache(TEMPLATES["add-remove-move"],   { clazz: 'move-up',   buttonText: '&uarr; ${vrtx.getMsg("editor.move-up")}'   });
      }
      var removeButton = $.mustache(TEMPLATES["add-remove-move"],   { clazz: 'remove',    buttonText: '${vrtx.getMsg("editor.remove")}'           });
      
      var id = "<input type=\"hidden\" class=\"id\" value=\"" + counter + "\" \/>";
      var newElementId = "vrtx-json-element-" + j.name + "-" + counter;
    
      $("#" + j.name + " .vrtx-add-button").before("<div class=\"vrtx-json-element\" id=\"" + newElementId + "\"><\/div>");
    
      var newElement = $("#" + newElementId);
      newElement.append(htmlTemplate);
      newElement.append(id);
    
      if (!isImmovable && counter > 0 && newElement.prev(".vrtx-json-element").length) {
        if(hasAccordion) {
          newElement.prev(".vrtx-json-element").find("> div.ui-accordion-content").append(moveDownButton);
        } else {
          newElement.prev(".vrtx-json-element").append(moveDownButton);
        }
      }
      
      newElement.append(removeButton);
    
      if (!isImmovable && counter > 0) {
        newElement.append(moveUpButton);
      }
      
      newElement.find(".vrtx-remove-button").click(function () {
        removeNode(j.name, counter, hasAccordion);
      });
      
      if(!isImmovable) {
        newElement.find(".vrtx-move-up-button").click(function () {
          swapContent(counter, arrayOfIds, -1, j.name);
        });

        if (newElement.prev(".vrtx-json-element").length) {
          newElement.prev(".vrtx-json-element").find(".vrtx-move-down-button").click(function () {
            swapContent(counter-1, arrayOfIds, 1, j.name);
          });
        }
      
      }

      // CK and date inputfields

      for (i in j.a) {
        var inputFieldName = j.name + "." + j.a[i].name + "." + counter;
        if (j.a[i].type == "simple_html") {
          newEditor(inputFieldName, false, false, '${resourceContext.parentURI?js_string}', '${fckeditorBase.url?html}', 
                                                  '${fckeditorBase.documentURL?html}', '${fckBrowse.url.pathRepresentation}', '<@vrtx.requestLanguage />', "");
        } else if (j.a[i].type == "html") {
          newEditor(inputFieldName, true, false, '${resourceContext.parentURI?js_string}', '${fckeditorBase.url?html}', 
                                                 '${fckeditorBase.documentURL?html}', '${fckBrowse.url.pathRepresentation}', '<@vrtx.requestLanguage />', "");
        } else if (j.a[i].type == "datetime") {
          displayDateAsMultipleInputFields(inputFieldName);
        }
      }
    }
    
    function removeNode(name, counter, hasAccordion) {
      var removeElementId = '#vrtx-json-element-' + name + '-' + counter;
      var removeElement = $(removeElementId);
      var removeElementParent = removeElement.parent();
      var textAreas = removeElement.find("textarea");
      var i = textAreas.length;
      while(i--) {
        var textAreaName = textAreas[i].name;
        if (isCkEditor(textAreaName)) {
          var ckInstance = getCkInstance(textAreaName);
          ckInstance.destroy();
          delete ckInstance;
        }
      }
      removeElement.remove();
      removeElementParent.find(".vrtx-json-element:first .vrtx-move-up-button").remove();
      removeElementParent.find(".vrtx-json-element:last .vrtx-move-down-button").remove();
      if(hasAccordion) {
        accordionRefresh(removeElementParent.closest(".fieldset"));
      }
    }

    function addStringField(elem, inputFieldName) {
      var json = { classes: "vrtx-string" + " " + elem.name,
                   elemTitle: elem.title,
                   inputFieldName: inputFieldName }
      return $.mustache(TEMPLATES["string"], json); 
    }
    
    function addHtmlField(elem, inputFieldName) {
      var baseclass = "vrtx-html";
      if (elem.type == "simple_html") {
        baseclass = "vrtx-simple-html";
      }
      var json = { classes: baseclass + " " + elem.name,
                   elemTitle: elem.title,
                   inputFieldName: inputFieldName }
      return $.mustache(TEMPLATES["html"], json); 
    }
    
    function addBooleanField(elem, inputFieldName) {
      var json = { elemTitle: elem.title,
                   inputFieldName: inputFieldName }
      return $.mustache(TEMPLATES["radio"], json); 
    }

    function addDropdown(elem, inputFieldName) {
      var htmlOpts = [];
      for (i in elem.valuemap) {
        var keyValuePair = elem.valuemap[i];
        var key = keyValuePair.split("$")[0];
        var value = keyValuePair.split("$")[1];
        htmlOpts.push({key: key, value: value});
      }
      var json = { classes: "vrtx-string" + " " + elem.name,
                   elemTitle: elem.title,
                   inputFieldName: inputFieldName,
                   options: htmlOpts }
      return $.mustache(TEMPLATES["dropdown"], json);  
    }

    function addDateField(elem, inputFieldName) {
      var json = { elemTitle: elem.title,
                   inputFieldName: inputFieldName }
      return $.mustache(TEMPLATES["date"], json); 
    }
    
    function addImageRef(elem, inputFieldName) {
      var json = { clazz: 'vrtx-image-ref',
                   elemTitle: elem.title,
                   inputFieldName: inputFieldName,
                   fckEditorBaseUrl: '${fckeditorBase.url}',
                   parentURI: '${resourceContext.parentURI?js_string}',
                   fckBrowsePath: '${fckBrowse.url.pathRepresentation}',
                   browseButtonText: '<@vrtx.msg code="editor.browseImages" />',
                   type: '',
                   size: 30,
                   onBlur: "previewImage('" + inputFieldName + "');",
                   preview: "<div id='" + inputFieldName + ".preview'></div>" }
      return $.mustache(TEMPLATES["browse"], json); 
    }
    
    function addResourceRef(elem, inputFieldName) {
      var json = { clazz: 'vrtx-resource-ref',
                   elemTitle: elem.title,
                   inputFieldName: inputFieldName,
                   fckEditorBaseUrl: '${fckeditorBase.url}',
                   parentURI: '${resourceContext.parentURI?js_string}',
                   fckBrowsePath: '${fckBrowse.url.pathRepresentation}',
                   browseButtonText: '<@vrtx.msg code="editor.browseImages" />',
                   type: 'File',
                   size: 40,
                   onBlur: "",
                   preview: "" }
      return $.mustache(TEMPLATES["browse"], json); 
    }
    
    function addMediaRef(elem, inputFieldName) {
      var json = { clazz: 'vrtx-media-ref',
                   elemTitle: elem.title,
                   inputFieldName: inputFieldName,
                   fckEditorBaseUrl: '${fckeditorBase.url}',
                   parentURI: '${resourceContext.parentURI?js_string}',
                   fckBrowsePath: '${fckBrowse.url.pathRepresentation}',
                   browseButtonText: '<@vrtx.msg code="editor.browseImages" />',
                   type: 'Media',
                   size: 30,
                   onBlur: "",
                   preview: "" }      
      return $.mustache(TEMPLATES["browse"], json); 
    }
    
    // When move up or move down (+ scroll to)
    
    function swapContent(counter, arrayOfIds, move, name) {
      var thisId = "#vrtx-json-element-" + name + "-" + counter;
      var movedId = "#";
      if (move > 0) {
        movedId += $(thisId).next(".vrtx-json-element").attr("id");
      } else {
        movedId += $(thisId).prev(".vrtx-json-element").attr("id");
      }
      var arrayOfIdsLength = arrayOfIds.length;
      for (var x = 0; x < arrayOfIdsLength; x++) {
        var elementId1 = '#' + arrayOfIds[x] + counter;
        var moveToId;
        if (move > 0) {
          moveToId = parseInt($(elementId1).parents(".vrtx-json-element").next(".vrtx-json-element").find("input.id").val());
        } else {
          moveToId = parseInt($(elementId1).parents(".vrtx-json-element").prev(".vrtx-json-element").find("input.id").val());
        }
        var elementId2 = '#' + arrayOfIds[x] + moveToId;
        
        /* We need to handle special cases like date and CK fields  */
        var ckInstanceName1 = arrayOfIds[x].replace(/\\/g, '') + counter;
        var ckInstanceName2 = arrayOfIds[x].replace(/\\/g, '') + moveToId;
        if (isCkEditor(ckInstanceName1) && isCkEditor(ckInstanceName2)) {
          var val1 = getCkValue(ckInstanceName1);
          var val2 = getCkValue(ckInstanceName2);
          setCkValue(ckInstanceName1, val2);
          setCkValue(ckInstanceName2, val1);
        } else if ($(elementId1).hasClass("date") && $(elementId2).hasClass("date")) {
          var date1 = $(elementId1 + '-date');
          var hours1 = $(elementId1 + '-hours');
          var minutes1 = $(elementId1 + '-minutes');
          var date2 = $(elementId2 + '-date');
          var hours2 = $(elementId2 + '-hours');
          var minutes2 = $(elementId2 + '-minutes');
          var dateVal1 = date1.val();
          var hoursVal1 = hours1.val();
          var minutesVal1 = minutes1.val();
          var dateVal2 = date2.val();
          var hoursVal2 = hours2.val();
          var minutesVal2 = minutes2.val();
          date1.val(dateVal2);
          hours1.val(hoursVal2);
          minutes1.val(minutesVal2);
          date2.val(dateVal1);
          hours2.val(hoursVal1);
          minutes2.val(minutesVal1);
        }    
        var element1 = $(elementId1);
        var element2 = $(elementId2);
        var val1 = element1.val();
        var val2 = element2.val();
        element1.val(val2);
        element2.val(val1);
        updateHeader(element1);
        updateHeader(element2);
        element1.blur();
        element2.blur();
        element1.change();
        element2.change();
      }
      element1.closest(".vrtx-json-element").focusout();
      element2.closest(".vrtx-json-element").focusout();
      
      var absPos = $(movedId).offset();
      var absPosTop = absPos.top;
      var stickyBar = $("#vrtx-editor-title-submit-buttons");
      if(stickyBar.css("position") == "fixed") {
        var stickyBarHeight = stickyBar.height();
        absPosTop -= (stickyBarHeight <= absPosTop) ? stickyBarHeight : 0;
      }

      $('body').scrollTo(absPosTop, 250, {
        easing: 'swing',
        queue: true,
        axis: 'y'
      });
    }
  
  // -->
  </script>
</#macro>
