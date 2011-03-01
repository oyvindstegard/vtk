var MULTIPLE_INPUT_FIELD_NAMES = new Array();
var COUNTER_FOR_MULTIPLE_INPUT_FIELD = new Array();
var LENGTH_FOR_MULTIPLE_INPUT_FIELD = new Array();

var debugMultipleInputFields = false;

function loadMultipleInputFields(name, addName, removeName, moveUpName, moveDownName, browseName) {
    var id = "#" + name;
    if ($(id).val() == null) { return; }
    var formFields = $(id).val().split(",");
    
    COUNTER_FOR_MULTIPLE_INPUT_FIELD[name] = 1; // 1-index
    LENGTH_FOR_MULTIPLE_INPUT_FIELD[name] = formFields.length;
    MULTIPLE_INPUT_FIELD_NAMES.push(name);

    var size = $(id).attr("size");

    registerClicks(name);
    
    var isResourceRef = false;
    if($(id).parent().attr("class").indexOf("vrtx-resource-ref-browse") != -1) {
      isResourceRef = true;	
    }

    $(id).hide();
    if($(id).next().is("button") && isResourceRef) {
      $(id).next().hide();
    }
    $(id).after("<div id='vrtx-" + name + "-add'>"
		      + "<button  onClick=\"addFormField('" + name + "',null, '"
		      + removeName + "','" + moveUpName + "','" + moveDownName + "','" + browseName + "','" + size + "'," + isResourceRef + "," + false + "); return false;\">"
		      + addName + "</button></div>");
    
    var addFormFieldFunc = addFormField;
    for (var i = 0; i < LENGTH_FOR_MULTIPLE_INPUT_FIELD[name]; i++) {
       addFormFieldFunc(name, jQuery.trim(formFields[i]), removeName, moveUpName, moveDownName, browseName, size, isResourceRef, true);
    }
}

function registerClicks(name) {
  $("." + name).delegate(".remove", "click", function(){
	removeFormField(name, $(this));
  });
  $("." + name).delegate(".moveup", "click", function(){
	moveUpFormField($(this));
  });
  $("." + name).delegate(".movedown", "click", function(){
	moveDownFormField($(this));
  });
  $("." + name).delegate(".browse-resource-ref", "click", function(){
	browseServer($(this).parent().find('input').attr('id'), browseBase, browseBaseFolder, browseBasePath, 'File');
  });
}

function addFormField(name, value, removeName, moveUpName, moveDownName, browseName, size, isResourceRef, init) {
	if (value == null) { value = ""; }

    var idstr = "vrtx-" + name + "-";
    var i = COUNTER_FOR_MULTIPLE_INPUT_FIELD[name];
    var removeButton = "";
    var moveUpButton = "";
    var moveDownButton = "";
    var browseButton = "";

    if (removeName != null) {
        removeButton = "<button class='remove' type='button' " + "id='" + idstr + "remove' >" + removeName + "</button>";
    }
    if (moveUpName != null && i > 1) {
    	moveUpButton = "<button class='moveup' type='button' " + "id='" + idstr + "moveup' >"
    	+ "&uarr; " + moveUpName + "</button>";
    }
    if (moveDownName != null && i < LENGTH_FOR_MULTIPLE_INPUT_FIELD[name]) {
    	moveDownButton = "<button class='movedown' type='button' " + "id='" + idstr + "movedown' >"
    	+ "&darr; " + moveDownName + "</button>";
    }
    if(browseButton != null) {
        browseButton = "<button type='button' class='browse-resource-ref'>" + browseName + "</button>";
    }
    
    if(!isResourceRef) { // TODO: check if field is «resource_ref»
      var html = "<div class='vrtx-multipleinputfield' id='" + idstr + "row-" + i + "'>"
               + "<input value='" + value + "' type='text' size='" + size + "' id='" + idstr + i + "' />"
               + removeButton + moveUpButton + moveDownButton + "</div>";
    } else {
      var html = "<div class='vrtx-multipleinputfield' id='" + idstr + "row-" + i + "'>"
               + "<input value='" + value + "' type='text' size='" + size + "' id='" + idstr + i + "' />"
               + browseButton + removeButton + moveUpButton + moveDownButton + "</div>";
    }
    
    $("#vrtx-" + name + "-add").before(html);
    
    if(!init) {
    	if(LENGTH_FOR_MULTIPLE_INPUT_FIELD[name] > 0) {
    	  var fields = "." + name + " div.vrtx-multipleinputfield";
          if($(fields).eq(LENGTH_FOR_MULTIPLE_INPUT_FIELD[name] - 1).not("has:button.movedown")) {
            var theId = $(fields).eq(LENGTH_FOR_MULTIPLE_INPUT_FIELD[name] - 1).attr("id");
          
        	moveDownButton = "<button class='movedown' type='button' " + "id='" + idstr + "movedown' >"
        	+ "&darr; " + moveDownName + "</button>";

        	$(fields).eq(LENGTH_FOR_MULTIPLE_INPUT_FIELD[name] - 1).append(moveDownButton);
        	//logMultipleInputFields("Added before-last movedown");
          }
    	}
        LENGTH_FOR_MULTIPLE_INPUT_FIELD[name]++;
      }

    COUNTER_FOR_MULTIPLE_INPUT_FIELD[name]++;
}

