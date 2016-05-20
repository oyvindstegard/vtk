<#ftl strip_whitespace=true>

<#--
  * ping
  * 
  * Description: Javascript for "pinging" a resource at
  * regular intervals, keeping the session alive, and possibly
  * performing other server-side tasks. Requires the XMLHttpRequest
  * Javascript object.
  * 
  * @param url the url to access 
  * @param interval the ping interval in seconds. The default value is
  *        300 (5 minutes)
  * @param method the request method (usually "HEAD" or "GET", the
  *        default is "GET" ("HEAD" causes Firefox to occasionally hang 
  *        waiting for the request to complete).
  *
  -->
<#macro ping url interval=300 method="GET">
  <script type="text/javascript"><!--
  
     if(typeof vrtxAdmin === "undefined") var vrtxAdmin = { editorIsDead: false };
  
     var intervalSec = ${interval};
     var req;
          
     var ping = function() {
        if (req == null) {
           if (window.XMLHttpRequest) {
              req = new XMLHttpRequest();
           } else if (window.ActiveXObject) {
              req = new ActiveXObject("Microsoft.XMLHTTP");
           }
        }
        if (req != null && !vrtxAdmin.editorIsDead) {
           var d=new Date();
           req.open('${method}', '${url}' + '&timestamp=' + d.getTime(), true);
           req.onreadystatechange = callback;
           req.send(null);
        }
     }

     var callback = function() {
        if (req != null && req.readyState == 4) {
        	if (req.status == 200) {
              setTimeout(ping, intervalSec * 1000);
            }
        }
     }

     setTimeout(ping, intervalSec * 1000);
  // -->
  </script>
</#macro>
