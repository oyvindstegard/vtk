/*
 *  SSL communication - lightweight library
 *  by USIT/2012 - Licenced under GPL v3.0
 *
 *  TODO: origin checks
 *
 */

function SSLComLink() {
  var instance; // cached instance
  VrtxAdmin = function VrtxAdmin() { // rewrite constructor
    return instance;
  };
  VrtxAdmin.prototype = this; // carry over properties
  instance = new VrtxAdmin(); // instance
  instance.constructor = VrtxAdmin; // reset construction pointer

  this.hasPostMessage = window['postMessage'] && (!($.browser.opera && $.browser.version < 9.65));
  this.origin = "*";
  this.predefinedCommands = {};
  
  return instance;
};

/* POST BACK */
SSLComLink.prototype.postCmd = function postCmd(cmdParams, source) {
  if(this.hasPostMessage && source != "") {
    source.postMessage(cmdParams, this.origin);
  }
};
/* POST TO PARENT */
SSLComLink.prototype.postCmdToParent = function postCmdToParent(cmdParams) {
  if(this.hasPostMessage && parent) {
    parent.postMessage(cmdParams, this.origin);
  }
};
/* POST TO IFRAME */
SSLComLink.prototype.postCmdToIframe = function postCmdToParent(iframeElm, cmdParams) {
  if(this.hasPostMessage && iframeElm && iframeElm.contentWindow ) {
    iframeElm.contentWindow.postMessage(cmdParams, this.origin);
  }
};

SSLComLink.prototype.setUpReceiveDataHandler = function setUpReceiveDataHandler(cmds) {
  var self = this;
  self.predefinedCommands = cmds;
  $(window).on("message", function(e) {
    if(e.originalEvent) e = e.originalEvent;
    var receivedData = e.data;
    var source = e.source;
    if(typeof source === "undefined") source = "";
    if(typeof receivedData === "string") {
      var cmdParams = receivedData.split(":");
      self.predefinedCommands(cmdParams, source);
    }
  });
};

/* ^ SSL communication - lightweight library */