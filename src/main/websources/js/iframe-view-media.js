/*
 *  Iframe resizing for cross domain (view for media files)
 *
 *  Based loosely on code found on the web page "http://sonspring.com/journal/jquery-iframe-sizing" which
 *  was written by Nathan Smith (http://technorati.com/people/technorati/nathansmith/)
 *
 *  - Should work as before with regard to previewIframe (served from the view domain)
 *  - Resizing the outer iframe (served from the admin domain) only works on browsers which support postMessage
 */

var crossDocComLink = new CrossDocComLink();

// Mobile preview
var originalHeight = 0;

crossDocComLink.setUpReceiveDataHandler(function(cmdParams, source) {
  switch(cmdParams[0]) {
    case "admin-min-height":
      var setHeight = (cmdParams.length === 2) ? cmdParams[1] : 450;
      var computedHeight = document.body.offsetHeight + 45;
      if(computedHeight > setHeight) {
        setHeight = computedHeight;
        crossDocComLink.postCmdToParent("preview-height|" + setHeight);
      } else { // Computed height is less than or below minimum height
        crossDocComLink.postCmdToParent("preview-keep-min-height");
      }
      originalHeight = setHeight;
      break;

    /* Mobile preview */

    case "update-height-vertical":
      var computedHeight = document.body.offsetHeight;
      crossDocComLink.postCmdToParent("preview-height-update|" + computedHeight);
      break;
    case "update-height-horizontal":
      var computedHeight = document.body.offsetHeight;
      crossDocComLink.postCmdToParent("preview-height-update|" + computedHeight);
      break;
    case "restore-height":
      crossDocComLink.postCmdToParent("preview-height-update|" + originalHeight);
      break;

    /* Print preview */

    case "print":
      window.focus();
      window.print();
      break;
    default:
  }
});

(function(){
  var waitMaxForPreviewLoaded = 10000, // 10s
      waitMaxForPreviewLoadedTimer,
      sentPreviewLoaded = false;

  $(document).ready(function () {
    if (window != top) { // Obs IE bug: http://stackoverflow.com/questions/4850978/ie-bug-window-top-false
	  waitMaxForPreviewLoadedTimer = setTimeout(function() {
        sendPreviewLoaded();
      }, waitMaxForPreviewLoaded);
      $(window).on("load", function (e) {
        sendPreviewLoaded();
      });
    }
  });

  function sendPreviewLoaded() {
    if(!sentPreviewLoaded) {
      crossDocComLink.postCmdToParent("preview-loaded");
      $("a").attr("target", "vrtx-preview-window");
      sentPreviewLoaded = true;
    }
  }
})();
