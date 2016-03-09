/*
 *  Cross-document communication (lightweight library)
 *  by USIT/2012 - Licenced under GPL v3.0
 *
 */

function CrossDocComLink() {
  this.hasPostMessage = window['postMessage'] && (!($.browser.opera && $.browser.version < 9.65));
  this.postOrigin = "*";
  this.predefinedCommands = null;
}

/* POST back to source */
CrossDocComLink.prototype.postCmd = function postCmd(cmdParams, source) {
  if(this.hasPostMessage && source !== "") {
    source.postMessage(cmdParams, this.postOrigin);
  }
};
/* POST to parent */
CrossDocComLink.prototype.postCmdToParent = function postCmdToParent(cmdParams) {
  if(this.hasPostMessage && parent) {
    parent.postMessage(cmdParams, this.postOrigin);
  }
};
/* POST to iframe */
CrossDocComLink.prototype.postCmdToIframe = function postCmdToParent(iframeElm, cmdParams) {
  if(this.hasPostMessage && iframeElm && iframeElm.contentWindow ) {
    iframeElm.contentWindow.postMessage(cmdParams, this.postOrigin);
  }
};

CrossDocComLink.prototype.setUpReceiveDataHandler = function setUpReceiveDataHandler(cmds) {
  var self = this;
  self.predefinedCommands = (typeof cmds === "function") ? cmds : null;
  
  $(window).on("message", function(e) {
    if(e.originalEvent) e = e.originalEvent;
    
    /* TODO: Need to check that origin is same domain here:
     * Although we validate the data (string and predefined commands) and don't do eval etc. */
 
    var receivedData = e.data;
    var source = e.source;
    if(typeof source === "undefined") source = "";
    if(typeof receivedData === "string" && self.predefinedCommands) {
      var cmdParams = receivedData.split("|");
      self.predefinedCommands(cmdParams, source);
    }
  });
};

/* ^ Cross-document communication (lightweight library) */