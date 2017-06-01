/*
 * FCKeditor - The text editor for Internet - http://www.fckeditor.net
 * Copyright (C) 2003-2009 Frederico Caldeira Knabben
 *
 * == BEGIN LICENSE ==
 *
 * Licensed under the terms of any of the following licenses at your
 * choice:
 *
 *  - GNU General Public License Version 2 or later (the "GPL")
 *    http://www.gnu.org/licenses/gpl.html
 *
 *  - GNU Lesser General Public License Version 2.1 or later (the "LGPL")
 *    http://www.gnu.org/licenses/lgpl.html
 *
 *  - Mozilla Public License Version 1.1 or later (the "MPL")
 *    http://www.mozilla.org/MPL/MPL-1.1.html
 *
 * == END LICENSE ==
 *
 * Common objects and functions shared by all pages that compose the
 * File Browser dialog window.
 */

// Automatically detect the correct document.domain (#1919).
(function()
{
	var d = document.domain ;

	while ( true )
	{
		// Test if we can access a parent property.
		try
		{
			var test = window.top.opener.document.domain ;
			break ;
		}
		catch( e )
		{}

		// Remove a domain part: www.mytest.example.com => mytest.example.com => example.com ...
		d = d.replace( /.*?(?:\.|$)/, '' ) ;

		if ( d.length == 0 )
			break ;		// It was not able to detect the domain.

		try
		{
			document.domain = d ;
		}
		catch (e)
		{
			break ;
		}
	}
})() ;

function AddSelectOption( selectElement, optionText, optionValue )
{
	var oOption = document.createElement("OPTION") ;

	oOption.text	= optionText ;
	oOption.value	= optionValue ;

	selectElement.options.add(oOption) ;

	return oOption ;
}

var oConnector	= window.top.oConnector ;
var oIcons		= window.top.oIcons ;


function StringBuilder( value )
{
    this._Strings = new Array( value || '' ) ;
}

StringBuilder.prototype.Append = function( value )
{
    if ( value )
        this._Strings.push( value ) ;
}

StringBuilder.prototype.ToString = function()
{
    return this._Strings.join( '' ) ;
}

function GetUrlParam( paramName )
{
	var oRegex = new RegExp( '[\?&]' + paramName + '=([^&]+)', 'i' ) ;
	var oMatch = oRegex.exec( window.top.location.search ) ;

	if ( oMatch && oMatch.length > 1 )
		return decodeURIComponent( oMatch[1] ) ;
	else
		return '' ;
}

function OpenFile( fileUrl )
{
    var protocol;
    var host;
    var ua = navigator.userAgent.toLowerCase() ;
    try {
        protocol = window.top.opener.location.protocol;
        host = window.top.opener.location.host;
        if ( / trident\//.test(ua) ) { // IE <= 11
            return LegacyOpenFile( fileUrl );
        }
    } catch (e) {
        var openerParam = GetUrlParam( 'opener' );
        var parts = openerParam.split('/');
        if (parts.length < 3)
        {
            return;
        }
        protocol = parts[0];
        host = parts[2];
    }

    if ( ! oConnector.IsAcceptableReceiver( host ) )
    {
        return ;
    }
    var sDomain = protocol + '//' + host ;

    var oMsg =
    {
        'type'            : 'browse-select-resource',
        'url'             : fileUrl,
        'CKEditorFuncNum' : GetUrlParam( 'CKEditorFuncNum' )
    };

    window.top.opener.postMessage( oMsg, sDomain ) ;
    window.top.close() ;

    if ( !(/iphone/.test(ua) || /ipad/.test(ua) || /ipod/.test(ua) || /android/.test(ua)) )
    {
        window.top.opener.focus() ;
    }
}

function LegacyOpenFile( fileUrl )
{
    if(window.top.opener.CKEDITOR) {
        funcNum = GetUrlParam('CKEditorFuncNum');
        window.top.opener.CKEDITOR.tools.callFunction( funcNum, fileUrl);
        window.top.opener.SetUrl( fileUrl );
    } else {
        window.top.opener.SetUrl( fileUrl );
    }

    var ua = navigator.userAgent.toLowerCase();
    window.top.close() ;
    if(!(/iphone/.test(ua) || /ipad/.test(ua) || /ipod/.test(ua) || /android/.test(ua))) {
        window.top.opener.focus() ;
    }
}

function ajaxSubmit (oFormElement, successHandler, errorHandler)
{
  if (!oFormElement.action) { return; }
  var xhr = new XMLHttpRequest();

  function _handler() {
    if (xhr.readyState === 4) {
      if (xhr.status >= 200 && xhr.status < 300) {
        if (typeof(successHandler) !== "undefined") {
          successHandler(xhr);
        }
      } else {
        if (typeof(errorHandler) !== "undefined") {
          errorHandler(xhr);
        }
      }
    }
  }

  xhr.onreadystatechange = _handler;
  if (oFormElement.method.toLowerCase() === "post") {
    xhr.open("post", oFormElement.action);
    xhr.send(new FormData(oFormElement));
  } else {
    var oField, sFieldType, nFile, sSearch = "";
    for (var nItem = 0; nItem < oFormElement.elements.length; nItem++) {
      oField = oFormElement.elements[nItem];
      if (!oField.hasAttribute("name")) { continue; }
      sFieldType = oField.nodeName.toUpperCase() === "INPUT" ? oField.getAttribute("type").toUpperCase() : "TEXT";
      if (sFieldType === "FILE") {
        for (nFile = 0; nFile < oField.files.length; sSearch += "&" + escape(oField.name) + "=" + escape(oField.files[nFile++].name));
      } else if ((sFieldType !== "RADIO" && sFieldType !== "CHECKBOX") || oField.checked) {
        sSearch += "&" + escape(oField.name) + "=" + escape(oField.value);
      }
    }
    xhr.open("get", oFormElement.action.replace(/(?:\?.*)?$/, sSearch.replace(/^&/, "?")), true);
    xhr.send(null);
  }
}