function removeFormField(name, that) {

    $(that).parent().remove();
    //logMultipleInputFields("Fjerner felt");

    LENGTH_FOR_MULTIPLE_INPUT_FIELD[name]--;
    COUNTER_FOR_MULTIPLE_INPUT_FIELD[name]--;
    //logMultipleInputFields("Number of inputfields: " + LENGTH_FOR_MULTIPLE_INPUT_FIELD[name]);
    //logMultipleInputFields("Next number for inputfield: " + COUNTER_FOR_MULTIPLE_INPUT_FIELD[name]);

    var fields = "." + name + " div.vrtx-multipleinputfield";

	if($(fields).eq(LENGTH_FOR_MULTIPLE_INPUT_FIELD[name] - 1).has("button.movedown")) {
	  $(fields).eq(LENGTH_FOR_MULTIPLE_INPUT_FIELD[name] - 1).find("button.movedown").remove();
	  //logMultipleInputFields("Removed last movedown");
	}

	if($(fields).eq(0).has("button.moveup")) {
	  $(fields).eq(0).find("button.moveup").remove();
	  //logMultipleInputFields("Removed first moveup");
	}
}

function moveUpFormField(that) {
  var thisInput = $(that).parent().find("input");
  var prevInput = $(that).parent().prev().find("input");
  var thisText = thisInput.val();
  var prevText = prevInput.val();
  $(thisInput).val(prevText);
  $(prevInput).val(thisText);
  //logMultipleInputFields("Moved up " + thisText + " and swapped with " + prevText);
}

function moveDownFormField(that) {
  var thisInput = $(that).parent().find("input");
  var nextInput = $(that).parent().next().find("input");
  var thisText = $(thisInput).val();
  var nextText = $(nextInput).val();
  $(thisInput).val(nextText);
  $(nextInput).val(thisText);
  //logMultipleInputFields("Moved down " + thisText + " and swapped with " + nextText);
}

function formatMultipleInputFields(name) {
    if ($( "#" + name ).val() == null)
        return;

    var allFields = $.find("input[id^='vrtx-" + name + "']");
    var result = "";
    var allFieldsLength = allFields.length;
    for (var i = 0; i < allFieldsLength; i++) {
        result += allFields[i].value;
        if (i < (allFieldsLength-1)) {
            result += ",";
        }
    }
    $("#" + name).val(result);
}

function saveMultipleInputFields(){
  var MULTIPLE_INPUT_FIELD_NAMES_LENGTH = MULTIPLE_INPUT_FIELD_NAMES.length;
  var formatMultipleInputFieldsFunc = formatMultipleInputFields;
  for(var i = 0; i < MULTIPLE_INPUT_FIELD_NAMES_LENGTH; i++){
    formatMultipleInputFields(MULTIPLE_INPUT_FIELD_NAMES[i]);
  }
}

function logMultipleInputFields(str) {
  if (typeof console != "undefined" && debugMultipleInputFields) {
    console.log(str);
  }
}