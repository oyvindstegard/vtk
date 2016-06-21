describe("Test resource editor", function () {

  describe("ajax save of form", function () {
    beforeEach(function () {
      jasmine.Ajax.install();
    });

    afterEach(function () {
      jasmine.Ajax.uninstall();
      vrtxAdmin.asyncEditorSavedDeferred = null;
    });

    it("temporary failure on server last modified request", function () {
      var mockDialog = jasmine.createSpyObj("dialog", ["close"]);
      spyOn(window, 'reTryOnTemporaryFailure').and.callThrough();

      var future = isServerLastModifiedOlderThanClientLastModified(mockDialog);
      var request = jasmine.Ajax.requests.mostRecent();
      request.respondWith({
        status: 503,
        contentType: "text/html",
        responseText: '<html><head>Service unavailable</head><body><h1>Service unavailable</h1></body></html>'
      });

      expect(mockDialog.close).not.toHaveBeenCalled();
      expect(future.state()).toEqual("pending");
      expect(window.reTryOnTemporaryFailure).toHaveBeenCalled();
    });

    it("temporary failure on ajax save of form", function () {
      window.ajaxSaveText = "Saving document";
      var lastModifiedFuture = $.Deferred();
      spyOn(window, 'isServerLastModifiedOlderThanClientLastModified').and.returnValue(lastModifiedFuture);
      spyOn(window, 'reTryOnTemporaryFailure').and.callThrough();
      var form = '<form id="editor"><input value="tekst"/></form>';
      $("body").append(form);

      ajaxSave();
      lastModifiedFuture.resolve();

      var request = jasmine.Ajax.requests.mostRecent();
      request.respondWith({
        status: 503,
        contentType: "text/html",
        responseText: '<html><head>Service unavailable</head><body><h1>Service unavailable</h1></body></html>'
      });

      $("#editor").remove();
      expect(window.reTryOnTemporaryFailure).toHaveBeenCalled();
    });

  });
});
