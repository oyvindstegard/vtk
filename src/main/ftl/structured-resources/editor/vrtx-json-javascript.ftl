<#-- 
  Evil hack(s) alert! 
-->
<#macro script >
  <#assign locale = springMacroRequestContext.getLocale() />
  <script language="Javascript" type="text/javascript"> <!-- 
   
  LIST_OF_JSON_ELEMENTS = new Array();
  $(document).ready(function() {
  
  <#assign i = 0 />
  <#list form.elements as elementBox>
    <#assign j = 0 />
    <#list elementBox.formElements as elem>
      <#if elem.description.type == "json" && elem.description.isMultiple() >
      LIST_OF_JSON_ELEMENTS[${i}] = new Object();
      LIST_OF_JSON_ELEMENTS[${i}].name = "${elem.name}";
      LIST_OF_JSON_ELEMENTS[${i}].type = "${elem.description.type}";
      LIST_OF_JSON_ELEMENTS[${i}].a = new Array();

    <#list elem.description.attributes as jsonAttr>
      LIST_OF_JSON_ELEMENTS[${i}].a[${j}] = new Object();
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
      var valuemap = new Array();
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
    var LIST_OF_JSON_ELEMENTS_LENGTH = LIST_OF_JSON_ELEMENTS.length;
    for (var i = 0; i < LIST_OF_JSON_ELEMENTS_LENGTH; i++) {
        $("#" + LIST_OF_JSON_ELEMENTS[i].name).append("<input type=\"button\" class=\"vrtx-add-button\" onClick=\"addNewJsonElement(LIST_OF_JSON_ELEMENTS[" + i + "],this)\" value=\"${vrtx.getMsg("editor.add")}\" />");
      }
    });
    
    
  function addNewJsonElement(j,button) {
  
     var counter = parseInt($(button).prev(".vrtx-json-element").find("input.id").val())+1;     
     if(isNaN(counter)){
     	counter = 0;
     }
        
     // Add opp og ned knapp...blah
     
     var htmlTemplate = "";
     var arrayOfIds = new Array();
   
     for (i in j.a) {
         var inputFieldName = j.name + "." + j.a[i].name + "." + counter;         
         arrayOfIds[i] = new String(j.name + "." + j.a[i].name + ".").replace(/\./g, "\\.");
         
         switch(j.a[i].type) {
           case "string":
             if (j.a[i].dropdown && j.a[i].valuemap) {
               htmlTemplate += addDropdown(j.a[i], inputFieldName); break;
             } else {
               htmlTemplate += addStringField(j.a[i], inputFieldName); break
             }
           case "html":
             htmlTemplate += addHtmlField(j.a[i], inputFieldName); break
           case "simple_html":
             htmlTemplate += addHtmlField(j.a[i], inputFieldName); break
           case "boolean":
             htmlTemplate += addBooleanField(j.a[i], inputFieldName); break
           case "image_ref":
             htmlTemplate += addImageRef(j.a[i], inputFieldName); break
           case "datetime":
             htmlTemplate += addDateField(j.a[i], inputFieldName); break
           case "media":
             htmlTemplate += addMediaRef(j.a[i], inputFieldName); break
           default:
             htmlTemplate += ""; break
         }
     }

     var moveDownButton = "<input type=\"button\" class=\"vrtx-move-down-button\" value=\"&darr; ${vrtx.getMsg("editor.move-down")}\" />";    
     var moveUpButton = "<input type=\"button\" class=\"vrtx-move-up-button\" value=\"&uarr; ${vrtx.getMsg("editor.move-up")}\" />";
     var deleteButton = "<input type=\"button\" class=\"vrtx-remove-button\" value=\"${vrtx.getMsg("editor.remove")}\" \/>";
   	 var id = "<input type=\"hidden\" class=\"id\" value=\"" + counter +"\" />";
     
     var newElementId = "vrtx-json-element-" + j.name + "-" + counter; 
     $("#" + j.name +" .vrtx-add-button").before("<div class=\"vrtx-json-element\" id=\"" + newElementId +  "\"><\/div>");
     
     var newElement = $("#" + newElementId);
     
     newElement.append(htmlTemplate);  
     newElement.append(id);
     
     if (counter > 0 && newElement.prev(".vrtx-json-element").length){
       newElement.prev(".vrtx-json-element").append(moveDownButton);
     }
     
	 newElement.append(deleteButton);
     
     if (counter > 0){
        newElement.append(moveUpButton);
     }
     
     newElement.find(".vrtx-remove-button").click(
     	function(){
     		removeNode( j.name, counter ,  arrayOfIds );
     	}
     );
     
     newElement.find(".vrtx-move-up-button").click(
    	function(){
     		swapContent(counter, arrayOfIds, -1);
     	}
     );
     
     if(newElement.prev(".vrtx-json-element").length){
	     newElement.prev(".vrtx-json-element").find(".vrtx-move-down-button").click(
	     	function(){
	     		swapContent(counter-1, arrayOfIds, 1);
	     	}
	     );
     }
     
     // Fck.........
     for (i in j.a) {
       var inputFieldName = j.name + "." + j.a[i].name + "." + counter;
       if (j.a[i].type == "simple_html") {
         newEditor(inputFieldName, false, false, '${resourceContext.parentURI?js_string}', '${fckeditorBase.url?html}', '${fckeditorBase.documentURL?html}', 
          '${fckBrowse.url.pathRepresentation}', '<@vrtx.requestLanguage />', '');
       } else if (j.a[i].type == "html") {
         newEditor(inputFieldName, true, false, '${resourceContext.parentURI?js_string}', '${fckeditorBase.url?html}', '${fckeditorBase.documentURL?html}', 
          '${fckBrowse.url.pathRepresentation}', '<@vrtx.requestLanguage />', '');
       } else if (j.a[i].type == "datetime") {
         displayDateAsMultipleInputFields(inputFieldName);
     }
     
    }
  }
  
  function removeNode(name,counter,arrayOfIds){
    var removeElementId = '#vrtx-json-element-' + name + '-' + counter;
    var removeElement = $(removeElementId);
  
	var siblingElement;
	if(removeElement.prev(".vrtx-json-element").length){
		siblingElement = removeElement.prev(".vrtx-json-element");	
	}else if(removeElement.next(".vrtx-json-element").length){
		siblingElement = removeElement.next(".vrtx-json-element");
	}
    $(removeElementId).remove(); 	
	removeUnwantedButtons(siblingElement);
  }
 
  
  function removeUnwantedButtons(siblingElement){
  	var e = siblingElement.parents(".vrtx-json").find(".vrtx-json-element");
  	while(e.prev(".vrtx-json-element").length){
  		e = e.prev(".vrtx-json-element");
  	}
  	e.find(".vrtx-move-up-button").remove();
  	
  	while(e.next(".vrtx-json-element").length){
  		e = e.next(".vrtx-json-element");
  	}
  	e.find(".vrtx-move-down-button").remove();
  }
  
  function addDropdown(elem, inputFieldName) {
    var htmlTemplate = new String();
    var classes = "vrtx-string" + " " + elem.name;
    htmlTemplate = '<div class=\"' + classes + '\">';
    htmlTemplate += '<label for=\"' + inputFieldName + '\">' + elem.title + '<\/label>';
    htmlTemplate += '<div class=\"inputfield\">';
    htmlTemplate += '<select id=\"' + inputFieldName + '\" name=\"' + inputFieldName + '\">';
    for (i in elem.valuemap) {
      var keyValuePair = elem.valuemap[i];
      var key = keyValuePair.split("$")[0];
      var value = keyValuePair.split("$")[1];
      htmlTemplate += '<option value=\"' + key + '\">' + value + '<\/option>';
    }
    htmlTemplate += '<\/select>';
    htmlTemplate +=  '<\/div>';
    htmlTemplate +=  '<div class=\"tooltip\"><\/div>';
    htmlTemplate +=  '<\/div>';
    
    return htmlTemplate;
  }

  function addStringField(elem, inputFieldName) {
    var htmlTemplate = new String();
    var classes = "vrtx-string" + " " + elem.name;
    htmlTemplate = '<div class=\"' + classes + '\">';
    htmlTemplate += '<label for=\"' + inputFieldName + '\">' + elem.title + '<\/label>';
    htmlTemplate += '<div class=\"inputfield\">';
    htmlTemplate += '<input size=\"40\" type=\"text\" name=\"' + inputFieldName + '\" id=\"' + inputFieldName + '\" />';
    htmlTemplate +=  '<\/div>';
    htmlTemplate +=  '<div class=\"tooltip\"><\/div>';
    htmlTemplate +=  '<\/div>';
    
    return htmlTemplate;
  }
  
  function addHtmlField(elem, inputFieldName) {
    var htmlTemplate = new String();
    var baseclass = "vrtx-html";
    if (elem.type == "simple_html") {
      baseclass = "vrtx-simple-html";
    }
    var classes = baseclass + " " + elem.name;
    htmlTemplate = '<div class=\"' + classes + '\">';
    htmlTemplate += '<label for=\"' + inputFieldName + '\">' + elem.title + '<\/label>';
    htmlTemplate += '<div>';
    htmlTemplate += '<textarea name=\"' + inputFieldName + '\" id=\"' + inputFieldName + '\" ';
    htmlTemplate += ' rows=\"4\" cols=\"20\" ><\/textarea>';
    htmlTemplate += '<\/div><\/div>';
    
    return htmlTemplate;
  }
  
  function addBooleanField(elem, inputFieldName) {
    var htmlTemplate = new String();
    htmlTemplate = '<div class=\"vrtx-radio\">';
    htmlTemplate += '<div><label>elem.title<\/label><\/div>';
    htmlTemplate += '<div>';
    htmlTemplate += '<input name=\"' + inputFieldName + '\" id=\"' + inputFieldName + '-true\" type=\"radio\" value=\"true\" \/>';
    htmlTemplate += '<label for=\"' + inputFieldName + '-true\">True<\/label>';
    htmlTemplate += '<input name=\"' + inputFieldName + '\" id=\"' + inputFieldName + '-false\" type=\"radio\" value=\"false\" \/>';
    htmlTemplate +=  '<label for=\"' + inputFieldName + '-false\">False<\/label>';
    htmlTemplate += '<\/div><\/div>';
      
    return htmlTemplate;
  }
  
  function addImageRef(elem, inputFieldName) {
    var htmlTemplate = new String();
    htmlTemplate = '<div class=\"vrtx-image-ref\">';
    htmlTemplate += '<div>';
    htmlTemplate += '<label for=\"' + inputFieldName+ '\">' + elem.title + '<\/label>';
    htmlTemplate += '<\/div><div>';
    htmlTemplate += '<input type=\"text\" id=\"' + inputFieldName+ '\" name=\"' + inputFieldName + '\" value=\"\" onblur=\"previewImage($(this).parent().find(\'input\').attr(\'id\'));\" size=\"30\" \/>';
    htmlTemplate += '<button type=\"button\" onclick=\"browseServer($(this).parent().find(\'input\').attr(\'id\'), \'${fckeditorBase.url}\', \'${resourceContext.parentURI?js_string}\', \'${fckBrowse.url.pathRepresentation}\');\"><@vrtx.msg code="editor.browseImages" /><\/button>';
    htmlTemplate += '<\/div>';
    htmlTemplate += '<div id=\"' + inputFieldName + '.preview\">';
    htmlTemplate += '<\/div><\/div>';
    
    return htmlTemplate;
  }
  
  function addDateField(elem, inputFieldName) {
    var htmlTemplate = new String();
    htmlTemplate = '<div class=\"vrtx-string date\">';
    htmlTemplate += '<label for=\"' + inputFieldName + '\">' + elem.title + '<\/label>';
    htmlTemplate += '<div class=\"inputfield\">';
    htmlTemplate += '<input size=\"20\" type=\"text\" name=\"' + inputFieldName + '\" id=\"' + inputFieldName + '\" value=\"\" class=\"date\" \/>';
    htmlTemplate += '<\/div>';
    htmlTemplate += '<div class=\"tooltip\"><\/div>';
    htmlTemplate += '<\/div>';
    
    return htmlTemplate;
  }

  function addMediaRef(elem, inputFieldName) {
    var htmlTemplate = new String();
    htmlTemplate = '<div class=\"vrtx-media-ref\">';
    htmlTemplate += '<div><label for=\"' + inputFieldName + '\">' + elem.title + '<\/label>';
    htmlTemplate += '<\/div><div>';
    htmlTemplate += '<input type=\"text\" id=\"' + inputFieldName + '\" name=\"' + inputFieldName + '\" value=\"\" onblur=\"previewImage($(this).parent().find(\'input\').attr(\'id\'));\" size=\"30\"\/>';
    htmlTemplate += '<button type=\"button\" onclick=\"browseServer($(this).parent().find(\'input\').attr(\'id\'), \'${fckeditorBase.url}\', \'${resourceContext.parentURI?js_string}\', \'${fckBrowse.url.pathRepresentation}\', \'Media\');\"><@vrtx.msg code="editor.browseImages" /><\/button>';
    htmlTemplate += '<\/div><\/div>'
    
    return htmlTemplate;
  }

  function getFckValue(instanceName) {
    var oEditor = FCKeditorAPI.GetInstance(instanceName);
    return oEditor.GetXHTML(true);
  }
  
  function setFckValue(instanceName, data) {
    var oEditor = FCKeditorAPI.GetInstance(instanceName);
    oEditor.SetData(data);
  }
   
  function isFckEditor(instanceName) {
    var oEditor = FCKeditorAPI.GetInstance(instanceName);
    return oEditor != null;
  }
  
  function swapContent(counter, arrayOfIds, move) {
     
    var arrayOfIdsLength = arrayOfIds.length;
    for (var x = 0; x < arrayOfIdsLength; x++) {
      var elementId1 = '#' + arrayOfIds[x] + counter;
      
      var moveToId;
      if(move > 0){  
      	moveToId = parseInt( $(elementId1).parents(".vrtx-json-element").next(".vrtx-json-element").find("input.id").val() );
      } else {
	moveToId = parseInt( $(elementId1).parents(".vrtx-json-element").prev(".vrtx-json-element").find("input.id").val() );
      }
	         
      var elementId2 = '#' + arrayOfIds[x] + moveToId;
     
      /* We need to handle special cases like date and fck fields  */
      var fckInstanceName1 = arrayOfIds[x].replace(/\\/g, '') + counter;
      var fckInstanceName2 = arrayOfIds[x].replace(/\\/g, '') + moveToId;
      if (isFckEditor(fckInstanceName1) && isFckEditor(fckInstanceName2)) {
        var val1 = getFckValue(fckInstanceName1);
        var val2 = getFckValue(fckInstanceName2);
        setFckValue(fckInstanceName1, val2);
        setFckValue(fckInstanceName2, val1);
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

      element1.blur();
      element2.blur();
      element1.change();
      element2.change();
    }
  }
  // -->
  </script>
</#macro>
