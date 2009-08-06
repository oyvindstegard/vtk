// Used by "createDocumentService" available from "manageCollectionListingService"

function changetemplatename(n) {
  document.createDocumentForm.name.value=n;
}

// Used to hide users and groups when editing permissions

function disableInput() {
  document.getElementById('principalList').style.display = "none";
  document.getElementById('submitButtons').style.paddingTop = "5px";
}

function enableInput() {
  document.getElementById('principalList').style.display = "block";
  document.getElementById('submitButtons').style.paddingTop = "10px";
}

/*
// Used by copyResourceService available from "manageCollectionListingService"


function copyMoveAction(action) {
    copyMoveAction(action, 'DEPRECATED: Du må markere minst ett element for flytting eller kopiering');
}

function copyMoveAction(action, unCheckedMessage) {
 // document.collectionListingForm.target = "_blank";
 document.collectionListingForm.action = action;

 var checked = false;

 for (var e = 0; e < document.collectionListingForm.elements.length; e++) {
    if (document.collectionListingForm.elements[e].type == 'checkbox' && document.collectionListingForm.elements[e].checked) {
       checked = true;
    }
 }

 if (checked) {
   document.collectionListingForm.submit();
 } else {
   alert(unCheckedMessage);
 }

}
*/

function interceptEnterKey() {
	$("form").bind("keypress", function(e) {
            if ((e.which && e.which == 13) || (e.keyCode && e.keyCode == 13)) {
               return false; //cancel the default browser click
           }
      });
}

function interceptEnterKeyAndReroute(txt, btn) {
	$("form " + txt).bind("keypress", function(e) {
            if ((e.which && e.which == 13) || (e.keyCode && e.keyCode == 13)) {
              $(btn).click(); //click the associated button
              return false; //cancel the default browser click
           }
      });
}


function copyMoveButtonsAsLinks() {
    var move = $('#vrtx-move-to-selected-folder');
    if (move.size() > 0) {
       var method = move.attr('method');
       var url = move.attr('action');
       var btn = move.children('button');
       btn.hide();

       if (method == 'get') {
          btn.after('(&nbsp;<a title="' + btn.title + '" id="vrtx-move-to-selected-folder.link" href="' + url + '">' + btn.text().trim() + '</a>&nbsp;)');
          btn.addClass('thickbox');
          tb_init('#vrtx-move-to-selected-folder\\.link');
       } else {
          btn.after('(&nbsp;<a title="' + btn.title + '" id="vrtx-move-to-selected-folder.link" href="javascript:void(0);">' + btn.text().trim() + '</a>&nbsp;)');
          $('#vrtx-move-to-selected-folder\\.link').click(function() {
              btn.click();
              return false;
           });
       }
    }

    var copy = $('#vrtx-copy-to-selected-folder');
    if (copy.size() > 0) {
       var method = copy.attr('method');
       var url = copy.attr('action');
       var btn = copy.children('button');
       btn.hide();

       if (method == 'get') {
           btn.after('(&nbsp;<a title="' + btn.title + '" id="vrtx-copy-to-selected-folder.link" href="' + url + '">' + btn.text() + '</a>&nbsp;)');
           btn.addClass('thickbox');
           tb_init('#vrtx-copy-to-selected-folder\\.link');
       } else {
           btn.after('(&nbsp;<a title="' + btn.title + '" id="vrtx-copy-to-selected-folder.link" href="javascript:void(0);">' + btn.text() + '</a>&nbsp;)');
           $('#vrtx-copy-to-selected-folder\\.link').click(function() {
               btn.click();
               return false;
           });
       }
    }
}


function placeMoveButtonInActiveTab() {
    var btn = $('#collectionListing\\.action\\.move-resources');
    btn.hide();
    var li = $('li.moveResourcesService');
    li.html('<a id="moveResourceService" href="javascript:void(0);">' + btn.attr('title') + '</a>');
    $('#moveResourceService').click(function() {
        if ($('form[name=collectionListingForm] input[type=checkbox]:checked').size() == 0) {
            alert(moveUncheckedMessage);
        } else {
            $('#collectionListing\\.action\\.move-resources').click();
        }
        return false;
    });
}


function placeCopyButtonInActiveTab() {
    var btn = $('#collectionListing\\.action\\.copy-resources');
    btn.hide();
    var li = $('li.copyResourcesService');
    li.html('<a id="copyResourceService" href="javascript:void(0);">' + btn.attr('title') + '</a>');
    $('#copyResourceService').click(function() {
        if ($('form[name=collectionListingForm] input[type=checkbox]:checked').size() == 0) {
            alert(copyUncheckedMessage);
        } else {
            $('#collectionListing\\.action\\.copy-resources').click();
        }
        return false;
    });
}

// Add callbacks for the above methods:

$(document).ready(copyMoveButtonsAsLinks);
$(document).ready(placeMoveButtonInActiveTab);
$(document).ready(placeCopyButtonInActiveTab);
