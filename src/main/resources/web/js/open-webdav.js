/*
 * WebDAV<=>Office functionality for IE7+
 *
 */

if(typeof agentWebDav === "undefined") {
  $(function() {
    var ua = window.navigator.userAgent.toLowerCase();         
    var isWin = ((ua.indexOf("win") !== -1) || (ua.indexOf("16bit") !== -1));
    if ((($.browser.msie && $.browser.version >= 7) || /.*trident\/7\.0.*/.test(ua)) && isWin) {  
      
      // Show / hide WebDAV link on hover in collection listing
      $(".vrtx-resource").hover(function (e) { 
        var resourceWrp = $(this);
        resourceWrp.find(".vrtx-resource-open-webdav, .vrtx-resource-locked-webdav").css("left", (resourceWrp.find(".vrtx-title-link").width() + 63) + "px").show(0);
      }, function (e) {
        $(this).find(".vrtx-resource-open-webdav, .vrtx-resource-locked-webdav").hide(0).css("left", "0px");
      });
      
      // Show / hide WebDAV link on hover in collection table
      $(".vrtx-collection-listing-table tr").hover(function (e) { 
        $(this).find(".vrtx-resource-open-webdav, .vrtx-resource-locked-webdav").show(0);
      }, function (e) {
        $(this).find(".vrtx-resource-open-webdav, .vrtx-resource-locked-webdav").hide(0);
      });
      
      // Open WebDAV link via Sharepoint extension
      $(".vrtx-resource-open-webdav").click(function(e) {
        var openOffice = new ActiveXObject("Sharepoint.OpenDocuments.1").EditDocument(this.href);
        e.stopPropagation();
        e.preventDefault();
      });
    }
  });
}