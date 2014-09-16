/*
 * Course schedule
 *
 */
 
$(document).ready(function() {
  var activitiesElm = $("#activities");

  // If user can write and is not locked
  if(canEdit === "true" || canEditLocked === "true") {
    activitiesElm.addClass("can-edit");
    // Toggle display on focus of row
    activitiesElm.on("focusin focusout", "tbody tr", function(e) {
      $(this)[e.type === "focusin" ? "addClass" : "removeClass"]("visible");
    });
    if(canEdit === "true") {
      // Open edit window for session on click (this should not be needed)
      activitiesElm.on("click", "a.edit-link", function(e) {
        var row = $(this).closest("tr");
        var idRow = row[0].id;
        var editUrl = window.location.pathname;
        if(/\/$/.test(editUrl)) {
          editUrl += "index.html";
        }
        window.location.href = editUrl + "?vrtx=admin&mode=editor&action=edit&embed&sessionid=" + encodeURIComponent(idRow) + "&t=" + (+ new Date()); // IE10 cache fix
        e.stopPropagation();
        e.preventDefault();
      });
    }
  }
  
  // Show hidden more resources
  var resourcesMoreHideVisible = function() {
    var visible = $(".res-more.visible");
    if(visible.length) {
      visible.removeClass("visible");
      visible.prev().text(scheduleI18n.showMore + "...");
    }
  };
  activitiesElm.on("click", "a.res-more-toggle", function(e) {
    var link = $(this);
    var wrapperElm = link.next();
    var isWrapperVisible = wrapperElm.hasClass("visible");
    resourcesMoreHideVisible();
    if(!isWrapperVisible) {
      wrapperElm.addClass("visible");
      link.text(scheduleI18n.hideMore + "...");
    }
    e.stopPropagation();
    e.preventDefault();
  });
  $(document).on("click", "body", resourcesMoreHideVisible);
});