// Used by the browseService to update the xml-element being edited with
// the URL to the selected resource 

function updateParent(editField, browseURL) {
  opener.document.getElementById(editField).value = browseURL;
  self.close();
  return false;
}
