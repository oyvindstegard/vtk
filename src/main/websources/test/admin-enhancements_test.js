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

describe("Test Admin general", function () {
  var inputElm = null;

  beforeEach(function () {
    var form = '<input id="sandbox" type="text" />';
    $("body").append(form);
    inputElm = $("#sandbox")
  });

  afterEach(function () {
    inputElm.remove();
  });

  describe("inputUpdateEngine", function () {

    it("substitution should return correct string in supervisor id field", function () {
      inputElm.val("#FOO BAR #TESTpiLoT");

      vrtxAdmin.inputUpdateEngine.update({
        input: inputElm,
        substitutions: {
          "#": "",
          " ": "-"
        },
        toLowerCase: false
      });

      expect(inputElm.val()).toBe('FOO-BAR-TESTpiLoT');
    });

    it("substitution should return correct string in create document/folder", function () {
      inputElm.val("#FØø %BÅR, #TÆSTpiLoT?");

      vrtxAdmin.inputUpdateEngine.update({
        input: inputElm,
        toLowerCase: true
      });

      expect(inputElm.val()).toBe('foo-bar-testpilot');
    });
  });

});
