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

// Used by copyResourceService available from "manageCollectionListingService"

function copyMoveAction(action) {
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
   alert('Du må markere minst ett element for flytting eller kopiering');
 }
 
}
