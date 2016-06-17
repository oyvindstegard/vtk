describe("Test Admin Ajax features", function () {

  beforeEach(function () {
    jasmine.Ajax.install();
  });

  afterEach(function () {
    jasmine.Ajax.uninstall();
  });

  it("popup should be shown on 503 response from server", function () {
    var callbacks = jasmine.createSpyObj("callbacks", ["success", "error"]);
    vrtxAdmin.serverFacade.errorMessages = {
        down: "The service seems to be down or inactive."
    };
    spyOn(vrtxAdmin, 'displayErrorMsg');

    vrtxAdmin.serverFacade.post("/test", "", callbacks);
    var request = jasmine.Ajax.requests.mostRecent();
    request.respondWith({
      status: 503,
      contentType: "text/html",
      responseText: '<html><head>Service unavailable</head><body><h1>Service unavailable</h1></body></html>'
    });

    expect(request.url).toBe('/test');
    expect(callbacks.error).toHaveBeenCalled();
    expect(vrtxAdmin.displayErrorMsg).toHaveBeenCalledWith(
      "503 - " + vrtxAdmin.serverFacade.errorMessages.down
    );
  });

  it("popup should be shown on request socket error", function () {
    var callbacks = jasmine.createSpyObj("callbacks", ["success", "error"]);
    vrtxAdmin.serverFacade.errorMessages = {
        abort: "The request have been aborted."
    };
    spyOn(vrtxAdmin, 'displayErrorMsg');

    vrtxAdmin.serverFacade.post("/test", "", callbacks);
    var request = jasmine.Ajax.requests.mostRecent();
    request.respondWith({
      status: 0,
      statusText: "abort"
    });

    expect(request.url).toBe('/test');
    expect(callbacks.error).toHaveBeenCalled();
    expect(vrtxAdmin.displayErrorMsg).toHaveBeenCalledWith(
      vrtxAdmin.serverFacade.errorMessages.abort
    );
  });
});

