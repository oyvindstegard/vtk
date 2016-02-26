/*
 * Specific behavior of autocomplete for permissions
 */

function permissionsAutocomplete(id, service, params, returnUsername) {
  var p = {
    formatItem : function(data, i, n, value) {
      return value.split(';')[0] + ' (' + value.split(';')[1] + ')';
    },
    formatResult : function(data, value) {
      if(returnUsername) {
        return value.split(';')[1];
      } else {
        return value.split(';')[0];
      }
    },
    highlight : function(value, term) {
      var splitValue = value.split("(");
      var desc = splitValue[1];
      var valueArray = splitValue[0].split(" ");
      var termArray = term.split(" ");
      var returnValue = "";
      for (v in valueArray) {
        var val = valueArray[v];
        for (t in termArray) {
          var regex = new RegExp("^(?![^&;]+;)(?!<[^<>]*)("
              + termArray[t].replace(/([\^\$\(\)\[\]\{\}\*\.\+\?\|\\])/gi, "\\$1") + ")(?![^<>]*>)(?![^&;]+;)", "gi");
          val = val.replace(regex, "<strong>$1</strong>");
          if (service.indexOf("userNames") != -1) {
            desc = desc.replace(regex, "<strong>$1</strong>");
          }
        }
        returnValue = (returnValue == "") ? val : (returnValue + " " + val);
      }
      return '<div class="vrtx-autocomplete-search-info"><span class="vrtx-autocomplete-search-title">' + returnValue + '</span>'
           + '<span class="vrtx-autocomplete-search-subtittel">' + desc.replace(")", "") + '</span></div>';
    }
  };
  if (params) {
    $.extend(p, params);
  }
  setAutoComplete(id, service, p);
}

function splitAutocompleteSuggestion(id) {
  var fieldId = '#' + id;
  var hiddenValueId = 'ac_' + id;
  $(fieldId).result( function(event, data, formatted) {
    if (formatted) {
      var existingValue = document.getElementById(hiddenValueId).value;
      if (existingValue != '') {
        document.getElementById(hiddenValueId).value = existingValue + ', ' + formatted;
      } else {
        document.getElementById(hiddenValueId).value = formatted;
      }
    }
  });
}

function enrichedUsersAutocomplete(id, focusClassAfterSelection) {
  var hasSelected = false;
  var fieldId = "#" + id;
  var field = $(fieldId);
  var fieldParent = field.parent();
  
  // Have selected autocomplete result
  field.result(function(event, data, formatted) {
    hasSelected = true;
    
    // Hide inputfield
    field.hide();
    
    // Add user enrichment
    var userEnriched = formatted.split(";");
    var url = userEnriched[2];
    var name = userEnriched[0];
    if(url != "") {
      var userEnrichmentHtml = "<a target='_blank' class='vrtx-multiple-inputfield-enrichment' href='" + url + "'>" + name + "</a>";
    } else {
      var userEnrichmentHtml = "<span class='vrtx-multiple-inputfield-enrichment'>" + name + "</span>";
    }
    $($.parseHTML(userEnrichmentHtml)).insertAfter(fieldParent);
    
    // Set focus on add button if exists
    var focusElm = fieldParent.closest(".vrtx-multipleinputfields").find(focusClassAfterSelection);
    if(focusElm.length) {
      focusElm[0].focus();
    }
  });
  
  // Not have selected autocomplete result on "focusout"
  $(document).on("focusout", fieldId, function() {
    if(!hasSelected) {
      field.val("");
    }
  });
}