lexer grammar ResourcetreeLexer;

tokens {
  /* Imaginary token used for intermediate handling of AST */
  PARENT;
  FIELDS;
}

@header {
package vtk.repository.resource;
}

COMMENT
    : '/*' .* '*/' {$channel=HIDDEN;}
    ;

INCLUDE	:	'include';
RESOURCETYPE
	:	'resourcetype';
PROPERTIES
	:	'properties';
EDITRULES
	:	'edit-rules';
VIEWCOMPONENTS
	:	'view-components';
VIEW	:	'view';
VOCABULARY
	:	'vocabulary';
LOCALIZATION
	:	'localization';
SCRIPTS	:	'scripts';
SERVICES:	'services';
TRIM	:	'trim';

LCB	:	'{';
RCB	:	'}';
LP	:	'(';
RP	:	')';
LB	:	'[';
RB	:	']';
COLON	:	':';
SEMICOLON
	:	';';
COMMA	:	',';
PLUS	:	'+';
DQ	:	'"';
EQ	:	'=';
QUESTION:	'?';

PROPTYPE:	(STRING (TRIM)? | RESOURCEREF | HTML | SIMPLEHTML | BOOLEAN | INT | DATETIME | IMAGEREF | MEDIAREF);
DERIVED	:	'derived';
JSON	:	'json';
BINARY	:	'binary';
EDITHINT:	(CLASS LB NAME RB | SIZE LB (NUMBER | SMALL | LARGE | QTEXT) RB | TEXTFIELD | TEXTAREA | RADIO | DROPDOWN | ATTRIBUTE LB NAME COLON FILENAME RB);
CLASS:   'class';
ATTRIBUTE: 'attribute';
EVAL	:	'eval';
DEFAULTPROP
	:	'default';
MULTIPLE:	'multiple';
INDEXABLEJSON:	'indexablejson';
SHOWHIDE:	'show-hide';
MULTIPLEINPUTFIELDS
	:	'multipleinputfields';
SCRIPTTRIGGER 
	:	 (ONCLICK | ONFOCUS | ONSELECT);
REQUIRES:	'requires';
AFFECTS	:	'affects';
REQUIRED:	'required';
NOEDIT  :	'noedit';
TOOLTIP:	'tooltip';
NOEXTRACT
	:	'noextract';
OVERRIDES
	:	'overrides';
GROUP	:	'group';
ORIENTATION
	:	(HORIZONTAL | VERTICAL);
BEFORE	:	'before';
AFTER	:	'after';
RANGE	:	'range';
DEFAULTVALUE
	:	'defaultvalue';
LETTER	:	('a'..'z' | 'A'..'Z');
NUMBER	:	('0'..'9')+;
NAME	:	(LETTER | '-' | '_')+;
FILENAME:	('classpath:' | 'file:')?(NAME | '.' | '/')+;
ESC_SEQ	:	'\\' ('\"'|'\''|'\\') ;
QTEXT	:	'"'  ( ESC_SEQ | ~('\\'|'"') )* '"'
        { 	
            String s = getText();
            if (s.length() > 2) {
                s = s.substring(1, s.length() - 1);
                setText(s);
            }
        };
DEF	:	'##' .* '##'
        {
		  String s = getText();
		  s = s.replace("##", "");
		  setText(s.trim());
        };

WS	:	(' ' | '\t' | '\n' | '\r')+ {$channel=HIDDEN;};

// Propertytypes
fragment STRING
	:	'string';	
fragment RESOURCEREF
	:	'resource_ref';
fragment HTML
	:	'html';
fragment SIMPLEHTML
	:	'simple_html';
fragment BOOLEAN
	:	'boolean';
fragment INT
	:	'int';
fragment DATETIME
	:	'datetime';
fragment IMAGEREF
	:	'image_ref';
fragment MEDIAREF
	:	'media_ref';

// Edithints
fragment SIZE
	:	'size';
fragment TEXTFIELD
	:	'textfield';
fragment TEXTAREA
	:	'textarea';
fragment RADIO
	:	'radio';
fragment DROPDOWN
	:	'dropdown';
fragment SMALL
	:	'small';
fragment LARGE
	:	'large';
fragment HORIZONTAL 
	:	'horizontal';
fragment VERTICAL
	:	'vertical';

// Scripting
fragment ONCLICK
	:	'onclick';
fragment ONFOCUS
	:	'onfocus';
fragment ONSELECT
	:	'onselect';
