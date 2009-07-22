lexer grammar ResourcetreeLexer;

tokens {
  /* Imaginary token used for intermediate handling of AST */
  PARENT;
  PROPERTY;
}

@header {
package org.vortikal.repository.resource;
}

RESOURCETYPE
	:	'resourcetype';
PROPERTIES
	:	'properties';
EDITRULES
	:	'edit-rules';
VIEWCOMPONENTS
	:	'view-components';
VIEWDEFINITION
	:	'view-definition';
LOCALIZATIONPROPERTIES
	:	'localization-properties';

LCB	:	'{';
RCB	:	'}';
LP	:	'(';
RP	:	')';
LB	:	'[';
RB	:	']';
COLON	:	':';
COMMA	:	',';
DQ		:	'"';

PROPTYPE:	(STRING | HTML | SIMPLEHTML | BOOLEAN | INT | DATETIME | IMAGEREF);
EDITHINT:	(SIZE LB NUMBER RB | TEXTFIELD | TEXTAREA | RADIO | DROPDOWN );
REQUIRED:	'required';
NOEXTRACT
	:	'noextract';
OVERRIDES
	:	'overrides';
GROUP	:	'group';
ORIANTATION
	:	HORISONTAL;
BEFORE	:	'before';
AFTER	:	'after';
LETTER	:	('a'..'z' | 'A'..'Z');
NUMBER	:	('0'..'9')+;
// XXX write rule
VALUELIST
	:	;
NAME	:	(LETTER | '-' | '_')+;
VIEWDEF	:	'$$' (options {greedy=false;} : .)* '$$'
		{
		  String s = getText();
		  s = s.replaceAll("\\s+", " ");
		  s = s.replace("$$", "");
		  setText(s.trim());
		};
VIEWCOMPDEF	:	'##' .* '##'
        {
		  String s = getText();
		  setText(s.trim());
        };

WS	:	(' ' | '\t' | '\n')+ {$channel=HIDDEN;};

// Propertytypes
fragment STRING
	:	'string';
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

// Edithints
fragment SIZE
    :   'size';
fragment TEXTFIELD
	:	'textfield';
fragment TEXTAREA
	:	'textarea';
fragment RADIO
	:	'radio';
fragment DROPDOWN
	:	'dropdown';
fragment HORISONTAL 
	:	'horisontal';
