/*  Based on code found on the web page "http://sonspring.com/journal/jquery-iframe-sizing" which 
 *  was written by Nathan Smith (http://technorati.com/people/technorati/nathansmith/)
 *
 *  Changed to only process specific frame and pass height to parent with postMessage.
 *  Should work as before with regard to document.body (served from the view domain). 
 *  Resizing the outer iframe (served from the admin domain) only works on browsers which support postMessage.
 *  
 *  TODO: refactor with iframe-view.js (much of same code used here without another iframe)
 *  
 */
$(document).ready(function()
	{		
		var hasPostMessage = window['postMessage'] && (!($.browser.opera && $.browser.version < 9.65))
	
		var vrtxAdminOrigin = "*"; // TODO: TEMP Need real origin of admin
			
		$(window).load(function()
			{
				// Set inline style to equal the body height of the iframed content,
				// when body content is at least 350px height
				var setHeight = 350;
				var computedHeight = document.body.offsetHeight + 45
				if (computedHeight > setHeight) { 
					setHeight = computedHeight;
				}
				document.body.style.height = setHeight + "px";
				if (hasPostMessage && parent) {
					// Pass our height to parent since it is typically cross domain (and can't access it directly)
					parent.postMessage(setHeight, vrtxAdminOrigin);	
				}					
			}
		);
	}
);
