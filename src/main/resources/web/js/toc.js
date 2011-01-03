var tocTargetId = ""; //possible to override with a target for toc defined typical in document.ready()

// This function is stolen (legally) from quirksmode.org
function getElementsByTagNames(list,obj) {
	if (!obj) var obj = document;
	var tagNames = list.split(',');
	var resultArray = new Array();
	var tagNamesLength = tagNames.length; //performance
	for (var i=0;i<tagNames.length;i++) {
		if(tocTargetId != "") { 
		  var tags = obj.getElementById(tocTargetId).getElementsByTagName(tagNames[i]);
		} else {
		  var tags = obj.getElementsByTagName(tagNames[i]);
		}
		var tagsLength = tags.length; //performance
		for (var j=0;j<tagsLength;j++) {
			resultArray.push(tags[j]);
		}
	}
	var testNode = resultArray[0];
	if (!testNode) return [];
	if (testNode.sourceIndex) {
		resultArray.sort(function (a,b) {
				return a.sourceIndex - b.sourceIndex;
		});
	}
	else if (testNode.compareDocumentPosition) {
		resultArray.sort(function (a,b) {
				return 3 - (a.compareDocumentPosition(b) & 6);
		});
	}
	return resultArray;
}


window.onload = function(){new tocGen('toc')};

//This script was originally written By Brady Mulhollem - WebTech101.com
//It was later modified by Tomm Eriksen and other humble USIT workers 
function tocGen(writeTo){
    this.num = 0;
    this.opened = 0;
    this.writeOut = '';
    this.previous = 0;
    if(document.getElementById){
        //current requirements;
        this.parentOb = document.getElementById(writeTo);

        if(document.querySelectorAll) {
        	if(tocTargetId != "") {      
              var headers = document.getElementById(tocTargetId).querySelectorAll('h2,h3');
        	} else {
        	  var headers = document.querySelectorAll('h2,h3');	
        	}
        } else if (typeof(document.compareDocumentPosition) != 'undefined' ||
                typeof(this.parentOb.sourceIndex) != 'undefined' ) {
           var headers = getElementsByTagNames('h2,h3');
        } else {
           var headers = getElementsByTagNames('h2');
        }

        if(headers.length > 0){
            var num;
            var headersLength = headers.length; //performance
            for(var i=0;i<headersLength;i++){
                num = headers[i].nodeName.substr(1);
                if(num > this.previous){
                    this.writeOut += '<ul>';
                    this.opened++;
                    this.addLink(headers[i]);
                }
                else if(num < this.previous){
                    for(var j=0;j<this.opened;j++){
                        this.writeOut += '<\/li><\/ul>';
                        this.opened--;
                    }
                    this.addLink(headers[i]);
                }
                else{
                    this.writeout += '<\/li>';
                    this.addLink(headers[i]);
                }
                this.previous = num;
            }
            // Added "-1" to this.opened 

            for(var j=0;j<=this.opened-1;j++){
                this.writeOut += '<\/li><\/ul>';
            }
            document.getElementById(writeTo).innerHTML = this.writeOut;
        }
    }
}
tocGen.prototype.addLink = function(ob){
	var id = this.getId(ob);
	var link = '<li><a href="#'+id+'">'+ob.innerHTML+'<\/a>';
	this.writeOut += link;
}
tocGen.prototype.getId = function(ob){
	if(!ob.id){
		ob.id = 'toc'+this.num;
		this.num++;
	}
	return ob.id;
}
