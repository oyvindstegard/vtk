describe("Test Admin Ajax features", function () {

  describe("serverFacade.post", function () {
    beforeEach(function () {
      jasmine.Ajax.install();
    });

    afterEach(function () {
      jasmine.Ajax.uninstall();
    });

    it("popup should be shown on 503 response from server", function () {
      var callbacks = jasmine.createSpyObj("callbacks", ["success", "error"]);
      spyOn(window, 'reTryOnTemporaryFailure').and.callThrough();

      vrtxAdmin.serverFacade.post("/test", "", callbacks);
      var request = jasmine.Ajax.requests.mostRecent();
      request.respondWith({
        status: 503,
        contentType: "text/html",
        responseText: '<html><head>Service unavailable</head><body><h1>Service unavailable</h1></body></html>'
      });

      expect(request.url).toBe('/test');
      expect(window.reTryOnTemporaryFailure).toHaveBeenCalled();
      expect(callbacks.error).toHaveBeenCalled();
    });

    it("popup should be shown on request socket error", function () {
      var callbacks = jasmine.createSpyObj("callbacks", ["success", "error"]);
      spyOn(window, 'reTryOnTemporaryFailure').and.callThrough();

      vrtxAdmin.serverFacade.post("/test", "", callbacks);
      var request = jasmine.Ajax.requests.mostRecent();
      request.respondWith({
        status: 0,
        statusText: "abort"
      });

      expect(request.url).toBe('/test');
      expect(window.reTryOnTemporaryFailure).toHaveBeenCalled();
      expect(callbacks.error).toHaveBeenCalled();
    });
  });

});

