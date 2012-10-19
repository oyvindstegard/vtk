function hideShowStudy(typeToDisplay) {
  var container = $("#editor");
  switch (typeToDisplay) { // TODO: possible use container.attr("class", "").addClass(""); instead
    case "so":
      container.removeClass("nm").removeClass("em").addClass("so");
      break;
    case "nm":
      container.removeClass("so").removeClass("em").addClass("nm");
      break;
    case "em":
      container.removeClass("so").removeClass("nm").addClass("em");
      break;
    default:
      container.removeClass("so").removeClass("nm").removeClass("em");
      break;
  }
}

function hideShowSemester(typeSemester, str) {
  var container = $("#editor");
  switch (typeSemester) { // TODO: possible use container.attr("class", "").addClass(""); instead
    case "bestemt-semester":
      container.removeClass(str + "-annet").addClass(str + "-bestemt");
      break;
    case "annet":
      container.removeClass(str + "-bestemt").addClass(str + "-annet");
      break;
    default:
      container.removeClass(str + "-annet").removeClass(str + "-bestemt");
      break;
  }
}

function replaceTag(selector, tag, replaceTag) {
  selector.find(tag).replaceWith(function() {
    return "<" + replaceTag + ">" + $(this).text() + "</" + replaceTag + ">";
  });
}

$(document).ready(function () {

  // 'How to search'-document
  var typeToDisplay = $("#typeToDisplay"); 
  if(typeToDisplay.length) { 
    try {
      hideShowStudy(typeToDisplay.val());
    }
    catch (err) {
      vrtxAdmin.error({msg: err});
    }
    for(var grouped = $(".vrtx-grouped"), i = grouped.length; i--;) { // Because accordion needs one content wrapper
      $(grouped[i]).find("> *:not(.header)").wrapAll("<div />");
    }
    $("#editor").accordion({ header: "> div > .header",
                             autoHeight: false,
                             collapsible: true,
                             active: false
                           });
    $(".ui-accordion > .vrtx-string:visible:last").addClass("last");
    
    $(document).on("change", "#typeToDisplay", function () {
      hideShowStudy($(this).val());
      $(".ui-accordion > .vrtx-string.last").removeClass("last");
      $(".ui-accordion > .vrtx-string:visible:last").addClass("last");
    });
  }
  
  // Course description - hide/show semesters (TODO: combine simular code)
  var typeSemesterUndervisning = $("#undervisningssemester");
  if(typeSemesterUndervisning.length) {
    try {
      hideShowSemester(typeSemesterUndervisning.val(), "undervisning");
    } catch (err) {
      vrtxAdmin.error({msg: err});
    }
    $(document).on("change", "#undervisningssemester", function () {
      hideShowSemester($(this).val(), "undervisning");
    });
  }
  var typeSemesterEksamen = $("#eksamenssemester");
  if(typeSemesterEksamen.length) {
    try {
      hideShowSemester(typeSemesterEksamen.val(), "eksamen");
    } catch (err) {
      vrtxAdmin.error({msg: err});
    }
    $(document).on("change", "#eksamenssemester", function () {
      hideShowSemester($(this).val(), "eksamen");
    });
  }
  
  // 'Samlet program'-document
  var samletElm = $(".samlet-element");
  if(samletElm.length) {
    replaceTag(samletElm, "h6", "strong");
    replaceTag(samletElm, "h5", "h6");  
    replaceTag(samletElm, "h4", "h5");
    replaceTag(samletElm, "h3", "h4");
    replaceTag(samletElm, "h2", "h3");
    replaceTag(samletElm, "h1", "h2");
  }
});
