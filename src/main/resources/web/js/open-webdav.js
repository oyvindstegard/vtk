/*
 * WebDAV<=>Office functionality for IE7+
 *
 */

if(typeof agentWebDav === "undefined") {
  $(function() {
    var ua = window.navigator.userAgent.toLowerCase();         
    var isWin = ((ua.indexOf("win") != -1) || (ua.indexOf("16bit") != -1));
    if (($.browser.msie && $.browser.version >= 7) || /.*trident\/7\.0.*/.test(ua)) && isWin) {  
      $(".vrtx-resource-open-webdav").click(function(e) {
        var openOffice = new ActiveXObject("Sharepoint.OpenDocuments.1").EditDocument(this.href);
        e.stopPropagation();W
        e.preventDefault();
      });
      $(".vrtx-resource").hover(function (e) { 
        var resourceWrp = $(this);
        resourceWrp.find(".vrtx-resource-open-webdav").css("left", (resourceWrp.find(".vrtx-title-link").width() + 63) + "px").show(0);
      }, function (e) {
        $(this).find(".vrtx-resource-open-webdav").hide(0).css("left", "0px");
      });
     $(".vrtx-collection-listing-table tr").hover(function (e) { 
        $(this).find(".vrtx-resource-open-webdav").show(0);
      }, function (e) {
        $(this).find(".vrtx-resource-open-webdav").hide(0);
      });
    }
  });
}