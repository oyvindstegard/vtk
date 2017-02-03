/*
 * WebDAV Office functionality for IE
 *
 */

if(typeof agentWebDav === "undefined") {
  $(function() {
    var collectionListing = $(".vrtx-resource");
    var collectionListingTable = $(".vrtx-collection-listing-table tr");
    var webdavFunctionalitySelector = ".vrtx-resource-open-webdav, .vrtx-resource-locked-webdav";

    var ua = window.navigator.userAgent.toLowerCase();
    var isIE = /.*(msie |trident\/|edge\/).*/.test(ua);

    if (isIE) {
      collectionListing.hover(function (e) {
        $(this).find(webdavFunctionalitySelector).css("left", ($(this).find(".vrtx-title-link").width() + 63) + "px").show(0);
      }, function (e) {
        $(this).find(webdavFunctionalitySelector).hide(0).css("left", "0px");
      });

      collectionListingTable.hover(function (e) {
        $(this).find(webdavFunctionalitySelector).show(0);
      }, function (e) {
        $(this).find(webdavFunctionalitySelector).hide(0);
      });

      // Open WebDAV link via Sharepoint extension
      $(".vrtx-resource-open-webdav").click(function(e) {
        var openOffice = new ActiveXObject("Sharepoint.OpenDocuments.1").EditDocument(this.href);
        e.stopPropagation();
        e.preventDefault();
      });
    } else {
      collectionListing.find(webdavFunctionalitySelector).hide(0);
      collectionListingTable.find(webdavFunctionalitySelector).hide(0);
    }
  });
}
