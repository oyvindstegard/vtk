/*
 *  Vortex browse window
 *  
 *  Used by the browseService to update the xml-element being edited with
 *
 */

function updateParent(editField, browseURL) {
  opener.document.getElementById(editField).value = browseURL;
  self.close();
  return false;
}

$(document).ready(function() {
  var contents = $("#contents");
  if(!contents.find("table").length) {
    contents.parent().remove();
  }
});